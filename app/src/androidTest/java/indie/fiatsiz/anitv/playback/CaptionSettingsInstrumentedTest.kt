package indie.fiatsiz.anitv.playback

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.TranslationProvider
import indie.fiatsiz.anitv.TranslationSettings
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CaptionSettingsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val mode = mutableStateOf(CaptionMode.ORIGINAL)
    private val translation = mutableStateOf(TranslationSettings())
    private var menu = CaptionMenu.OPTIONS

    private fun content(initialMode: CaptionMode, translated: Boolean = false) {
        mode.value = initialMode
        if (translated) translation.value = TranslationSettings(TranslationProvider.DEEPL, "fixture")
        compose.setContent {
            val captionFocus = remember { FocusRequester() }
            val languageFocus = remember { FocusRequester() }
            val styleFocus = remember { FocusRequester() }
            val choiceFocus = remember { FocusRequester() }
            AniTVTheme {
                CaptionSettings(
                    menu, mode.value, translation.value, "en", captionFocus, languageFocus,
                    styleFocus, choiceFocus, onMenu = { menu = it }, onMode = { mode.value = it },
                    onTranslationSettings = { translation.value = it }
                )
            }
            LaunchedEffect(Unit) { captionFocus.requestFocus() }
        }
    }

    private fun toggle(label: Int) = compose.onNodeWithText(context.getString(label))
        .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }

    private fun assertMode(expected: CaptionMode, label: Int) {
        compose.onNodeWithText(context.getString(label)).assertIsFocused()
        compose.runOnIdle {
            assertEquals(expected, mode.value)
            assertEquals(CaptionMenu.OPTIONS, menu)
        }
    }

    @Test
    fun originalCaptionsShowCurrentStateAndToggleWithoutLosingFocus() {
        content(CaptionMode.ORIGINAL)
        toggle(R.string.captions_on)
        assertMode(CaptionMode.OFF, R.string.captions_off)
        toggle(R.string.captions_off)
        assertMode(CaptionMode.ORIGINAL, R.string.captions_on)
    }

    @Test
    fun initiallyOffCaptionsEnableOriginalCaptions() {
        content(CaptionMode.OFF)
        toggle(R.string.captions_off)
        assertMode(CaptionMode.ORIGINAL, R.string.captions_on)
    }

    @Test
    fun translatedCaptionsShowOnAndRestoreTranslationAfterToggle() {
        content(CaptionMode.TRANSLATED, translated = true)
        toggle(R.string.captions_on)
        assertMode(CaptionMode.OFF, R.string.captions_off)
        toggle(R.string.captions_off)
        assertMode(CaptionMode.TRANSLATED, R.string.captions_on)
    }

    @Test
    fun unavailableTranslationFallsBackToOriginalWhenEnabled() {
        content(CaptionMode.TRANSLATED, translated = true)
        toggle(R.string.captions_on)
        compose.runOnIdle { translation.value = TranslationSettings() }
        toggle(R.string.captions_off)
        assertMode(CaptionMode.ORIGINAL, R.string.captions_on)
    }
}
