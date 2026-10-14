package indie.fiatsiz.anitv.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ReleaseTest {
    @Test fun comparesNumericStableVersions() {
        assertTrue(StableVersion.parse("1.10.0")!! > StableVersion.parse("1.9.99")!!)
        assertTrue(StableVersion.parse("99999999999999999999999999.0.0")!! > StableVersion.parse("2.0.0")!!)
        for (bad in listOf("v1.0.0", "1.0", "01.0.0", "1.0.0-rc1", "-1.0.0", "1.0.0+build")) {
            assertNull(StableVersion.parse(bad))
        }
    }

    @Test fun validatesExactAssetContractAndRejectsMalformedMetadata() {
        assertEquals("1.2.0", Release.parse(releaseJson().toString().toByteArray())!!.version.name)
        for (flag in listOf("draft", "prerelease")) assertNull(Release.parse(releaseJson().put(flag, true).toString().toByteArray()))
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("tag_name", "v1.2.0-rc1") }, { it.remove("draft") },
            { it.put("assets", JSONArray()) },
            { it.getJSONArray("assets").put(it.getJSONArray("assets").getJSONObject(0)) },
            { it.getJSONArray("assets").getJSONObject(0).put("state", "open") },
            { it.getJSONArray("assets").getJSONObject(0).put("name", "app-release.apk") },
            { it.getJSONArray("assets").getJSONObject(0).put("size", 0) },
            { it.getJSONArray("assets").getJSONObject(0).put("size", MAX_APK_BYTES + 1) },
            { it.getJSONArray("assets").getJSONObject(0).put("size", 1.5) },
            { it.getJSONArray("assets").getJSONObject(0).remove("digest") },
            { it.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:bad") },
            { it.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/other/repo/releases/download/v1.2.0/AniTV-1.2.0.apk") }
        )
        changes.forEach { change ->
            val json = releaseJson().also(change)
            assertThrows(UpdateException::class.java) { Release.parse(json.toString().toByteArray()) }
        }
        assertThrows(UpdateException::class.java) { Release.parse("bad json".toByteArray()) }
        assertThrows(UpdateException::class.java) { readMetadata(ByteArray(MAX_METADATA_BYTES + 1).inputStream()) }
    }

    @Test fun verifiesSizeDigestAndCancellation() {
        val file = Files.createTempFile("apk", ".apk").toFile()
        try {
            file.writeBytes(byteArrayOf(1, 2, 3)); verifyBytes(file, release())
            file.writeBytes(byteArrayOf(1, 2))
            assertEquals(UpdateProblem.SIZE, assertThrows(UpdateException::class.java) { verifyBytes(file, release()) }.problem)
            file.writeBytes(byteArrayOf(3, 2, 1))
            assertEquals(UpdateProblem.DIGEST, assertThrows(UpdateException::class.java) { verifyBytes(file, release()) }.problem)
            val cancellation = DownloadCancellation().apply { cancel() }
            assertThrows(java.util.concurrent.CancellationException::class.java) { verifyBytes(file, release(), cancellation) }
        } finally { file.delete() }
    }

    @Test fun checksApkPackageVersionSdkAndExactSignerSet() {
        val installed = ApkIdentity("indie.fiatsiz.anitv", "1.1.0", 2, 34, setOf("owner"))
        val candidate = installed.copy(versionName = "1.2.0", versionCode = 3)
        validateApk(candidate, installed, release(), 34)
        val invalid = listOf(
            candidate.copy(packageName = "other") to UpdateProblem.PACKAGE,
            candidate.copy(versionName = "1.2.1") to UpdateProblem.VERSION,
            candidate.copy(versionCode = 2) to UpdateProblem.VERSION,
            candidate.copy(minSdk = 35) to UpdateProblem.SDK,
            candidate.copy(certificates = emptySet()) to UpdateProblem.CERTIFICATE,
            candidate.copy(certificates = setOf("attacker")) to UpdateProblem.CERTIFICATE,
            candidate.copy(certificates = setOf("owner", "extra")) to UpdateProblem.CERTIFICATE
        )
        invalid.forEach { (apk, problem) ->
            assertEquals(problem, assertThrows(UpdateException::class.java) { validateApk(apk, installed, release(), 34) }.problem)
        }
    }

    @Test fun retryHeadersRespectLongestDeadlineAndDates() {
        val now = 1_000_000L
        assertEquals(now + 60_000, retryDeadline(ReleaseResponse(429), now))
        assertEquals(now + 120_000, retryDeadline(ReleaseResponse(429, retryAfter = "120"), now))
        val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(now + 180_000).atZone(ZoneOffset.UTC))
        assertEquals(now + 180_000, retryDeadline(ReleaseResponse(403, retryAfter = date), now))
        assertEquals(now + 60_000, retryDeadline(ReleaseResponse(403, retryAfter = "garbage", rateReset = "-1"), now))
    }
}
