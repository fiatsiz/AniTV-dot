package indie.fiatsiz.anitv.playback

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.source.OpeningInterval
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackControlsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var position = 20_000L
    private var previousCount = 0
    private var nextCount = 0
    private var episodesCount = 0
    private var settingsCount = 0
    private var openingCount = 0

    private fun content(previousEnabled: Boolean = true, nextEnabled: Boolean = true, seekable: Boolean = true,
                        opening: OpeningInterval? = null) {
        compose.setContent {
            val video = remember { FocusRequester() }
            val play = remember { FocusRequester() }
            val episodes = remember { FocusRequester() }
            val settings = remember { FocusRequester() }
            var visible by remember { mutableStateOf(true) }
            var playing by remember { mutableStateOf(true) }
            var progress by remember { mutableStateOf(position) }
            AniTVTheme {
                PlaybackControls(
                    visible, true, playing, progress, 60_000, seekable, previousEnabled, nextEnabled,
                    video, play, episodes, settings, { visible = it }, {}, { playing = !playing },
                    { position = seekPosition(position, it, 60_000); progress = position },
                    { position = it; progress = position },
                    { previousCount++ }, { nextCount++ }, { episodesCount++ }, { settingsCount++ },
                    openingAvailable = openingSkipTarget(opening, progress, 60_000, seekable) != null,
                    onSkipOpening = {
                        openingSkipTarget(opening, progress, 60_000, seekable)?.let {
                            position = it; progress = it; openingCount++
                        }
                    }
                )
            }
            LaunchedEffect(visible) { if (visible) play.requestFocus() else video.requestFocus() }
        }
    }

    private fun control(label: Int) = compose.onNodeWithContentDescription(context.getString(label))
    private fun key(key: Key) = compose.onRoot().performKeyInput { pressKey(key) }
    private fun openingButton() = compose.onNodeWithText(context.getString(R.string.skip_opening))

    @Test
    fun openingButtonNeedsSelectAndReturnsFocusAfterSkipping() {
        content(opening = OpeningInterval(10_000, 40_000))
        openingButton().assertIsDisplayed().assertIsNotFocused()
        control(R.string.pause).assertIsFocused()
        compose.runOnIdle { assertEquals(20_000L, position); assertEquals(0, openingCount) }
        key(Key.DirectionCenter)
        control(R.string.play).assertIsFocused()
        openingButton().assertIsDisplayed()
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        openingButton().assertIsFocused()
        val button = openingButton().fetchSemanticsNode().boundsInRoot
        val slider = control(R.string.playback_progress).fetchSemanticsNode().boundsInRoot
        assertTrue(button.bottom <= slider.top)
        assertEquals(slider.right, button.right, 1f)
        key(Key.DirectionCenter)
        compose.runOnIdle { assertEquals(40_000L, position); assertEquals(1, openingCount) }
        openingButton().assertDoesNotExist()
        control(R.string.playback_progress).assertIsFocused()
        key(Key.DirectionLeft)
        openingButton().assertIsDisplayed()
        compose.runOnIdle { assertEquals(30_000L, position); assertEquals(1, openingCount) }
    }

    @Test
    fun openingExpiryRestoresSliderFocusWithoutSeeking() {
        content(opening = OpeningInterval(10_000, 40_000))
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        openingButton().assertIsFocused()
        control(R.string.playback_progress).performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        openingButton().assertDoesNotExist()
        control(R.string.playback_progress).assertIsFocused()
        compose.runOnIdle { assertEquals(54_000L, position); assertEquals(0, openingCount) }
    }

    @Test
    fun hiddenControllerKeepsOpeningPromptAndSupportsDpadSelect() {
        content(opening = OpeningInterval(10_000, 40_000))
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        control(R.string.playback_progress).assertDoesNotExist()
        openingButton().assertIsDisplayed()
        key(Key.DirectionUp)
        openingButton().assertIsFocused()
        key(Key.DirectionCenter)
        openingButton().assertDoesNotExist()
        compose.runOnIdle { assertEquals(40_000L, position); assertEquals(1, openingCount) }
        key(Key.DirectionDown)
        control(R.string.pause).assertIsFocused()
    }

    @Test
    fun upFocusesSliderThenHidesAndDownReopens() {
        content()
        control(R.string.pause).assertIsFocused()
        key(Key.DirectionUp)
        control(R.string.playback_progress).assertIsFocused()
        key(Key.DirectionRight)
        compose.runOnIdle { assertEquals(30_000L, position) }
        key(Key.DirectionLeft)
        compose.runOnIdle { assertEquals(20_000L, position) }
        key(Key.DirectionDown)
        control(R.string.pause).assertIsFocused()
        key(Key.DirectionCenter)
        control(R.string.play).assertIsFocused()
        key(Key.DirectionUp)
        key(Key.DirectionUp)
        control(R.string.playback_progress).assertDoesNotExist()
        control(R.string.play).assertDoesNotExist()
        key(Key.DirectionDown)
        control(R.string.play).assertIsFocused()
    }

    @Test
    fun horizontalNavigationReachesEpisodeAndSettingsActions() {
        content()
        key(Key.DirectionLeft)
        control(R.string.previous_episode).assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionLeft)
        compose.onNodeWithText(context.getString(R.string.episodes)).assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        key(Key.DirectionRight)
        control(R.string.next_episode).assertIsFocused()
        key(Key.DirectionCenter)
        key(Key.DirectionRight)
        control(R.string.settings).assertIsFocused()
        key(Key.DirectionCenter)
        compose.runOnIdle {
            assertEquals(1, previousCount)
            assertEquals(1, nextCount)
            assertEquals(1, episodesCount)
            assertEquals(1, settingsCount)
        }
    }

    @Test
    fun boundariesDisableEpisodeSkipAndUnknownMediaDoesNotSeek() {
        content(previousEnabled = false, nextEnabled = false, seekable = false, opening = OpeningInterval(10_000, 40_000))
        openingButton().assertDoesNotExist()
        control(R.string.previous_episode).assertIsNotEnabled()
        control(R.string.next_episode).assertIsNotEnabled()
        key(Key.DirectionUp)
        control(R.string.playback_progress).assertIsFocused().assertIsNotEnabled()
        key(Key.DirectionRight)
        key(Key.DirectionLeft)
        compose.runOnIdle { assertEquals(20_000L, position) }
        key(Key.DirectionDown)
        key(Key.DirectionLeft)
        compose.onNodeWithText(context.getString(R.string.episodes)).assertIsFocused()
    }

    @Test
    fun accessibleSliderActionSeeksAndClamps() {
        content()
        control(R.string.playback_progress).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.runOnIdle { assertEquals(30_000L, position) }
        control(R.string.playback_progress).performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        compose.runOnIdle { assertEquals(60_000L, position) }
    }

    @Test
    fun layoutCentersPlayAndKeepsSliderAboveSeparateButtons() {
        content()
        val play = control(R.string.pause).fetchSemanticsNode().boundsInRoot
        val previous = control(R.string.previous_episode).fetchSemanticsNode().boundsInRoot
        val next = control(R.string.next_episode).fetchSemanticsNode().boundsInRoot
        val episodes = compose.onNodeWithText(context.getString(R.string.episodes)).fetchSemanticsNode().boundsInRoot
        val settings = control(R.string.settings).fetchSemanticsNode().boundsInRoot
        val slider = control(R.string.playback_progress).fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(screen.center.x, play.center.x, 1f)
        assertTrue(episodes.right < previous.left && previous.right < play.left)
        assertTrue(play.right < next.left && next.right < settings.left)
        assertTrue(slider.bottom < play.top && settings.right <= screen.right)
    }
}
