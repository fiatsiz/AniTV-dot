package indie.fiatsiz.anitv.source

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import java.util.Base64

class RulesParserTest {
    private fun path(vararg keys: String) = JSONArray(keys.toList())
    private fun field(selector: String, attribute: String = "") =
        JSONObject().put("selector", selector).put("attribute", attribute)

    private fun wrapped(html: String) =
        JSONObject().put("ok", true).put("data", JSONObject().put("markup", html)).toString()

    private fun changeResponse(section: JSONObject) {
        section.put(
            "response",
            JSONObject().put("format", "json").put("htmlPath", path("data", "markup"))
                .put("statusPath", path("ok"))
        )
    }

    private fun changedRules(): SignedRules = RulesTestSupport.signed(2) { json ->
        json.getJSONObject("search").apply {
            put("endpoint", "/find?text={query}"); put("container", ".listing"); put(
            "items",
            ".listing article"
        )
            put("id", field(".watch", "data-anime")); put(
            "title",
            field(".name", "data-title")
        ); put("link", field(".watch", "data-url"))
            changeResponse(this)
        }
        json.getJSONObject("detail").apply {
            put("root", "#details"); put(
            "description",
            field(".synopsis", "data-summary")
        ); put("poster", field(".portrait", "data-image"))
            put("facts", ".fact"); put("factName", ".label"); put(
            "factNames",
            path("Year")
        ); changeResponse(this)
        }
        json.getJSONObject("episodes").apply {
            put("endpoint", "/episodes/{animeId}"); put("items", ".entry"); put(
            "idAttribute",
            "data-ep"
        )
            put("numberAttribute", "data-no"); put("titleAttribute", "data-label"); changeResponse(
            this
        )
        }
        json.getJSONObject("servers").apply {
            put("endpoint", "/servers/{episodeId}"); put("items", ".host"); put(
            "audioAttribute",
            "kind"
        ); put("nameAttribute", "provider")
            put("name", "zokoanime"); put("sub", "captions"); put(
            "dub",
            "voice"
        ); put("embedAttribute", "link"); put("base64", false)
            changeResponse(this)
        }
        json.getJSONObject("player").apply {
            put("variable", "window.PAYLOAD"); put("key", "changed-public-key")
            put("streamPath", path("media", "url")); put("subtitlesPath", path("media", "tracks"))
            put("subtitleUrlPath", path("file", "url")); put(
            "subtitleDefaultPath",
            path("selected", "value")
        )
        }
    }

    @Test
    fun sameCompiledSourceAcceptsNewSignedRulesAndChangedResponses() {
        val newer = changedRules()
        val old = RulesTestSupport.signed()
        val repo = RulesRepository(
            RulesCache(Files.createTempDirectory("rules-parser").toFile()),
            RulesTransport { _, _ -> RulesResponse(200, newer.envelope()) },
            old,
            RulesTestSupport.keys.public
        )
        val requests = mutableListOf<String>()
        val anime = Anime("42", "Changed title", "https://hianime.at/title-42")
        val episode = Episode("7", "2", "Changed episode")
        val source = HiAnimeSource("test", repo::snapshot) { url, _, _ ->
            requests += url
            val body = when {
                url.contains("/find?") -> wrapped("<div class='listing'><article><i class='name' data-title='Changed title'></i><a class='watch' data-anime='42' data-url='/title-42'></a></article></div>")
                url.endsWith("/title-42") -> wrapped("<div id='details'><i class='synopsis' data-summary='Changed summary'></i><i class='portrait' data-image='https://image.example/p.webp'></i><div class='fact'><b class='label'>Year:</b> 2026</div></div>")
                url.endsWith("/episodes/42") -> wrapped("<a class='entry' data-ep='7' data-no='2' data-label='Changed episode'></a>")
                url.endsWith("/servers/7") -> wrapped("<div class='host' kind='captions' provider='zokoanime' link='https://player.example/new'></div>")
                url == "https://player.example/new" -> {
                    val data = JSONObject().put(
                        "media", JSONObject().put("url", "https://video.example/new.m3u8").put(
                            "tracks",
                            JSONArray().put(
                                JSONObject().put(
                                    "file",
                                    JSONObject().put("url", "https://video.example/new.vtt")
                                )
                                    .put("selected", JSONObject().put("value", true))
                            )
                        )
                    )
                    val clear = data.toString().toByteArray()
                    val key = newer.rules.player.key
                    val encoded = Base64.getEncoder()
                        .encodeToString(ByteArray(clear.size) { i -> (clear[i].toInt() xor key[i % key.length].code).toByte() })
                    "window.PAYLOAD = '$encoded';"
                }

                else -> error("Unexpected request")
            }
            body.toByteArray()
        }
        assertEquals(listOf(anime), source.search(" changed title "))
        assertEquals(
            AnimeDetails(
                "Changed summary",
                "https://image.example/p.webp",
                listOf("Year: 2026")
            ), source.details(anime)
        )
        assertEquals(listOf(episode), source.episodes(anime))
        val server = source.servers(episode).single()
        assertEquals(
            Stream(
                "https://video.example/new.m3u8",
                "https://video.example/new.vtt",
                "https://player.example/"
            ), source.stream(server)
        )
        assertEquals(
            listOf(
                "https://hianime.at/find?text=changed+title",
                "https://hianime.at/title-42",
                "https://hianime.at/episodes/42",
                "https://hianime.at/servers/7",
                "https://player.example/new"
            ), requests
        )
        assertEquals(2L, repo.snapshot().revision)
    }

