package indie.fiatsiz.anitv.playback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.HiAnimeSource
import indie.fiatsiz.anitv.source.Stream
import indie.fiatsiz.anitv.source.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import indie.fiatsiz.anitv.TranslationSettings
import indie.fiatsiz.anitv.TranslationProvider
import indie.fiatsiz.anitv.SubtitleTranslator
import indie.fiatsiz.anitv.subtitleBatchEnd
import indie.fiatsiz.anitv.shouldPrefetchNextBatch
import indie.fiatsiz.anitv.R

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private data class QualityOption(val group: TrackGroup, val index: Int, val label: String)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun Playback(lifecycle: Lifecycle, stream: Stream, savedPositionMs: Long, translation: TranslationSettings, targetLanguage: String, userAgent: String,
    animeTitle: String, currentEpisode: Episode, episodes: List<Episode>, episodesLoading: Boolean, episodesError: String?,
    onReloadEpisodes: () -> Unit, onSelectEpisode: (Episode) -> Unit, onTranslationSettings: (TranslationSettings) -> Unit,
    onProgress: (Long, Long) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val rules = remember { indie.fiatsiz.anitv.source.ProcessRules.get(context) }
    val source = remember(userAgent, rules) { HiAnimeSource(userAgent, rules::snapshot) }
    val translator = remember { SubtitleTranslator() }
    val hasCaptions = stream.subtitleUrl != null
    val translationReady = hasCaptions && translation.provider != TranslationProvider.OFF && translation.apiKey.isNotBlank()
    val captionStyle = translation.captionStyle
    val effectiveTarget = translation.targetLanguage.ifBlank { targetLanguage }
    var captionMode by remember(stream) { mutableStateOf(if (!hasCaptions) CaptionMode.OFF else if (translationReady) CaptionMode.TRANSLATED else CaptionMode.ORIGINAL) }
    val translationOn = captionMode == CaptionMode.TRANSLATED && translationReady
    var buffering by remember(stream) { mutableStateOf(true) }
    var playing by remember(stream) { mutableStateOf(true) }
    var captionMenu by remember(stream) { mutableStateOf(CaptionMenu.CLOSED) }
    var positionMs by remember(stream) { mutableStateOf(savedPositionMs) }
    var durationMs by remember(stream) { mutableStateOf(0L) }
    var controlsVisible by remember(stream) { mutableStateOf(true) }
    var qualityOpen by remember(stream) { mutableStateOf(false) }
    var episodesOpen by remember(stream) { mutableStateOf(false) }
    var restoreEpisodesFocus by remember(stream) { mutableStateOf(false) }
    var qualityOptions by remember(stream) { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember(stream) { mutableStateOf(resources.getString(R.string.auto)) }
    var interactionTick by remember(stream) { mutableStateOf(0) }
    var captionCues by remember(stream, translationOn, effectiveTarget) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var translatedTexts by remember(stream, translationOn, effectiveTarget) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var translationFailed by remember(stream, translationOn, effectiveTarget) { mutableStateOf(false) }
    val episodesButtonFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val controlFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val videoFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val qualityFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val captionFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val languageFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val styleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val choiceFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val progressCallback by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val errorCallback by androidx.compose.runtime.rememberUpdatedState(onError)
    var currentPlayer by remember(lifecycle, stream) { mutableStateOf<Player?>(null) }
    val createPlayer by androidx.compose.runtime.rememberUpdatedState(newValue = { resumePositionMs: Long ->
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to stream.referer))
        ExoPlayer.Builder(context).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().apply {
            val builder = MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8)
            stream.subtitleUrl?.let { subtitle ->
                builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle))
                    .setMimeType(MimeTypes.TEXT_VTT).setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            }
            setMediaItem(builder.build())
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, captionMode == CaptionMode.OFF || translationOn).build()
            if (resumePositionMs > 0) seekTo(resumePositionMs)
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
                override fun onTracksChanged(tracks: Tracks) {
                    qualityOptions = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
                        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                            val format = group.getTrackFormat(index)
                            val name = if (format.height > 0) "${format.height}p" else resources.getString(R.string.video_track, index + 1)
                            val bitrate = if (format.bitrate > 0) " · ${format.bitrate / 1000} kb/s" else ""
                            QualityOption(group.mediaTrackGroup, index, name + bitrate)
                        }
                    }.sortedByDescending { it.group.getFormat(it.index).height }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    buffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_ENDED) progressCallback(playerPosition(), duration.coerceAtLeast(0))
                }
                private fun playerPosition() = currentPosition.coerceAtLeast(0)
                override fun onPlayerError(error: PlaybackException) {
                    errorCallback(resources.getString(R.string.playback_failed))
                }
            })
            prepare()
        }
    })
    DisposableEffect(lifecycle, stream) {
        val session = ForegroundPlayer(lifecycle, savedPositionMs, { createPlayer(it) },
            { position, duration -> progressCallback(position, duration) }, { currentPlayer = it })
        onDispose { session.dispose() }
    }
    val player = currentPlayer ?: return
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            if (positionMs > 0) progressCallback(positionMs, durationMs)
        }
    }
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            delay(250)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
        }
    }
    androidx.compose.runtime.LaunchedEffect(player, stream, translationOn, translation.provider, translation.apiKey, effectiveTarget) {
        if (!translationOn) return@LaunchedEffect
        try {
            val cues = withContext(Dispatchers.IO) { source.subtitleCues(stream) }
            if (cues.isEmpty()) throw IllegalStateException("No subtitle cues")
            captionCues = cues
            val firstCue = cues.indexOfFirst { it.endMs >= player.currentPosition }
            var start = if (firstCue < 0) cues.size else firstCue
            while (start < cues.size) {
                val end = subtitleBatchEnd(start, cues.size)
                val translated = withContext(Dispatchers.IO) {
                    translator.translate(translation.provider, translation.apiKey, effectiveTarget, cues.subList(start, end).map { it.text })
                }
                translatedTexts = translatedTexts + (start until end).associateWith { translated[it - start] }
                val translatedThrough = end - 1
                fun currentCue() = cues.indexOfFirst { it.endMs > player.currentPosition }.let { if (it < 0) cues.size else it }
                while (!shouldPrefetchNextBatch(currentCue(), translatedThrough)) {
                    delay(250)
                }
                val current = currentCue()
                start = maxOf(current, end)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            translationFailed = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(player, captionMode, translationOn, translationFailed) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, captionMode == CaptionMode.OFF || (translationOn && !translationFailed)).build()
    }
    BackHandler(captionMenu != CaptionMenu.CLOSED || qualityOpen) {
        when {
            captionMenu == CaptionMenu.LANGUAGES -> captionMenu = CaptionMenu.OPTIONS
            captionMenu in listOf(CaptionMenu.FONT, CaptionMenu.SIZE, CaptionMenu.BACKGROUND, CaptionMenu.FOREGROUND) -> captionMenu = CaptionMenu.STYLES
            captionMenu == CaptionMenu.STYLES -> captionMenu = CaptionMenu.OPTIONS
            captionMenu == CaptionMenu.OPTIONS -> captionMenu = CaptionMenu.CLOSED
            else -> qualityOpen = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(controlsVisible, qualityOpen, captionMenu, episodesOpen) {
        when {
            episodesOpen -> Unit
            !controlsVisible -> videoFocus.requestFocus()
            captionMenu == CaptionMenu.LANGUAGES -> languageFocus.requestFocus()
            captionMenu == CaptionMenu.STYLES -> styleFocus.requestFocus()
            captionMenu in listOf(CaptionMenu.FONT, CaptionMenu.SIZE, CaptionMenu.BACKGROUND, CaptionMenu.FOREGROUND) -> choiceFocus.requestFocus()
            captionMenu == CaptionMenu.OPTIONS -> captionFocus.requestFocus()
            qualityOpen -> qualityFocus.requestFocus()
            restoreEpisodesFocus -> {
                restoreEpisodesFocus = false
                androidx.compose.runtime.withFrameNanos { }
                episodesButtonFocus.requestFocus()
            }
            else -> controlFocus.requestFocus()
        }
    }
    androidx.compose.runtime.LaunchedEffect(controlsVisible, qualityOpen, captionMenu, episodesOpen, playing, interactionTick) {
        if (controlsVisible && playing && !episodesOpen && !qualityOpen && captionMenu == CaptionMenu.CLOSED) {
            delay(6_000)
            controlsVisible = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                controlsVisible && !episodesOpen && !qualityOpen && captionMenu == CaptionMenu.CLOSED && event.key == Key.DirectionDown -> {
                    controlsVisible = false
                    true
                }
                !controlsVisible && (event.key == Key.DirectionUp || event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                    controlsVisible = true
                    true
                }
                else -> { interactionTick++; false }
            }
        }
        .focusRequester(videoFocus).focusable()) {
        AndroidView(
            factory = { PlayerView(it).apply {
                this.player = player
                useController = false
                isFocusable = false
            } },
            update = { view -> view.player = player; view.subtitleView?.let { styleCaptionView(it, captionStyle) } },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )
        val activeCue = captionCues.withIndex().firstOrNull { positionMs in it.value.startMs until it.value.endMs }
        if (translationOn && !translationFailed) {
            val text = activeCue?.let { translatedTexts[it.index] ?: it.value.text }
            val cues = remember(text) { text?.let { listOf(Cue.Builder().setText(it).build()) } ?: emptyList() }
            AndroidView(factory = { SubtitleView(it).apply { isFocusable = false } },
                update = { view -> styleCaptionView(view, captionStyle); view.setCues(cues) },
                modifier = Modifier.fillMaxSize())
        }
        if (translationOn && translationFailed) {
            Text(stringResource(R.string.translation_unavailable),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(12.dp), color = Color.White)
        }
        if (controlsVisible) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xDD0F0F23)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    qualityOpen = false
                    captionMenu = CaptionMenu.CLOSED
                    episodesOpen = true
                }, modifier = Modifier.focusRequester(episodesButtonFocus)) { Text(stringResource(R.string.episodes)) }
                Button(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Text(stringResource(R.string.seek_back)) }
                Button(onClick = { if (player.playWhenReady) player.pause() else player.play() }, modifier = Modifier.focusRequester(controlFocus)) {
                    Text(stringResource(if (playing) R.string.pause else R.string.play))
                }
                Button(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0))) }) { Text(stringResource(R.string.seek_forward)) }
                Button(onClick = {
                    qualityOpen = false
                    captionMenu = if (captionMenu == CaptionMenu.CLOSED) CaptionMenu.OPTIONS else CaptionMenu.CLOSED
                }, enabled = hasCaptions) { Text(stringResource(if (hasCaptions) R.string.captions_settings else R.string.captions_unavailable)) }
                if (qualityOptions.size > 1) Button(onClick = {
                    captionMenu = CaptionMenu.CLOSED
                    qualityOpen = !qualityOpen
                }) { Text(stringResource(R.string.quality, selectedQuality)) }
                else Text(stringResource(R.string.auto_quality), color = Color.White)
                Text("${positionMs / 60_000}:${(positionMs / 1_000 % 60).toString().padStart(2, '0')} / " +
                    "${durationMs / 60_000}:${(durationMs / 1_000 % 60).toString().padStart(2, '0')}", color = Color.White)
            }
            if (qualityOpen) Column(Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 16.dp)
                .background(Color(0xEE0F0F23)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    selectedQuality = resources.getString(R.string.auto)
                    qualityOpen = false
                }, modifier = Modifier.focusRequester(qualityFocus)) { Text(stringResource(R.string.auto)) }
                qualityOptions.forEach { option ->
                    Button(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(option.group, option.index)).build()
                        selectedQuality = option.label.substringBefore(" ·")
                        qualityOpen = false
                    }) { Text(option.label) }
                }
            }
            if (captionMenu != CaptionMenu.CLOSED) CaptionSettings(captionMenu, captionMode, translation, effectiveTarget,
                captionFocus, languageFocus, styleFocus, choiceFocus,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 16.dp),
                onMenu = { captionMenu = it }, onMode = { captionMode = it }, onTranslationSettings = onTranslationSettings)
        }
        if (buffering) Text(stringResource(R.string.buffering), modifier = Modifier.align(Alignment.Center), color = Color.White)
        if (episodesOpen) EpisodeSidebar(animeTitle, currentEpisode, episodes, episodesLoading, episodesError,
            onDismiss = { episodesOpen = false; restoreEpisodesFocus = true; interactionTick++ },
            onRetry = onReloadEpisodes, onSelect = { selected ->
                if (selected.id == currentEpisode.id) {
                    episodesOpen = false
                    restoreEpisodesFocus = true
                    interactionTick++
                } else {
                    if (player.currentPosition > 0) progressCallback(player.currentPosition, player.duration.coerceAtLeast(0))
                    onSelectEpisode(selected)
                }
            })
    }
}
