package indie.fiatsiz.anitv.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal enum class CheckStatus { NEVER, CHECKING, CURRENT, AVAILABLE, NO_STABLE, ERROR, RATE_LIMIT, DEBOUNCED }
internal enum class DownloadStatus { IDLE, DOWNLOADING, VERIFYING, READY, CANCELLED, ERROR }
internal enum class InstallStatus { IDLE, PERMISSION, PERMISSION_DENIED, OFFER_INSTALL, INSTALLER, NOT_INSTALLED, UNAVAILABLE }
internal data class UpdateState(
    val installedVersion: String,
    val release: Release? = null,
    val check: CheckStatus = CheckStatus.NEVER,
    val problem: UpdateProblem? = null,
    val lastSuccess: Long? = null,
    val retryAt: Long = 0,
    val download: DownloadStatus = DownloadStatus.IDLE,
    val bytes: Long = 0,
    val downloadProblem: UpdateProblem? = null,
    val install: InstallStatus = InstallStatus.IDLE
) {
    val available: Boolean get() = release?.version?.let { latest ->
        StableVersion.parse(installedVersion)?.let { latest > it }
    } == true
}

internal class UpdateRepository(
    private val store: UpdateStore,
    private val directory: File,
    private val installed: () -> ApkIdentity,
    private val inspect: (File, Release, ApkIdentity) -> Unit,
    private val transport: UpdateTransport = GitHubUpdateTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "anitv-updater").apply { isDaemon = true }
    }
) {
    private var record = UpdateRecord()
    private var identity = installed()
    private val mutable = MutableStateFlow(UpdateState(identity.versionName))
    val states = mutable.asStateFlow()
    private var checking = false
    private var active: DownloadCancellation? = null
    private var foreground = false

    init {
        // Startup cleanup/revalidation shares the download lock so it cannot race a new transfer.
        synchronized(this) {
            val cancellation = DownloadCancellation()
            active = cancellation
            executor.execute {
                try {
                    val cached = store.read()
                    val release = cached.metadata?.let { Release.parse(it.toByteArray()) }
                    synchronized(this) {
                        record = cached
                        mutable.value = mutable.value.copy(release = release, lastSuccess = record.lastSuccess,
                            retryAt = record.retryAt, problem = record.problem, check = when {
                                record.problem == UpdateProblem.RATE_LIMIT -> CheckStatus.RATE_LIMIT
                                record.problem != null -> CheckStatus.ERROR
                                record.lastSuccess == null -> CheckStatus.NEVER
                                else -> successStatus(release)
                            })
                    }
                    directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
                    if (release != null && states.value.available && apk(release).isFile) {
                        verify(release, apk(release), cancellation)
                        synchronized(this) { mutable.value = mutable.value.copy(download = DownloadStatus.READY) }
                    } else clearApks()
                } catch (e: Exception) {
                    if (e !is CancellationException) clearApks()
                } finally { synchronized(this) {
                    if (active === cancellation) active = null
                    if (foreground) check(false)
                } }
            }
        }
    }

    private fun apk(release: Release) = File(directory, release.fileName)
    private fun clearApks() { directory.listFiles()?.filter { it.extension == "apk" }?.forEach { it.delete() } }
    private fun successStatus(release: Release?) = when {
        release == null -> CheckStatus.NO_STABLE
        release.version > (StableVersion.parse(identity.versionName) ?: throw UpdateException(UpdateProblem.VERSION)) -> CheckStatus.AVAILABLE
        else -> CheckStatus.CURRENT
    }
    private fun persist() {
        try { store.save(record) } catch (_: Exception) {
            mutable.value = mutable.value.copy(check = CheckStatus.ERROR, problem = UpdateProblem.STORAGE)
        }
    }

    @Synchronized fun resume(canInstall: Boolean) {
        foreground = true
        val current = installed()
        if (current != identity) {
            identity = current
            cancelDownload()
            mutable.value = mutable.value.copy(installedVersion = current.versionName,
                download = DownloadStatus.IDLE, install = InstallStatus.IDLE)
            if (!mutable.value.available) {
                mutable.value = mutable.value.copy(release = null, check = CheckStatus.NEVER)
                record = record.copy(metadata = null, etag = null, lastAttempt = null, lastSuccess = null, problem = null)
                executor.execute { synchronized(this) { clearApks(); persist() } }
            }
        }
        mutable.value = mutable.value.copy(install = when (mutable.value.install) {
            InstallStatus.PERMISSION -> if (canInstall) InstallStatus.OFFER_INSTALL else InstallStatus.PERMISSION_DENIED
            InstallStatus.INSTALLER -> InstallStatus.NOT_INSTALLED
            else -> mutable.value.install
        })
        check(false)
    }

    @Synchronized fun background() {
        foreground = false
        cancelDownload()
    }

    @Synchronized fun check(manual: Boolean): Boolean {
        if (checking || active != null) return false
        val now = clock()
        if (now < record.retryAt) {
            mutable.value = mutable.value.copy(check = CheckStatus.RATE_LIMIT, problem = UpdateProblem.RATE_LIMIT)
            return false
        }
        if (manual) {
            if (!elapsed(record.lastManual, now, 60_000)) {
                mutable.value = mutable.value.copy(check = CheckStatus.DEBOUNCED)
                return false
            }
        } else if (!elapsed(record.lastAttempt, now, 24 * 60 * 60 * 1000L)) return false
        checking = true
        record = record.copy(lastAttempt = now, lastManual = if (manual) now else record.lastManual)
        mutable.value = mutable.value.copy(check = CheckStatus.CHECKING, problem = null)
        executor.execute {
            synchronized(this) { persist() }
            try {
                val response = transport.latest(record.etag)
                synchronized(this) {
                    val result = when (response.status) {
                        200 -> Release.parse(response.bytes)
                        304 -> {
                            val cached = record.metadata ?: throw UpdateException(UpdateProblem.METADATA)
                            Release.parse(cached.toByteArray())
                        }
                        403, 429 -> {
                            record = record.copy(retryAt = retryDeadline(response, clock()))
                            throw UpdateException(UpdateProblem.RATE_LIMIT)
                        }
                        else -> throw UpdateException(UpdateProblem.HTTP)
                    }
                    // Release-note edits do not replace a verified APK.
                    if (result?.copy(notes = "") != mutable.value.release?.copy(notes = "")) {
                        clearApks()
                        mutable.value = mutable.value.copy(download = DownloadStatus.IDLE, bytes = 0,
                            downloadProblem = null, install = InstallStatus.IDLE)
                    }
                    record = record.copy(metadata = if (response.status == 200) response.bytes.toString(Charsets.UTF_8) else record.metadata,
                        etag = response.etag ?: if (response.status == 304) record.etag else null,
                        lastSuccess = clock(), retryAt = 0, problem = null)
                    mutable.value = mutable.value.copy(release = result, check = successStatus(result), problem = null,
                        lastSuccess = record.lastSuccess, retryAt = 0)
                }
            } catch (e: Exception) {
                synchronized(this) {
                    val problem = (e as? UpdateException)?.problem ?: UpdateProblem.NETWORK
                    record = record.copy(problem = problem)
                    mutable.value = mutable.value.copy(check = if (problem == UpdateProblem.RATE_LIMIT) CheckStatus.RATE_LIMIT else CheckStatus.ERROR,
                        problem = problem, retryAt = record.retryAt)
                }
            } finally { synchronized(this) { persist(); checking = false } }
        }
        return true
    }

    @Synchronized fun download(): Boolean {
        val release = mutable.value.release ?: return false
        if (!foreground || !mutable.value.available || active != null || checking) return false
        val cancellation = DownloadCancellation()
        active = cancellation
        mutable.value = mutable.value.copy(download = DownloadStatus.DOWNLOADING, bytes = 0,
            downloadProblem = null, install = InstallStatus.IDLE)
        executor.execute {
            val partial = File(directory, "${release.fileName}.part")
            try {
                check(directory.mkdirs() || directory.isDirectory)
                if (apk(release).isFile) {
                    verify(release, apk(release), cancellation)
                } else {
                    transport.download(release, partial, cancellation) { bytes ->
                        synchronized(this) {
                            if (bytes == release.size || bytes * 100 / release.size != mutable.value.bytes * 100 / release.size)
                                mutable.value = mutable.value.copy(bytes = bytes)
                        }
                    }
                    synchronized(this) { mutable.value = mutable.value.copy(download = DownloadStatus.VERIFYING) }
                    verify(release, partial, cancellation)
                    cancellation.check()
                    check(partial.renameTo(apk(release)))
                }
                synchronized(this) {
                    cancellation.check()
                    mutable.value = mutable.value.copy(download = DownloadStatus.READY, bytes = release.size)
                }
            } catch (e: Exception) {
                synchronized(this) {
                    val cancelled = runCatching { cancellation.check() }.exceptionOrNull() is CancellationException
                    if (!cancelled) apk(release).delete()
                    mutable.value = mutable.value.copy(download = if (cancelled) DownloadStatus.CANCELLED else DownloadStatus.ERROR,
                        downloadProblem = if (cancelled) null else (e as? UpdateException)?.problem ?: UpdateProblem.DOWNLOAD)
                }
            } finally {
                partial.delete()
                synchronized(this) { if (active === cancellation) active = null }
            }
        }
        return true
    }

    private fun verify(release: Release, file: File, cancellation: DownloadCancellation) {
        verifyBytes(file, release, cancellation)
        inspect(file, release, installed())
        cancellation.check()
    }

    @Synchronized fun cancelDownload() {
        val cancellation = active ?: return
        cancellation.cancel()
        // Closing a network connection must not block TV input or Activity Stop.
        executor.execute { cancellation.disconnect() }
    }
    @Synchronized fun installStatus(status: InstallStatus) { mutable.value = mutable.value.copy(install = status) }

    @Synchronized fun forInstall(ready: (File) -> Unit) {
        val release = mutable.value.release ?: return
        if (!foreground || active != null || checking || mutable.value.download != DownloadStatus.READY) return
        val cancellation = DownloadCancellation()
        active = cancellation
        mutable.value = mutable.value.copy(download = DownloadStatus.VERIFYING)
        executor.execute {
            try {
                verify(release, apk(release), cancellation)
                synchronized(this) {
                    cancellation.check()
                    mutable.value = mutable.value.copy(download = DownloadStatus.READY)
                    if (foreground) ready(apk(release))
                }
            } catch (e: Exception) {
                synchronized(this) {
                    if (e is CancellationException) {
                        mutable.value = mutable.value.copy(download = DownloadStatus.READY)
                    } else {
                        mutable.value = mutable.value.copy(download = DownloadStatus.ERROR,
                            downloadProblem = (e as? UpdateException)?.problem ?: UpdateProblem.APK_INVALID)
                        apk(release).delete()
                    }
                }
            } finally { synchronized(this) { if (active === cancellation) active = null } }
        }
    }

    companion object {
        internal fun elapsed(previous: Long?, now: Long, interval: Long) = previous == null || now < previous || now - previous >= interval
    }
}