    @Test
    fun decodingFlagsSupportExistingAlgorithmsOnly() {
        for (base64 in listOf(false, true)) {
            val signed = RulesTestSupport.signed(2) {
                it.getJSONObject("player").put("xor", false).put("base64", base64)
            }
            val data =
                """{"src":"https://video.example/plain.m3u8","subtitles":[{"src":"https://video.example/plain.vtt","default":true}]}"""
            val blob = if (base64) Base64.getEncoder().encodeToString(data.toByteArray()) else data
            assertEquals(
                "https://video.example/plain.m3u8",
                HiAnimeSource.parsePlayer(
                    "window.__P = '$blob';",
                    "https://player.example/",
                    rules = signed.rules
                ).url
            )
        }
    }

    @Test
    fun numericIdsAndHttpsAndSubtitleRequirementsStayCompiled() {
        val source = HiAnimeSource(
            "test",
            { changedRules().rules }) { _, _, _ -> error("Must reject before network") }
        assertThrows(SourceException::class.java) { source.episodes(Anime("../bad", "Invalid")) }
        assertThrows(SourceException::class.java) {
            source.servers(
                Episode(
                    "1&bad=1",
                    "1",
                    "Invalid"
                )
            )
        }
        assertThrows(SourceException::class.java) {
            source.stream(
                EpisodeServer(
                    AudioVersion.SUB,
                    "http://player.example/"
                )
            )
        }
    }

    @Test
    fun responsePathsHandleHtmlAndJsonAndReportMissingOrFailedFields() {
        val json = ResponseRules("json", listOf("data", "html"), listOf("ok"))
        assertEquals(
            "<p>ok</p>",
            json.html("""{"ok":true,"data":{"html":"<p>ok</p>"}}""", SourceFailure.EPISODES_LOAD)
        )
        for (body in listOf("invalid", "{}", """{"ok":false,"data":{"html":"anything"}}""")) {
            assertEquals(
                SourceFailure.EPISODES_LOAD,
                assertThrows(SourceException::class.java) {
                    json.html(
                        body,
                        SourceFailure.EPISODES_LOAD
                    )
                }.failure
            )
        }
        assertEquals(
            "<p>ok</p>",
            ResponseRules("html", emptyList(), emptyList()).html(
                "<p>ok</p>",
                SourceFailure.EPISODES_LOAD
            )
        )
    }

    @Test
    fun oneOperationCapturesOneImmutableSnapshotIncludingDetailSearchFallback() {
        var captures = 0
        val source = HiAnimeSource("test", { captures++; SourceRules.bundled }) { url, _, _ ->
            javaClass.getResource(if (url.contains("/search?")) "/hianime-search.html" else "/hianime-detail.html")!!
                .readBytes()
        }
        source.details(Anime("1", "One Piece"))
        assertEquals(1, captures)
    }
}
