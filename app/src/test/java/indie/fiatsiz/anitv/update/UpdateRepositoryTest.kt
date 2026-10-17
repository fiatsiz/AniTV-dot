package indie.fiatsiz.anitv.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal fun releaseJson(
    version: String = "1.2.0",
    bytes: ByteArray = byteArrayOf(1, 2, 3)
): JSONObject =
    JSONObject().put("tag_name", "v$version").put("draft", false).put("prerelease", false)
        .put("body", "Release notes\nPlain text")
        .put(
            "assets", JSONArray().put(
                JSONObject().put("name", "AniTV-$version.apk")
                    .put("state", "uploaded").put("size", bytes.size)
                    .put(
                        "digest",
                        "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes)
                            .joinToString("") { "%02x".format(it) })
                    .put(
                        "browser_download_url",
                        "https://github.com/fiatsiz/AniTV-dot/releases/download/v$version/AniTV-$version.apk"
                    )
            )
        )

internal fun release(version: String = "1.2.0", bytes: ByteArray = byteArrayOf(1, 2, 3)) =
    Release.parse(releaseJson(version, bytes).toString().toByteArray())!!

class UpdateRepositoryTest {
    private class Fixture {
        val directory = Files.createTempDirectory("updater-test").toFile()
        val executor = Executors.newSingleThreadExecutor()
        var now = 1_000_000L
        var identity = ApkIdentity("indie.fiatsiz.anitv", "1.1.0", 2, 34, setOf("owner"))
        var response = ReleaseResponse(200, releaseJson().toString().toByteArray(), "etag")
        var failure = false
        val calls = AtomicInteger()
        var etag: String? = null
        var gate: CountDownLatch? = null
        var downloadGate: CountDownLatch? = null
        val downloadStarted = CountDownLatch(1)
        var inspectGate: CountDownLatch? = null
        val inspectStarted = CountDownLatch(1)
        val transport = object : UpdateTransport {
            override fun latest(etag: String?): ReleaseResponse {
                calls.incrementAndGet()
                this@Fixture.etag = etag
                gate?.await(5, TimeUnit.SECONDS)
                if (failure) throw java.io.IOException("offline")
                return response
            }

            override fun download(
                release: Release,
                target: File,
                cancellation: DownloadCancellation,
                progress: (Long) -> Unit
            ) {
                target.writeBytes(byteArrayOf(1))
                progress(1)
                downloadStarted.countDown()
                downloadGate?.await(5, TimeUnit.SECONDS)
                cancellation.check()
                target.writeBytes(byteArrayOf(1, 2, 3))
                progress(3)
            }
        }

        fun repository(wait: Boolean = true) =
            UpdateRepository(
                UpdateStore(File(directory, "metadata")), File(directory, "apk"),
                { identity }, { _, _, _ ->
                    if (inspectGate != null) inspectStarted.countDown()
                    inspectGate?.await(5, TimeUnit.SECONDS)
                }, transport, { now }, executor
            ).also { if (wait) idle() }

        fun idle() {
            executor.submit {}.get(6, TimeUnit.SECONDS)
        }

        fun close() {
            executor.shutdownNow(); directory.deleteRecursively()
        }
    }

    @Test
    fun timingPersistenceFailuresRollbackAndManualDebounce() {
        val f = Fixture()
        try {
            var repository = f.repository()
            assertTrue(repository.check(false)); f.idle()
            assertEquals(CheckStatus.AVAILABLE, repository.states.value.check)
            assertFalse(repository.check(false))
            repository = f.repository()
            assertFalse(repository.check(false))
            assertEquals("etag", UpdateStore(File(f.directory, "metadata")).read().etag)
            assertTrue(repository.check(true)); f.idle()
            assertFalse(repository.check(true))
            assertEquals(CheckStatus.DEBOUNCED, repository.states.value.check)
            f.now += 60_000
            f.failure = true
            assertTrue(repository.check(true)); f.idle()
            assertEquals(CheckStatus.ERROR, repository.states.value.check)
            assertNotNull(repository.states.value.release)
            val restarted = f.repository()
            assertEquals(CheckStatus.ERROR, restarted.states.value.check)
            assertFalse(restarted.check(false))
            f.now -= 1
            assertTrue(restarted.check(false)); f.idle()
            f.now += 24 * 60 * 60 * 1000L
            assertTrue(restarted.check(false)); f.idle()
        } finally {
            f.close()
        }
    }

    @Test
    fun firstResumeChecksAfterStartupCleanupAndDeduplicates() {
        val f = Fixture()
        try {
            f.gate = CountDownLatch(1)
            val repository = f.repository()
            repository.resume(false)
            repeat(20) { assertFalse(repository.check(true)); repository.resume(false) }
            f.gate!!.countDown(); f.idle()
            assertEquals(1, f.calls.get())
            assertEquals(CheckStatus.AVAILABLE, repository.states.value.check)
        } finally {
            f.close()
        }
    }

    @Test
    fun resumeWhileStartupStorageLoadsStillMakesFirstCheck() {
        val f = Fixture()
        val gate = CountDownLatch(1)
        try {
            f.executor.execute { gate.await(5, TimeUnit.SECONDS) }
            val repository = f.repository(wait = false)
            repository.resume(false)
            gate.countDown()
            f.idle(); f.idle()
            assertEquals(1, f.calls.get())
            assertEquals(CheckStatus.AVAILABLE, repository.states.value.check)
        } finally {
            gate.countDown(); f.close()
        }
    }

