package indie.fiatsiz.anitv.update

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateInstrumentedTest {
    @Test
    fun androidInspectsRealSameSignerCandidateAndRejectsMismatches() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = File(context.filesDir, "updater-fixture")
        val candidate = File(fixtures, "candidate.apk")
        assertTrue(
            "Prepare the debug-only v1.3.0/versionCode 4 fixture first; see docs/RELEASE.md",
            candidate.isFile
        )
        val release = Release.parse(File(fixtures, "release.json").readBytes())!!
        val inspector = AndroidApkInspector(context)
        val installed = inspector.installed()
        verifyBytes(candidate, release)
        inspector.inspect(candidate, release, installed)
        assertEquals(UpdateProblem.CERTIFICATE, assertThrows(UpdateException::class.java) {
            inspector.inspect(
                candidate,
                release,
                installed.copy(certificates = setOf("wrong signer"))
            )
        }.problem)
        assertEquals(UpdateProblem.PACKAGE, assertThrows(UpdateException::class.java) {
            inspector.inspect(candidate, release, installed.copy(packageName = "other.app"))
        }.problem)
        assertEquals(UpdateProblem.VERSION, assertThrows(UpdateException::class.java) {
            inspector.inspect(
                candidate,
                release.copy(version = StableVersion.parse("1.3.1")!!),
                installed
            )
        }.problem)
        val invalid = File(fixtures, "invalid.apk").apply { writeText("invalid APK") }
        try {
            assertEquals(UpdateProblem.APK_INVALID, assertThrows(UpdateException::class.java) {
                inspector.inspect(invalid, release, installed)
            }.problem)
        } finally {
            invalid.delete()
        }
    }

    @Test
    fun providerSharesOnlyTheUpdaterCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val update = File(context.cacheDir, "update/test.apk")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", update)
        assertEquals("content", uri.scheme)
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.updates",
                File(context.filesDir, "library.xml")
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.updates",
                File(context.cacheDir, "poster.webp")
            )
        }
    }
}
