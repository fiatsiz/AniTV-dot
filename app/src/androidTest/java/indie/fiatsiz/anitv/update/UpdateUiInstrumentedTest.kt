package indie.fiatsiz.anitv.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Surface
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.ui.HomeContent
import indie.fiatsiz.anitv.ui.UpdateContent
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UpdateUiInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun arrivingHomeNoticeKeepsSearchFocusAndOpensSettings() {
        val version = mutableStateOf<String?>(null)
        var settingsOpened = false
        compose.setContent {
            val focus = remember { FocusRequester() }
            AniTVTheme {
                Surface(Modifier.fillMaxSize()) {
                    HomeContent(
                        emptyList(), focus, Modifier.padding(56.dp), version.value,
                        onNavigate = {
                            settingsOpened = it == indie.fiatsiz.anitv.ui.Page.SETTINGS
                        }) {}
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
        val search = compose.onNode(
            hasText(
                context.getString(R.string.search),
                substring = true
            ) and hasClickAction()
        )
        search.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        search.assertIsFocused()
        compose.runOnIdle { version.value = "1.2.0" }
        search.assertIsFocused()
        val notice = compose.onNodeWithText(context.getString(R.string.update_notice, "1.2.0"))
        notice.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        notice.assertIsFocused()
            .performKeyInput { pressKey(androidx.compose.ui.input.key.Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun downloadActionKeepsFocusAcrossProgressAndReadyStates() {
        val release = Release(StableVersion.parse("1.2.0")!!, "Notes", "", 100, "0".repeat(64))
        val state = mutableStateOf(UpdateState("1.1.0", release, CheckStatus.AVAILABLE))
        compose.setContent {
            AniTVTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .padding(56.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        UpdateContent(state.value, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.update_download)).performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        compose.onNodeWithText(context.getString(R.string.update_download)).assertIsFocused()
        compose.runOnIdle {
            state.value = state.value.copy(download = DownloadStatus.DOWNLOADING, bytes = 50)
        }
        compose.onNodeWithText(context.getString(R.string.update_cancel)).assertIsFocused()
        compose.runOnIdle { state.value = state.value.copy(download = DownloadStatus.READY) }
        compose.onNodeWithText(context.getString(R.string.update_install)).assertIsFocused()
    }
}
