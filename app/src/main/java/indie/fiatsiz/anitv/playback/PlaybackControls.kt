package indie.fiatsiz.anitv.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R

@Composable
internal fun PlaybackControls(
    visible: Boolean,
    active: Boolean,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    seekable: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    videoFocus: FocusRequester,
    controlFocus: FocusRequester,
    episodesFocus: FocusRequester,
    settingsFocus: FocusRequester,
    onVisible: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onEpisodes: () -> Unit,
    onSettings: () -> Unit,
    openingAvailable: Boolean = false,
    onSkipOpening: () -> Unit = {},
    onOpeningFocusChanged: (Boolean) -> Unit = {}
) {
    val sliderFocus = remember { FocusRequester() }
    val openingFocus = remember { FocusRequester() }
    var sliderFocused by remember { mutableStateOf(false) }
    var openingFocused by remember { mutableStateOf(false) }
    val openingVisible = active && openingAvailable
    val currentOpeningVisible by rememberUpdatedState(openingVisible)
    val colors = MaterialTheme.colorScheme
    LaunchedEffect(openingVisible) {
        if (!openingVisible && openingFocused) {
            openingFocused = false
            onOpeningFocusChanged(false)
            if (visible) sliderFocus.requestFocus() else videoFocus.requestFocus()
        }
    }
    Box(
        Modifier.fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !active) return@onPreviewKeyEvent false
                onInteraction()
                when {
                    !visible && !openingFocused && event.key in listOf(Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter) -> {
                        onVisible(true)
                        true
                    }
                    !visible && openingVisible && !openingFocused && event.key == Key.DirectionUp -> {
                        openingFocus.requestFocus()
                        true
                    }
                    visible && event.key == Key.DirectionUp -> {
                        when {
                            openingFocused -> onVisible(false)
                            sliderFocused && openingVisible -> openingFocus.requestFocus()
                            sliderFocused -> onVisible(false)
                            else -> sliderFocus.requestFocus()
                        }
                        true
                    }
                    openingFocused && event.key == Key.DirectionDown -> {
                        if (visible) sliderFocus.requestFocus() else onVisible(true)
                        true
                    }
                    visible && event.key == Key.DirectionDown -> {
                        if (sliderFocused) controlFocus.requestFocus()
                        true
                    }
                    visible && sliderFocused && event.key in listOf(Key.DirectionLeft, Key.DirectionRight) -> {
                        if (seekable && durationMs > 0) onSeek(if (event.key == Key.DirectionRight) 10_000 else -10_000)
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(videoFocus)
            .focusable()
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(if (visible) colors.surface.copy(alpha = 0.96f) else Color.Transparent)
                .padding(horizontal = 32.dp, vertical = 16.dp)
        ) {
            if (openingVisible) Button(
                onClick = { onInteraction(); onSkipOpening() },
                modifier = Modifier.align(Alignment.End).padding(bottom = 8.dp).heightIn(min = 48.dp)
                    .focusRequester(openingFocus)
                    .onFocusChanged {
                        // Retain outgoing focus until the removal effect can restore the slider/video.
                        if (it.isFocused || currentOpeningVisible) openingFocused = it.isFocused
                        onOpeningFocusChanged(it.isFocused)
                    },
                scale = ButtonDefaults.scale(focusedScale = 1f)
            ) { Text(stringResource(R.string.skip_opening)) }
            if (visible) {
                val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                val progressLabel = stringResource(R.string.playback_progress)
                Canvas(
                    Modifier.fillMaxWidth().height(48.dp)
                        .border(2.dp, if (sliderFocused) colors.onSurface else Color.Transparent, RoundedCornerShape(8.dp))
                        .focusRequester(sliderFocus)
                        .onFocusChanged { sliderFocused = it.isFocused }
                        .semantics {
                            contentDescription = progressLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                            if (seekable && durationMs > 0) setProgress { value ->
                                if (!value.isFinite()) false else {
                                    onInteraction()
                                    onSeekTo((value.coerceIn(0f, 1f) * durationMs).toLong())
                                    true
                                }
                            } else disabled()
                        }
                        .focusable()
                ) {
                    val inset = 12.dp.toPx()
                    val start = Offset(inset, size.height / 2)
                    val end = Offset(size.width - inset, size.height / 2)
                    val thumb = Offset(start.x + (end.x - start.x) * progress, start.y)
                    drawLine(colors.onSurface.copy(alpha = 0.35f), start, end, 4.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.onSurface, start, thumb, 4.dp.toPx(), StrokeCap.Round)
                    drawCircle(colors.onSurface, if (sliderFocused) 8.dp.toPx() else 5.dp.toPx(), thumb)
                }
                Text("${playbackTime(positionMs)} / ${playbackTime(durationMs)}", color = colors.onSurface)
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Button(
                        onClick = onEpisodes,
                        modifier = Modifier.align(Alignment.CenterStart).focusRequester(episodesFocus),
                        scale = ButtonDefaults.scale(focusedScale = 1f)
                    ) { Text(stringResource(R.string.episodes)) }
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPrevious, enabled = previousEnabled,
                            modifier = Modifier.size(56.dp).focusProperties { canFocus = previousEnabled },
                            contentPadding = PaddingValues(12.dp),
                            scale = ButtonDefaults.scale(focusedScale = 1f)
                        ) {
                            Icon(Icons.Default.SkipPrevious, stringResource(R.string.previous_episode), Modifier.size(32.dp))
                        }
                        Button(
                            onClick = onPlayPause,
                            modifier = Modifier.size(64.dp).focusRequester(controlFocus),
                            contentPadding = PaddingValues(12.dp),
                            scale = ButtonDefaults.scale(focusedScale = 1f)
                        ) {
                            if (playing) Icon(Icons.Default.Pause, stringResource(R.string.pause), Modifier.size(40.dp))
                            else Icon(Icons.Default.PlayArrow, stringResource(R.string.play), Modifier.size(40.dp))
                        }
                        Button(
                            onClick = onNext, enabled = nextEnabled,
                            modifier = Modifier.size(56.dp).focusProperties { canFocus = nextEnabled },
                            contentPadding = PaddingValues(12.dp),
                            scale = ButtonDefaults.scale(focusedScale = 1f)
                        ) {
                            Icon(Icons.Default.SkipNext, stringResource(R.string.next_episode), Modifier.size(32.dp))
                        }
                    }
                    Button(
                        onClick = onSettings,
                        modifier = Modifier.align(Alignment.CenterEnd).size(56.dp).focusRequester(settingsFocus),
                        contentPadding = PaddingValues(12.dp),
                        scale = ButtonDefaults.scale(focusedScale = 1f)
                    ) { Icon(Icons.Default.Settings, stringResource(R.string.settings), Modifier.size(32.dp)) }
                }
            }
        }
    }
}

internal fun playbackTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal fun seekPosition(positionMs: Long, offsetMs: Long, durationMs: Long): Long =
    (positionMs + offsetMs).coerceIn(0, durationMs.coerceAtLeast(0))
