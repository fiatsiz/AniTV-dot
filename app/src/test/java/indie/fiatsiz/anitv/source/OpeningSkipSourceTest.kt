package indie.fiatsiz.anitv.source

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OpeningSkipSourceTest {
    private val search = """{"categories":[{"type":"anime","items":[
        {"id":21,"type":"anime","name":"One Piece"},
        {"id":459,"type":"anime","name":"One Piece Movie 01"}]}]}"""
    private fun times(start: String = "28.783", end: String = "118.783", length: String = "1500.04", type: String = "op") =
        """{"found":true,"results":[{"interval":{"startTime":$start,"endTime":$end},
            "skipType":"$type","episodeLength":$length}]}"""

    @Test fun usesMalIdInsteadOfHiAnimeIdAndPreservesFractionalSeconds() {
        val requests = mutableListOf<String>()
        val source = OpeningSkipSource("fixture") { url ->
            requests.add(url)
            if (url.contains("myanimelist.net")) search else times()
        }
        assertEquals(OpeningInterval(28_783, 118_783), source.opening(
            Anime("1", "One Piece"), Episode("55", "1", "Episode 1"), 1_500_040
        ))
        assertEquals(listOf(
            "https://myanimelist.net/search/prefix.json?type=anime&keyword=One+Piece&v=1",
            "https://api.aniskip.com/v2/skip-times/21/1?types[]=op&episodeLength=1500.04"
        ), requests)
    }

    @Test fun matchesRomanizedTitleAndRejectsFuzzyOrAmbiguousTitles() {
        assertEquals(21, OpeningSkipSource.matchingMalId(search, listOf(" one   PIECE ")))
        assertNull(OpeningSkipSource.matchingMalId(search, listOf("One Piece Season 2")))
        val ambiguous = search.replace("459", "999").replace("One Piece Movie 01", "One Piece")
        assertNull(OpeningSkipSource.matchingMalId(ambiguous, listOf("One Piece")))
        val source = OpeningSkipSource("fixture") { url ->
            if (url.contains("myanimelist.net")) {
                assertTrue(url.contains("keyword=Takopii+no+Genzai"))
                search.replace("One Piece", "Takopii no Genzai")
            } else times()
        }
        assertNotNull(source.opening(Anime("329", "Takopi's Original Sin", japaneseTitle = "Takopii no Genzai"),
            Episode("55", "1", "Episode 1"), 1_500_040))
    }

    @Test fun rejectsMissingMalformedUnsafeAndWrongCutIntervals() {
        assertNull(OpeningSkipSource.parseOpening("""{"found":false}""", 1_500_040))
        for (body in listOf(times("-1"), times(end = "0"), times(end = "1501"),
            times(length = "1400"), times(type = "ed"), times(type = "mixed-op"),
            times(start = "\"28.783\""))) {
            assertNull(OpeningSkipSource.parseOpening(body, 1_500_040))
        }
        assertNull(OpeningSkipSource.parseOpening(times(), 0))
        assertNotNull(OpeningSkipSource.parseOpening(times(length = "1471"), 1_500_040))
        assertNull(OpeningSkipSource.parseOpening(times(length = "1469"), 1_500_040))
        assertThrows(org.json.JSONException::class.java) { OpeningSkipSource.parseOpening("broken", 1_500_040) }
        val conflicting = times().replace(
            "\"results\":[", "\"results\":[{\"interval\":{\"startTime\":30,\"endTime\":120},\"skipType\":\"op\",\"episodeLength\":1500},"
        )
        assertNull(OpeningSkipSource.parseOpening(conflicting, 1_500_040))
    }

    @Test fun missingMatchSkipsAniSkipAndInvalidEpisodeSkipsAllRequests() {
        var requests = 0
        val source = OpeningSkipSource("fixture") { requests++; search }
        val anime = Anime("1", "Unknown")
        assertNull(source.opening(anime, Episode("55", "1", "Episode 1"), 1_500_040))
        assertEquals(1, requests)
        for (number in listOf("Special", "-1", "1/2", "NaN", "1?types[]=ed")) {
            assertNull(source.opening(anime, Episode("55", number, number), 1_500_040))
        }
        assertNull(source.opening(anime, Episode("55", "1", "Episode 1"), 0))
        assertEquals(1, requests)
    }

    @Test fun networkFailureDoesNotBecomeAnOpening() {
        val source = OpeningSkipSource("fixture") { throw IOException("offline") }
        assertThrows(IOException::class.java) {
            source.opening(Anime("1", "One Piece"), Episode("55", "1", "Episode 1"), 1_500_040)
        }
    }
}
