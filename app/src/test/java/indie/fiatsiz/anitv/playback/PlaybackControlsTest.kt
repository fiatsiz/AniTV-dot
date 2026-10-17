package indie.fiatsiz.anitv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackControlsTest {
    @Test
    fun tenSecondSeeksClampAtMediaBounds() {
        assertEquals(30_000L, seekPosition(20_000, 10_000, 60_000))
        assertEquals(10_000L, seekPosition(20_000, -10_000, 60_000))
        assertEquals(0L, seekPosition(5_000, -10_000, 60_000))
        assertEquals(60_000L, seekPosition(55_000, 10_000, 60_000))
        assertEquals(0L, seekPosition(5_000, 10_000, -1))
    }

    @Test
    fun elapsedTimeSupportsUnknownDurationAndLongEpisodes() {
        assertEquals("0:00", playbackTime(-1))
        assertEquals("0:09", playbackTime(9_000))
        assertEquals("65:01", playbackTime(3_901_000))
    }
}
