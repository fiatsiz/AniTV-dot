package indie.fiatsiz.anitv.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.OpeningSkipSource
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OpeningSkipInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dpadToggleRetainsFocusAndReportsUnavailableWithoutBlockingOtherControls() {
        var enabled by mutableStateOf(true)
        var status by mutableStateOf(OpeningStatus.READY)
        var back = false
        compose.setContent {
            val first = remember { FocusRequester() }
            AniTVTheme {
                Column(
                    Modifier
                        .padding(32.dp)
                        .width(320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(first)
                    ) { Text("Before") }
                    OpeningSkipSettings(enabled, status) { enabled = !enabled }
                    Button(onClick = { back = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            context.getString(R.string.back)
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { first.requestFocus() }
        }
        fun key(value: Key) = compose.onRoot().performKeyInput { pressKey(value) }
        fun toggle(label: Int) = compose.onNodeWithText(context.getString(label)).assertIsFocused()
        compose.onNodeWithText("Before").assertIsFocused()
        key(Key.DirectionDown)
        toggle(R.string.opening_skip_on)
        key(Key.DirectionCenter)
        toggle(R.string.opening_skip_off)
        compose.runOnIdle { assertFalse(enabled) }
        key(Key.DirectionCenter)
        toggle(R.string.opening_skip_on)
        compose.runOnIdle { status = OpeningStatus.UNAVAILABLE }
        compose.onNodeWithText(context.getString(R.string.opening_skip_unavailable))
            .assertIsDisplayed()
        toggle(R.string.opening_skip_on)
        key(Key.DirectionDown)
        compose.onNodeWithText(context.getString(R.string.back)).assertIsFocused()
        key(Key.DirectionCenter)
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun installedAndroidParserResolvesRomanizedTitleAndOffersManualTarget() {
        val source = OpeningSkipSource("fixture") { url ->
            if (url.contains("myanimelist.net"))
                """{"categories":[{"type":"anime","items":[{"id":60489,"type":"anime","name":"Takopii no Genzai"}]}]}"""
            else """{"found":true,"results":[{"interval":{"startTime":22.5,"endTime":112.5},"skipType":"op","episodeLength":1400}]}"""
        }
        val interval = source.opening(
            Anime("329", "Takopi's Original Sin", japaneseTitle = "Takopii no Genzai"),
            Episode("99", "1", "Episode 1"), 1_400_000
        )
        assertEquals(112_500L, openingSkipTarget(interval, 22_500, 1_400_000, true))
        assertEquals(112_500L, openingSkipTarget(interval, 22_500, 1_400_000, true))
        assertNull(openingSkipTarget(interval, 112_500, 1_400_000, true))
    }
}
