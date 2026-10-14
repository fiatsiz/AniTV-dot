package indie.fiatsiz.anitv.source

import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64

internal object RulesTestSupport {
    val keys by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
    }

    fun payload(revision: Int = 1): JSONObject = JSONObject(
        String(
            Base64.getDecoder().decode(
                JSONObject(
                    SignedRules.bundled.envelope().toString(Charsets.UTF_8)
                ).getString("payload")
            ), Charsets.UTF_8
        )
    ).put("revision", revision)

    fun sign(payload: ByteArray, key: PrivateKey = keys.private): ByteArray {
        val signature =
            Signature.getInstance("SHA256withRSA").run { initSign(key); update(payload); sign() }
        return JSONObject().put("payload", Base64.getEncoder().encodeToString(payload))
            .put("signature", Base64.getEncoder().encodeToString(signature)).toString()
            .toByteArray()
    }

    fun signed(revision: Int = 1, change: (JSONObject) -> Unit = {}): SignedRules {
        val json = payload(revision).also(change)
        return SignedRules.verify(sign(json.toString().toByteArray()), keys.public, 1)
    }
}

/** Both release publication and tests execute the shipping Kotlin parser against expected models. */
internal object RulesFixtures {
    fun validate(directory: File, rules: SourceRules) {
        val manifest = JSONObject(File(directory, "expected.json").readText())
        fun fixture(kind: String) =
            File(directory, manifest.getJSONObject(kind).getString("file")).readText()

        fun items(kind: String) = manifest.getJSONObject(kind).getJSONArray("expected")
        val search = items("search")
        assertEquals((0 until search.length()).map { i ->
            search.getJSONObject(i).let {
                Anime(it.getString("id"), it.getString("title"), it.getString("detailUrl"))
            }
        }, HiAnimeSource.parseSearch(fixture("search"), rules))
        val detail = manifest.getJSONObject("detail").getJSONObject("expected")
        assertEquals(
            AnimeDetails(
                detail.getString("description"), detail.getString("posterUrl"),
                detail.getJSONArray("facts")
                    .let { array -> (0 until array.length()).map { array.getString(it) } }),
            HiAnimeSource.parseDetails(fixture("detail"), rules)
        )
        val episodes = items("episodes")
        assertEquals(
            (0 until episodes.length()).map { i ->
                episodes.getJSONObject(i).let {
                    Episode(it.getString("id"), it.getString("number"), it.getString("title"))
                }
            },
            HiAnimeSource.parseEpisodes(
                rules.episodes.response.html(
                    fixture("episodes"),
                    SourceFailure.EPISODES_LOAD
                ), rules
            )
        )
        val servers = items("servers")
        assertEquals(
            (0 until servers.length()).map { i ->
                servers.getJSONObject(i).let {
                    EpisodeServer(
                        AudioVersion.valueOf(it.getString("audio")),
                        it.getString("embedUrl")
                    )
                }
            },
            HiAnimeSource.parseServers(
                rules.servers.response.html(
                    fixture("servers"),
                    SourceFailure.SERVERS_LOAD
                ), rules
            )
        )
        for (kind in listOf("player", "dubPlayer")) {
            val entry = manifest.getJSONObject(kind)
            val expected = entry.getJSONObject("expected")
            val audio = AudioVersion.valueOf(entry.getString("audio"))
            assertEquals(
                Stream(
                    expected.getString("url"),
                    expected.optString("subtitleUrl").takeIf { it.isNotEmpty() },
                    entry.getString("referer"),
                    audio
                ),
                HiAnimeSource.parsePlayer(fixture(kind), entry.getString("referer"), audio, rules)
            )
        }
    }
}
