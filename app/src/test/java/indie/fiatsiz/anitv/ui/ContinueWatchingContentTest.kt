package indie.fiatsiz.anitv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingContentTest {
    @Test fun formatsSavedPositionWithSecondsAndLongEpisodeTimes() {
        assertEquals("0:00", formatResumePosition(-1))
        assertEquals("0:00", formatResumePosition(0))
        assertEquals("0:59", formatResumePosition(59_999))
        assertEquals("1:00", formatResumePosition(60_000))
        assertEquals("12:33", formatResumePosition(753_123))
        assertEquals("120:05", formatResumePosition(7_205_000))
    }
}
