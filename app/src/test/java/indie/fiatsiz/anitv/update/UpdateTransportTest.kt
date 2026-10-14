package indie.fiatsiz.anitv.update

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class UpdateTransportTest {
    private class Response(url: URL, val status: Int = 200, val body: ByteArray = byteArrayOf(1, 2, 3),
        val headers: Map<String, String> = emptyMap(), val length: Long = body.size.toLong()) : HttpURLConnection(url) {
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getInputStream() = body.inputStream()
        override fun getHeaderField(name: String) = headers[name]
        override fun getContentLengthLong() = length
    }

    @Test fun metadataUsesConditionalHeadersRejectsRedirectsAndOversizedBodies() {
        val response = Response(URL(RELEASE_API), 304, headers = mapOf("ETag" to "new"))
        val transport = GitHubUpdateTransport { response }
        assertEquals(304, transport.latest("old").status)
        assertEquals("old", response.getRequestProperty("If-None-Match"))
        assertEquals("application/vnd.github+json", response.getRequestProperty("Accept"))
        assertFalse(response.instanceFollowRedirects)
        assertTrue(response.closed)
        assertEquals(302, GitHubUpdateTransport { Response(it, 302) }.latest(null).status)
        assertThrows(UpdateException::class.java) {
            GitHubUpdateTransport { Response(it, body = ByteArray(MAX_METADATA_BYTES + 1), length = -1) }.latest(null)
        }
        assertThrows(UpdateException::class.java) {
            GitHubUpdateTransport { Response(it, length = MAX_METADATA_BYTES + 1L) }.latest(null)
        }
    }

    @Test fun permitsOnlyHttpsReleaseHostsAndAtMostFiveRedirects() {
        for (bad in listOf("http://github.com/path", "https://evil.example/path", "https://github.com.evil/path",
            "https://user@github.com/path", "https://github.com:444/path", "file:///path", "https://github.com/path#fragment")) {
            assertThrows(UpdateException::class.java) { validateRedirect(bad) }
        }
        for (host in listOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
            validateRedirect("https://$host/path?token=value")
        }
        val file = Files.createTempFile("updater-transport", ".part").toFile()
        try {
            var calls = 0
            val progress = mutableListOf<Long>()
            GitHubUpdateTransport { url ->
                calls++
                if (calls <= 5) Response(url, 302, headers = mapOf("Location" to "https://objects.githubusercontent.com/asset-$calls"))
                else Response(url)
            }.download(release(), file, DownloadCancellation(), progress::add)
            assertEquals(6, calls)
            assertEquals(listOf(3L), progress)
            assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
            calls = 0
            assertThrows(UpdateException::class.java) {
                GitHubUpdateTransport { url -> calls++; Response(url, 302, headers = mapOf("Location" to "https://github.com/loop")) }
                    .download(release(), file, DownloadCancellation()) {}
            }
            assertEquals(6, calls)
            assertThrows(UpdateException::class.java) {
                GitHubUpdateTransport { Response(it, 302, headers = mapOf("Location" to "https://evil.example/asset")) }
                    .download(release(), file, DownloadCancellation()) {}
            }
        } finally { file.delete() }
    }

    @Test fun rejectsOversizedTruncatedWrongLengthAndHttpFailures() {
        val file = Files.createTempFile("updater-size", ".part").toFile()
        try {
            val variants: List<(URL) -> Response> = listOf(
                { Response(it, body = byteArrayOf(1, 2), length = -1) },
                { Response(it, body = byteArrayOf(1, 2, 3, 4), length = -1) },
                { Response(it, length = MAX_APK_BYTES + 1) },
                { Response(it, status = 404) }
            )
            for (factory in variants) assertThrows(UpdateException::class.java) {
                GitHubUpdateTransport(factory).download(release(), file, DownloadCancellation()) {}
            }
            val response = Response(URL(release().url))
            val cancellation = DownloadCancellation()
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                GitHubUpdateTransport { response }.download(release(), file, cancellation) { cancellation.cancel() }
            }
            assertTrue(response.closed)
        } finally { file.delete() }
    }
}
