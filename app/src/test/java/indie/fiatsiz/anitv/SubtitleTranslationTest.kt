package indie.fiatsiz.anitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTranslationTest {
    @Test
    fun batchesAtMostFourCues() {
        assertEquals(4, subtitleBatchEnd(0, 10))
        assertEquals(10, subtitleBatchEnd(8, 10))
    }

    @Test
    fun startsNextBatchWithTwoCuesStillAhead() {
        assertFalse(shouldPrefetchNextBatch(0, 3))
        assertTrue(shouldPrefetchNextBatch(1, 3))
    }

    @Test
    fun deeplUsesSupportedRegionalTargets() {
        assertEquals("EN-US", deeplTargetLanguage("en"))
        assertEquals("PT-BR", deeplTargetLanguage("pt"))
        assertEquals("TR", deeplTargetLanguage("tr"))
    }
}
