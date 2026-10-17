package indie.fiatsiz.anitv.playback

import indie.fiatsiz.anitv.source.OpeningInterval
import org.junit.Assert.*
import org.junit.Test

class OpeningSkipperTest {
    private val opening = OpeningInterval(28_783, 118_783)

    @Test fun skipsInsideOpeningOnceAndAllowsSeekingBackToReplay() {
        val skipper = OpeningSkipper()
        assertNull(skipper.target(opening, 28_782, 1_500_040, true))
        assertEquals(118_783L, skipper.target(opening, 28_783, 1_500_040, true))
        assertNull(skipper.target(opening, 30_000, 1_500_040, true))
        assertEquals(118_783L, OpeningSkipper().target(opening, 60_000, 1_500_040, true))
    }

    @Test fun pausedDisabledUnseekableMenusAndUnknownDurationDoNotConsumeSkip() {
        val skipper = OpeningSkipper()
        assertNull(skipper.target(opening, 30_000, 1_500_040, false))
        assertNull(skipper.target(opening, 30_000, 0, true))
        assertNull(skipper.target(null, 30_000, 1_500_040, true))
        assertNull(skipper.target(OpeningInterval(-1, 100), 0, 1_500_040, true))
        assertNull(skipper.target(OpeningInterval(100, 100), 100, 1_500_040, true))
        assertNull(skipper.target(opening, opening.endMs, 1_500_040, true))
        assertEquals(118_783L, skipper.target(opening, 30_000, 1_500_040, true))
    }
}
