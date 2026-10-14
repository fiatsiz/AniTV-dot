package indie.fiatsiz.anitv.source

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs the installed APK's parser on Android's JSON, regex and crypto implementations. */
@RunWith(AndroidJUnit4::class)
class SignedRulesInstrumentedTest {
    @Test
    fun installedParserAcceptsNewSignedRevisionWithoutRecompilation() {
        assertEquals(1L, SignedRules.bundled.rules.revision)
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        fun payload(): JSONObject = JSONObject(
            String(
                Base64.getDecoder().decode(
                    JSONObject(
                        SignedRules.bundled.envelope().toString(Charsets.UTF_8)
                    ).getString("payload")
                ), Charsets.UTF_8
            )
        )

        fun sign(json: JSONObject): SignedRules {
            val bytes = json.toString().toByteArray()
            val signature = Signature.getInstance("SHA256withRSA")
                .run { initSign(keys.private); update(bytes); sign() }
            val envelope = JSONObject().put("payload", Base64.getEncoder().encodeToString(bytes))
                .put("signature", Base64.getEncoder().encodeToString(signature)).toString()
                .toByteArray()
            return SignedRules.verify(envelope, keys.public, 1)
        }

        val original = sign(payload())
        val newer = sign(payload().put("revision", 2).apply {
            getJSONObject("search").apply {
                put("endpoint", "/find?query={query}"); put("container", ".cards"); put(
                "items",
                ".cards .entry"
            )
                put("id", JSONObject().put("selector", ".title").put("attribute", "data-key"))
                put("title", JSONObject().put("selector", ".title").put("attribute", ""))
                put("link", JSONObject().put("selector", ".title").put("attribute", "data-link"))
                put(
                    "response",
                    JSONObject().put("format", "json")
                        .put("htmlPath", JSONArray(listOf("data", "markup")))
                        .put("statusPath", JSONArray(listOf("ok")))
                )
            }
        })
        val directory = Files.createTempDirectory(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.toPath(),
            "rules-proof"
        ).toFile()
        val release = CountDownLatch(1)
        val repo = RulesRepository(RulesCache(directory), RulesTransport { _, _ ->
            check(release.await(2, TimeUnit.SECONDS))
            RulesResponse(200, newer.envelope(), "new-revision")
        }, original, keys.public)
        val html =
            "<div class='cards'><div class='entry'><a class='title' data-key='42' data-link='/changed-42'>Changed fixture</a></div></div>"
        val body =
            JSONObject().put("ok", true).put("data", JSONObject().put("markup", html)).toString()
        val source = HiAnimeSource("instrumented-test", repo::snapshot) { url, _, _ ->
            assertEquals("https://hianime.at/find?query=changed+fixture", url)
            body.toByteArray()
        }
        try {
            release.countDown()
            assertEquals(
                listOf(Anime("42", "Changed fixture", "https://hianime.at/changed-42")),
                source.search("changed fixture")
            )
            assertEquals(2L, repo.snapshot().revision)
            assertEquals(RulesOutcome.UPDATED, repo.state.outcome)
            assertThrows(SourceException::class.java) {
                HiAnimeSource.parseSearch(
                    body,
                    original.rules
                )
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (!java.io.File(
                    directory,
                    "current.json"
                ).isFile && System.nanoTime() < deadline
            ) Thread.sleep(10)
            assertTrue(java.io.File(directory, "current.json").isFile)
        } finally {
            release.countDown(); directory.deleteRecursively()
        }
    }
}
