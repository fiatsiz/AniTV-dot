package indie.fiatsiz.anitv.playback

import indie.fiatsiz.anitv.source.OpeningInterval
import org.junit.Assert.*
import org.junit.Test

class OpeningSkipperTest {
    private val opening = OpeningInterval(28_783, 118_783)

    @Test fun offersSkipOnlyInsideOpeningAndOffersItAgainAfterSeekingBack() {
        assertNull(openingSkipTarget(opening, 28_782, 1_500_040, true))
        assertEquals(118_783L, openingSkipTarget(opening, 28_783, 1_500_040, true))
        assertNull(openingSkipTarget(opening, 118_783, 1_500_040, true))
        assertEquals(118_783L, openingSkipTarget(opening, 60_000, 1_500_040, true))
        assertEquals(118_783L, openingSkipTarget(opening, 60_000, 1_500_040, true))
    }

    @Test fun disabledUnknownUnseekableAndInvalidIntervalsHaveNoTarget() {
        assertNull(openingSkipTarget(opening, 30_000, 1_500_040, false))
        assertNull(openingSkipTarget(opening, 30_000, 0, true))
        assertNull(openingSkipTarget(null, 30_000, 1_500_040, true))
        assertNull(openingSkipTarget(OpeningInterval(-1, 100), 0, 1_500_040, true))
        assertNull(openingSkipTarget(OpeningInterval(100, 100), 100, 1_500_040, true))
        assertNull(openingSkipTarget(opening, opening.endMs, 1_500_040, true))
        assertEquals(118_783L, openingSkipTarget(opening, 30_000, 1_500_040, true))
    }
}
