package indie.fiatsiz.anitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTranslationTest {
    @Test
    fun captionDefaultsAndSizesStayValid() {
        val style = CaptionStyleSettings()
        assertEquals(CaptionFont.SANS, style.font)
        assertEquals(100, style.sizePercent)
        assertEquals(CaptionColor.BLACK, style.background)
        assertEquals(0xCC000000.toInt(), style.backgroundArgb)
        assertEquals(CaptionColor.WHITE, style.foreground)
        assertEquals(listOf(50, 75, 100, 150, 200), captionSizes)
        assertEquals(100, captionSizePercent(123))
    }

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
