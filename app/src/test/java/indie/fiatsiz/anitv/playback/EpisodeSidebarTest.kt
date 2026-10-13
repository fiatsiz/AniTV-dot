package indie.fiatsiz.anitv.playback

import indie.fiatsiz.anitv.source.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeSidebarTest {
    @Test fun currentEpisodeUsesIdInsteadOfEpisodeNumber() {
        val episodes = listOf(Episode("10", "1", "Special"), Episode("11", "1", "Episode 1"))
        assertEquals(1, episodeSidebarFocusIndex(episodes, "11"))
        assertEquals(0, episodeSidebarFocusIndex(episodes, "10"))
    }

    @Test fun missingCurrentEpisodeFallsBackWithoutInvalidScrollIndex() {
        assertEquals(-1, episodeSidebarFocusIndex(emptyList(), "11"))
        assertEquals(0, episodeSidebarFocusIndex(listOf(Episode("10", "1", "Episode 1")), "11"))
    }

    @Test fun currentEpisodeCanBeFarBeyondTheInitialViewport() {
        val episodes = (1..1200).map { Episode("$it", "$it", "Episode $it") }
        assertEquals(999, episodeSidebarFocusIndex(episodes, "1000"))
        assertEquals(1199, episodeSidebarFocusIndex(episodes, "1200"))
    }
}