    @Test
    fun backgroundDuringInstallRevalidationRetainsVerifiedDownload() {
        val f = Fixture()
        try {
            val repository = f.repository()
            repository.resume(false); f.idle()
            repository.download(); f.idle()
            f.inspectGate = CountDownLatch(1)
            repository.forInstall { fail("Must not open installer in background") }
            assertTrue(f.inspectStarted.await(2, TimeUnit.SECONDS))
            repository.background()
            f.inspectGate!!.countDown(); f.idle()
            assertEquals(DownloadStatus.READY, repository.states.value.download)
            assertTrue(File(f.directory, "apk/AniTV-1.2.0.apk").isFile)
        } finally {
            f.inspectGate?.countDown(); f.close()
        }
    }

    @Test
    fun conditional304OfflineMalformedAndRateLimitsRetainInformation() {
        val f = Fixture()
        try {
            val repository = f.repository()
            repository.check(false); f.idle()
            val previous = repository.states.value.release
            f.now += 60_000
            f.response = ReleaseResponse(304)
            repository.check(true); f.idle()
            assertEquals("etag", f.etag)
            assertEquals(previous, repository.states.value.release)
            f.now += 60_000
            f.response = ReleaseResponse(200, "{}".toByteArray())
            repository.check(true); f.idle()
            assertEquals(CheckStatus.ERROR, repository.states.value.check)
            assertEquals(previous, repository.states.value.release)
            f.now += 60_000
            f.response = ReleaseResponse(
                429,
                retryAfter = "120",
                rateReset = ((f.now + 180_000) / 1000).toString()
            )
            repository.check(true); f.idle()
            val retry = repository.states.value.retryAt
            assertEquals(f.now + 180_000, retry)
            f.now += 60_000
            assertFalse(repository.check(true))
            assertFalse(f.repository().check(true))
            f.now = retry
            f.response = ReleaseResponse(304)
            assertTrue(repository.check(true)); f.idle()
            assertEquals(CheckStatus.AVAILABLE, repository.states.value.check)
        } finally {
            f.close()
        }
    }

    @Test
    fun currentOlderAndIgnoredPrereleasesAreNeverOffered() {
        for (version in listOf("1.1.0", "1.0.99")) {
            val f = Fixture()
            try {
                f.response = ReleaseResponse(200, releaseJson(version).toString().toByteArray())
                val repository = f.repository()
                repository.check(false); f.idle()
                assertEquals(CheckStatus.CURRENT, repository.states.value.check)
                assertFalse(repository.states.value.available)
                assertFalse(repository.download())
            } finally {
                f.close()
            }
        }
        val f = Fixture()
        try {
            f.response =
                ReleaseResponse(200, releaseJson().put("prerelease", true).toString().toByteArray())
            val repository = f.repository()
            repository.check(false); f.idle()
            assertEquals(CheckStatus.NO_STABLE, repository.states.value.check)
            f.now += 60_000; f.response = ReleaseResponse(304)
            repository.check(true); f.idle()
            assertEquals(CheckStatus.NO_STABLE, repository.states.value.check)
        } finally {
            f.close()
        }
    }

    @Test
    fun downloadProgressCancelBackgroundRetryReuseAndInstallationReturn() {
        val f = Fixture()
        try {
            val repository = f.repository()
            repository.resume(false); f.idle()
            f.downloadGate = CountDownLatch(1)
            assertTrue(repository.download())
            assertTrue(f.downloadStarted.await(2, TimeUnit.SECONDS))
            assertEquals(1, repository.states.value.bytes)
            assertFalse(repository.download())
            repository.background()
            f.downloadGate!!.countDown(); f.idle()
            assertEquals(DownloadStatus.CANCELLED, repository.states.value.download)
            assertTrue(File(f.directory, "apk").listFiles()!!.isEmpty())
            repository.resume(false)
            assertTrue(repository.download()); f.idle()
            assertEquals(DownloadStatus.READY, repository.states.value.download)
            f.response = ReleaseResponse(
                200,
                releaseJson().put("body", "Edited notes").toString().toByteArray()
            )
            repository.check(true); f.idle()
            assertEquals(DownloadStatus.READY, repository.states.value.download)
            var ready: File? = null
            repository.forInstall { ready = it }; f.idle()
            assertNotNull(ready)
            repository.installStatus(InstallStatus.PERMISSION)
            repository.resume(false)
            assertEquals(InstallStatus.PERMISSION_DENIED, repository.states.value.install)
            repository.installStatus(InstallStatus.PERMISSION)
            repository.resume(true)
            assertEquals(InstallStatus.OFFER_INSTALL, repository.states.value.install)
            repository.installStatus(InstallStatus.INSTALLER)
            repository.resume(true)
            assertEquals(InstallStatus.NOT_INSTALLED, repository.states.value.install)
            assertEquals(DownloadStatus.READY, f.repository().states.value.download)
            ready!!.writeBytes(byteArrayOf(8, 8, 8))
            repository.forInstall { fail("Tampered file must not install") }; f.idle()
            assertEquals(UpdateProblem.DIGEST, repository.states.value.downloadProblem)
            assertFalse(ready!!.exists())
        } finally {
            f.close()
        }
    }

    @Test
    fun obsoleteDownloadsAndStartupPartialsAreRemoved() {
        val f = Fixture()
        try {
            val repository = f.repository()
            repository.resume(false); f.idle()
            repository.download(); f.idle()
            File(f.directory, "apk/interrupted.part").writeText("partial")
            assertEquals(DownloadStatus.READY, f.repository().states.value.download)
            assertFalse(File(f.directory, "apk/interrupted.part").exists())
            f.identity = f.identity.copy(versionName = "1.2.0", versionCode = 3)
            repository.resume(true); f.idle()
            assertFalse(repository.states.value.available)
            assertEquals(DownloadStatus.IDLE, repository.states.value.download)
            assertTrue(File(f.directory, "apk").listFiles()!!.isEmpty())
        } finally {
            f.close()
        }
    }
}
