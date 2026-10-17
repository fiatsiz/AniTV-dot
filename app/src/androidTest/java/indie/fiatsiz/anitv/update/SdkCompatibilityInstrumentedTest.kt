package indie.fiatsiz.anitv.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import indie.fiatsiz.anitv.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class SdkCompatibilityInstrumentedTest {
    @Test
    fun installedApkRetainsSdkVersionAndSignerValidation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inspector = AndroidApkInspector(context)
        val installed = inspector.installed()
        assertEquals(23, installed.minSdk)
        assertEquals(BuildConfig.VERSION_CODE.toLong(), installed.versionCode)
        assertTrue(installed.certificates.isNotEmpty())
        val release = Release(
            StableVersion.parse(BuildConfig.VERSION_NAME)!!, "", "", 1, ""
        )
        val earlier = installed.copy(versionName = "1.0.0", versionCode = 1)
        val apk = File(context.applicationInfo.sourceDir)
        inspector.inspect(apk, release, earlier)
        assertEquals(UpdateProblem.CERTIFICATE, assertThrows(UpdateException::class.java) {
            inspector.inspect(apk, release, earlier.copy(certificates = setOf("wrong signer")))
        }.problem)
    }

    @Test
    fun desugaredFilesAndHttpDatesPreserveUpdaterState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "sdk-compat").toFile()
        try {
            val store = UpdateStore(directory)
            store.save(UpdateRecord(etag = "old", lastAttempt = 1))
            val next = UpdateRecord(etag = "new", lastAttempt = 2, retryAt = 10)
            store.save(next)
            assertEquals(next, store.read())
            assertEquals(1445412480000L, retryDeadline(
                ReleaseResponse(429, retryAfter = "Wed, 21 Oct 2015 07:28:00 GMT"), 0
            ))
        } finally {
            directory.deleteRecursively()
        }
    }
}
