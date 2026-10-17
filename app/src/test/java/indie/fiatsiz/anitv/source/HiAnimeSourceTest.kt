package indie.fiatsiz.anitv.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HiAnimeSourceTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test
    fun extractsOptionalRomanizedTitleWithoutChangingDisplayTitle() {
        val html = fixture("hianime-search.html").replace("class=\"dynamic-name\"", "class=\"dynamic-name\" data-jname=\"Wan Piisu\"")
        val anime = HiAnimeSource.parseSearch(html).single()
        assertEquals("One Piece", anime.title)
        assertEquals("Wan Piisu", anime.japaneseTitle)
        assertEquals(null, HiAnimeSource.parseSearch(fixture("hianime-search.html")).single().japaneseTitle)
    }

    @Test
    fun parsesCurrentSearchAndEpisodes() {
        assertEquals(
            listOf(Anime("1", "One Piece", "https://hianime.at/one-piece-1")),
            HiAnimeSource.parseSearch(fixture("hianime-search.html"))
        )
        assertEquals(
            listOf(Episode("1", "1", "Episode 1")),
            HiAnimeSource.parseEpisodes(fixture("hianime-episodes.html"))
        )
    }

    @Test
    fun parsesCurrentDetailAndRejectsChangedMarkup() {
        assertEquals(
            AnimeDetails(
                "Gold Roger inspired the Great Pirate Era.",
                "https://hianime.at/storage/media/2026/08/one-piece.webp",
                listOf(
                    "Aired: Oct 20, 1999 to ?",
                    "Status: Currently Airing",
                    "Genres: Adventure Fantasy"
                )
            ),
            HiAnimeSource.parseDetails(fixture("hianime-detail.html"))
        )
        assertEquals(
            SourceFailure.DETAIL_CHANGED,
            assertThrows(SourceException::class.java) { HiAnimeSource.parseDetails("<html></html>") }.failure
        )
        assertEquals(SourceFailure.INSECURE_LINK, assertThrows(SourceException::class.java) {
            HiAnimeSource.parseDetails(
                fixture("hianime-detail.html").replace(
                    "anisc-poster",
                    "missing-poster"
                )
            )
        }.failure)
    }

    @Test
    fun reportsChangedMarkup() {
        assertEquals(
            SourceFailure.SEARCH_CHANGED,
            assertThrows(SourceException::class.java) { HiAnimeSource.parseSearch("<html></html>") }.failure
        )
        assertEquals(
            SourceFailure.NO_EPISODES,
            assertThrows(SourceException::class.java) { HiAnimeSource.parseEpisodes("<div></div>") }.failure
        )
    }

    @Test
    fun decodesSubtitlePlayer() {
        assertEquals(
            Stream(
                "https://video.example/master.m3u8",
                "https://video.example/en.vtt",
                "https://zokoanime.video/"
            ),
            HiAnimeSource.parsePlayer(fixture("hianime-player.html"), "https://zokoanime.video/")
        )
    }

    @Test
    fun parsesAvailableAudioWithoutInventingADub() {
        val sub = EpisodeServer(AudioVersion.SUB, "https://player.example/episode-1/sub")
        val dub = EpisodeServer(AudioVersion.DUB, "https://player.example/episode-1/dub")
        assertEquals(listOf(sub, dub), HiAnimeSource.parseServers(fixture("hianime-servers.html")))
        assertEquals(
            listOf(sub),
            HiAnimeSource.parseServers(fixture("hianime-servers-sub-only.html"))
        )
        val dubOnly =
            org.jsoup.Jsoup.parse(fixture("hianime-servers.html"))
                .select("[data-type=dub]").outerHtml()
        assertEquals(listOf(dub), HiAnimeSource.parseServers(dubOnly))
    }

    @Test
    fun reportsUnsupportedServersAndMalformedOrInsecureEmbeds() {
        assertEquals(SourceFailure.NO_SUPPORTED_SERVER, assertThrows(SourceException::class.java) {
            HiAnimeSource.parseServers("<div class=\"server-item\" data-type=\"dub\" data-server-name=\"HD-1\"></div>")
        }.failure)
        assertEquals(SourceFailure.SERVER_LINK_CHANGED, assertThrows(SourceException::class.java) {
            HiAnimeSource.parseServers("<div class=\"server-item\" data-type=\"dub\" data-server-name=\"ZokoAnime\" data-hash=\"!\"></div>")
        }.failure)
        val insecure =
            java.util.Base64.getEncoder().encodeToString("http://player.example/dub".toByteArray())
        assertEquals(SourceFailure.INSECURE_LINK, assertThrows(SourceException::class.java) {
            HiAnimeSource.parseServers("<div class=\"server-item\" data-type=\"dub\" data-server-name=\"ZokoAnime\" data-hash=\"$insecure\"></div>")
        }.failure)
    }

    @Test
    fun allowsDubWithoutSubtitlesButPreservesSubtitleRequirementForSub() {
        assertEquals(
            Stream(
                "https://video.example/dub.m3u8",
                null,
                "https://player.example/",
                AudioVersion.DUB
            ),
            HiAnimeSource.parsePlayer(
                fixture("hianime-dub-player.html"),
                "https://player.example/",
                AudioVersion.DUB
            )
        )
        assertEquals(SourceFailure.NO_SUBTITLE, assertThrows(SourceException::class.java) {
            HiAnimeSource.parsePlayer(
                fixture("hianime-dub-player.html"),
                "https://player.example/",
                AudioVersion.SUB
            )
        }.failure)
        assertEquals(SourceFailure.NO_SUBTITLE, assertThrows(SourceException::class.java) {
            HiAnimeSource("test").subtitleCues(
                Stream(
                    "https://video.example/dub.m3u8",
                    null,
                    "https://player.example/",
                    AudioVersion.DUB
                )
            )
        }.failure)
    }

    @Test
    fun keepsAvailableDubCaptions() {
        assertEquals(
            Stream(
                "https://video.example/master.m3u8",
                "https://video.example/en.vtt",
                "https://player.example/",
                AudioVersion.DUB
            ),
            HiAnimeSource.parsePlayer(
                fixture("hianime-player.html"),
                "https://player.example/",
                AudioVersion.DUB
            )
        )
    }
}
