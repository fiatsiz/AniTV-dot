package indie.fiatsiz.anitv.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HiAnimeSourceTest {
    private fun fixture(name: String) = javaClass.getResource("/$name")!!.readText()

    @Test fun parsesCurrentSearchAndEpisodes() {
        assertEquals(listOf(Anime("1", "One Piece", "https://hianime.at/one-piece-1")), HiAnimeSource.parseSearch(fixture("hianime-search.html")))
        assertEquals(listOf(Episode("1", "1", "Episode 1")), HiAnimeSource.parseEpisodes(fixture("hianime-episodes.html")))
    }

    @Test fun parsesCurrentDetailAndRejectsChangedMarkup() {
        assertEquals(AnimeDetails("Gold Roger inspired the Great Pirate Era.",
            "https://hianime.at/storage/media/2026/08/one-piece-banner.webp",
            listOf("Aired: Oct 20, 1999 to ?", "Status: Currently Airing", "Genres: Adventure Fantasy")),
            HiAnimeSource.parseDetails(fixture("hianime-detail.html")))
        assertThrows(SourceException::class.java) { HiAnimeSource.parseDetails("<html></html>") }
    }

    @Test fun reportsChangedMarkup() {
        assertThrows(SourceException::class.java) { HiAnimeSource.parseSearch("<html></html>") }
        assertThrows(SourceException::class.java) { HiAnimeSource.parseEpisodes("<div></div>") }
    }

    @Test fun decodesSubtitlePlayer() {
        assertEquals(
            Stream("https://video.example/master.m3u8", "https://video.example/en.vtt", "https://zokoanime.video/"),
            HiAnimeSource.parsePlayer(fixture("hianime-player.html"), "https://zokoanime.video/")
        )
    }
}
