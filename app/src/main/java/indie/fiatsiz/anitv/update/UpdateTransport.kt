package indie.fiatsiz.anitv.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException

internal data class ReleaseResponse(
    val status: Int,
    val bytes: ByteArray = byteArrayOf(),
    val etag: String? = null,
    val retryAfter: String? = null,
    val rateReset: String? = null,
    val remaining: String? = null
)

internal interface UpdateTransport {
    fun latest(etag: String?): ReleaseResponse
    fun download(release: Release, target: File, cancellation: DownloadCancellation, progress: (Long) -> Unit)
}

internal class DownloadCancellation {
    @Volatile private var cancelled = false
    private var connection: HttpURLConnection? = null
    @Synchronized fun attach(value: HttpURLConnection?) {
        check()
        connection = value
    }
    fun cancel() { cancelled = true }
    @Synchronized fun disconnect() { connection?.disconnect() }
    fun check() { if (cancelled) throw CancellationException() }
}

internal class GitHubUpdateTransport(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : UpdateTransport {
    private fun connection(url: String) = connect(URL(url)).apply {
        instanceFollowRedirects = false
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", "AniTV-release-updater")
        setRequestProperty("Accept-Encoding", "identity")
    }

    override fun latest(etag: String?): ReleaseResponse {
        val connection = connection(RELEASE_API)
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            etag?.let { connection.setRequestProperty("If-None-Match", it) }
            val status = connection.responseCode
            val body = if (status == 200) {
                requireUpdate(connection.contentLengthLong <= MAX_METADATA_BYTES, UpdateProblem.METADATA)
                connection.inputStream.use { readMetadata(it) }
            } else byteArrayOf()
            return ReleaseResponse(status, body, connection.getHeaderField("ETag"),
                connection.getHeaderField("Retry-After"), connection.getHeaderField("X-RateLimit-Reset"),
                connection.getHeaderField("X-RateLimit-Remaining"))
        } finally { connection.disconnect() }
    }

    override fun download(release: Release, target: File, cancellation: DownloadCancellation, progress: (Long) -> Unit) {
        validateInitialUrl(release.url, release.version)
        var url = release.url
        var redirects = 0
        while (true) {
            cancellation.check()
            validateRedirect(url)
            val connection = connection(url)
            try {
                cancellation.attach(connection)
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    requireUpdate(redirects++ < 5, UpdateProblem.UNSAFE_URL)
                    val location = connection.getHeaderField("Location")
                        ?: throw UpdateException(UpdateProblem.UNSAFE_URL)
                    url = URI(url).resolve(location).toString()
                    validateRedirect(url)
                    continue
                }
                requireUpdate(status == 200, UpdateProblem.DOWNLOAD)
                val length = connection.contentLengthLong
                requireUpdate(length == -1L || length == release.size, UpdateProblem.SIZE)
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            cancellation.check()
                            val count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            requireUpdate(total <= release.size && total <= MAX_APK_BYTES, UpdateProblem.SIZE)
                            output.write(buffer, 0, count)
                            progress(total)
                        }
                        requireUpdate(total == release.size, UpdateProblem.SIZE)
                    }
                }
                return
            } finally {
                connection.disconnect()
                // Detach without checking: cancellation must not mask cleanup.
                runCatching { cancellation.attach(null) }
            }
        }
    }
}

internal fun readMetadata(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count == -1) return output.toByteArray()
        requireUpdate(output.size() + count <= MAX_METADATA_BYTES, UpdateProblem.METADATA)
        output.write(buffer, 0, count)
    }
}

internal fun verifyBytes(file: File, release: Release, cancellation: DownloadCancellation = DownloadCancellation()) {
    requireUpdate(file.isFile && file.length() == release.size && file.length() <= MAX_APK_BYTES, UpdateProblem.SIZE)
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            cancellation.check()
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            requireUpdate(total <= release.size, UpdateProblem.SIZE)
            digest.update(buffer, 0, count)
        }
        requireUpdate(total == release.size, UpdateProblem.SIZE)
    }
    requireUpdate(digest.digest().joinToString("") { "%02x".format(it) } == release.sha256, UpdateProblem.DIGEST)
}

internal fun retryDeadline(response: ReleaseResponse, now: Long): Long {
    fun seconds(value: String?): Long? = value?.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000)
    val delay = seconds(response.retryAfter)?.let { if (it <= Long.MAX_VALUE - now) now + it else Long.MAX_VALUE }
        ?: runCatching { ZonedDateTime.parse(response.retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
    val reset = seconds(response.rateReset)
    return maxOf(now + 60_000, delay ?: 0, reset ?: 0)
}
