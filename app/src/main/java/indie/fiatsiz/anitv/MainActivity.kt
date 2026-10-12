package indie.fiatsiz.anitv

import android.os.Bundle
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
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
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.HiAnimeSource
import indie.fiatsiz.anitv.source.Stream
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AniTVTheme {
                Surface(Modifier.fillMaxSize()) { AniTV() }
            }
        }
    }
}

private enum class Page { HOME, SEARCH, FAVORITES, WATCHING, COMPLETED }

@Composable
private fun AniTV() {
    val context = LocalContext.current
    val source = remember { HiAnimeSource() }
    val store = remember { LibraryStore(context) }
    val detailCache = remember { AnimeDetailCache(context) }
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(store.read()) }
    var page by remember { mutableStateOf(Page.HOME) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var anime by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var details by remember { mutableStateOf<AnimeDetails?>(null) }
    var poster by remember { mutableStateOf<ImageBitmap?>(null) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var episode by remember { mutableStateOf<Episode?>(null) }
    var stream by remember { mutableStateOf<Stream?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(0) }
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }

    fun save(change: (List<LibraryEntry>) -> List<LibraryEntry>) {
        library = change(library)
        store.save(library)
    }

    fun search() {
        if (query.isBlank() || loading) return
        loading = true
        error = null
        searched = true
        results = emptyList()
        val term = query
        val current = ++request
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { source.search(term) }
                if (current == request) results = found
            } catch (e: Exception) { if (current == request) error = e.message ?: "Search failed. Try again." }
            finally { if (current == request) loading = false }
        }
    }

    fun selectAnime(selected: Anime) {
        anime = selected
        episodes = emptyList()
        details = null
        poster = null
        detailError = null
        error = null
        loading = true
        val current = ++request
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { source.episodes(selected) }
                if (current == request) episodes = found
            } catch (e: Exception) { if (current == request) error = e.message ?: "Could not load episodes." }
            finally { if (current == request) loading = false }
        }
        scope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { detailCache.read(selected) }
                if (current != request) return@launch
                if (cached != null) {
                    details = cached.details
                    poster = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeByteArray(cached.poster, 0, cached.poster.size)?.asImageBitmap()
                    }
                }
                if (cached?.isFresh(System.currentTimeMillis()) == true && poster != null) return@launch
                val fresh = withContext(Dispatchers.IO) { source.details(selected) }
                if (current != request) return@launch
                details = fresh
                val bytes = withContext(Dispatchers.IO) { source.poster(fresh.posterUrl) }
                val decoded = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    ?: throw IllegalStateException("HiAnime returned an unreadable poster.")
                withContext(Dispatchers.IO) { detailCache.save(selected, fresh, bytes, System.currentTimeMillis()) }
                if (current == request) poster = decoded
            } catch (e: Exception) {
                if (current == request) detailError = e.message ?: "Anime details are unavailable. Episodes are still accessible."
            }
        }
    }

    fun selectEpisode(selected: Episode) {
        episode = selected
        stream = null
        error = null
        loading = true
        val current = ++request
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { source.stream(selected) }
                if (current == request) stream = found
            } catch (e: Exception) { if (current == request) error = e.message ?: "Could not load the stream." }
            finally { if (current == request) loading = false }
        }
    }

    fun back() {
        request++
        error = null
        loading = false
        when {
            episode != null -> { episode = null; stream = null }
            anime != null -> { anime = null; episodes = emptyList() }
            page != Page.HOME -> page = Page.HOME
        }
    }

    BackHandler(episode != null || anime != null || page != Page.HOME) { back() }

    if (episode != null && stream != null) {
        val selectedAnime = anime!!
        val selectedEpisode = episode!!
        val saved = library.firstOrNull { it.anime.id == selectedAnime.id }?.progress
            ?.takeIf { it.episodeId == selectedEpisode.id }?.positionMs ?: 0L
        Playback(stream!!, saved, onBack = ::back, onProgress = { position, duration ->
            save { entries -> LibraryStore.update(entries, selectedAnime) {
                it.copy(progress = WatchProgress(selectedEpisode.id, selectedEpisode.number, position, duration))
            } }
        }, onError = { error = it; stream = null })
        return
    }

    val screen = when {
        episode != null -> "episode"
        anime != null -> "detail"
        else -> page.name
    }
    androidx.compose.runtime.LaunchedEffect(screen) { firstFocus.requestFocus() }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F0F23)).padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("AniTV", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF8FAFC))
            Text(".", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC0DE))
            Text("ANIME PLAYER FOR AndroidTV", fontSize = 14.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp)
        }
        when {
            episode != null -> {
                Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text("Back to episodes") }
                Text("${anime?.title} · ${episode!!.title}", fontSize = 26.sp)
                if (loading) Text("Loading subtitled stream…")
                error?.let { Text(it, color = Color(0xFFFF9999)) }
                if (error != null) Button(onClick = { selectEpisode(episode!!) }) { Text("Retry playback") }
            }
            anime != null -> {
                val selected = anime!!
                val entry = library.firstOrNull { it.anime.id == selected.id }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text("Back") }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(180.dp).height(280.dp).background(Color(0xFF27273B))) {
                                poster?.let { Image(it, "${selected.title} poster", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                if (poster == null) Text(if (detailError == null) "Artwork loading…" else "Artwork unavailable",
                                    modifier = Modifier.align(Alignment.Center).padding(12.dp), color = Color.White)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(selected.title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                                details?.let { info ->
                                    Text(info.description, maxLines = 7, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                                    info.facts.forEach { fact -> Text(fact, color = Color(0xFFB9C5D8), fontSize = 15.sp) }
                                }
                                detailError?.let { Text(it, color = Color(0xFFFF9999)) }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { save { LibraryStore.update(it, selected) { item -> item.copy(favorite = !item.favorite) } } }) {
                                Text(if (entry?.favorite == true) "Remove favorite" else "Add favorite")
                            }
                            Button(onClick = { save { LibraryStore.update(it, selected) { item -> item.copy(completed = !item.completed) } } }) {
                                Text(if (entry?.completed == true) "Move to watching" else "Mark title completed")
                            }
                        }
                    }
                    if (loading) item { Text("Loading episodes…") }
                    error?.let { message -> item { Text(message, color = Color(0xFFFF9999)) } }
                    if (!loading && episodes.isNotEmpty()) {
                        item { Text("EPISODES", fontSize = 15.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp) }
                        items(episodes, key = { it.id }) { episodeItem ->
                            Button(onClick = { selectEpisode(episodeItem) }, modifier = Modifier.fillMaxWidth()) {
                                val resume = entry?.progress?.takeIf { it.episodeId == episodeItem.id && it.positionMs > 0 }
                                Text("${episodeItem.number}. ${episodeItem.title}" + (if (resume != null) "  ·  Resume ${resume.positionMs / 60_000}m" else ""))
                            }
                        }
                    }
                    if (episodes.isEmpty() && !loading) item {
                        Button(onClick = { selectAnime(selected) }) { Text("Retry episodes") }
                    }
                }
            }
            page == Page.HOME -> {
                Text("Find your next episode.", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("Search HiAnime, pick up where you left off, or browse your library.", color = Color(0xFFB9C5D8), fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeButton("Search", "Find a title", Modifier.weight(1f).focusRequester(firstFocus)) { page = Page.SEARCH }
                    HomeButton("Currently watching", "${LibraryStore.watching(library).size} titles", Modifier.weight(1f)) { page = Page.WATCHING }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    HomeButton("Favorites", "${library.count { it.favorite }} saved", Modifier.weight(1f)) { page = Page.FAVORITES }
                    HomeButton("Completed", "${library.count { it.completed }} archived", Modifier.weight(1f)) { page = Page.COMPLETED }
                }
                Text("Select to open · Back returns here", color = Color(0xFFB9C5D8), fontSize = 15.sp)
            }
            page == Page.SEARCH -> {
                Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text("Home") }
                Text("Search anime", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                SearchField(query, { query = it }, ::search)
                Button(onClick = ::search, enabled = query.isNotBlank() && !loading) { Text("Search") }
                if (loading) Text("Searching HiAnime…")
                error?.let { Text(it, color = Color(0xFFFF9999)) }
                if (searched && !loading && error == null && results.isEmpty()) Text("No results found. Try another title.")
                if (results.isNotEmpty()) {
                    Text("RESULTS", fontSize = 15.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results, key = { it.id }) { item ->
                            Button(onClick = { selectAnime(item) }, modifier = Modifier.fillMaxWidth()) { Text(item.title) }
                        }
                    }
                }
            }
            else -> {
                Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text("Home") }
                val entries = when (page) {
                    Page.FAVORITES -> library.filter { it.favorite }
                    Page.WATCHING -> LibraryStore.watching(library)
                    else -> library.filter { it.completed }
                }
                Text(when (page) { Page.FAVORITES -> "Favorites"; Page.WATCHING -> "Currently watching"; else -> "Completed anime" },
                    fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                if (entries.isEmpty()) Text("Nothing here yet. Search for anime to build your library.", color = Color(0xFFB9C5D8))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries, key = { it.anime.id }) { item ->
                        Button(onClick = { selectAnime(item.anime) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.anime.title + (item.progress?.let { "  ·  Episode ${it.episodeNumber}" } ?: ""))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeButton(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(112.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 15.sp)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        Modifier.fillMaxWidth().height(54.dp)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color.Gray, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                        keyboard?.hide()
                        focusManager.moveFocus(FocusDirection.Down)
                        true
                    } else false
                }
                .onFocusChanged { focused = it.isFocused }
        )
        if (value.isEmpty() && !focused) Text("Type a title with your remote", color = Color.LightGray)
    }
}

private data class QualityOption(val group: TrackGroup, val index: Int, val label: String)

@Composable
private fun Playback(stream: Stream, savedPositionMs: Long, onBack: () -> Unit, onProgress: (Long, Long) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    var buffering by remember(stream) { mutableStateOf(true) }
    var playing by remember(stream) { mutableStateOf(true) }
    var captions by remember(stream) { mutableStateOf(true) }
    var positionMs by remember(stream) { mutableStateOf(savedPositionMs) }
    var durationMs by remember(stream) { mutableStateOf(0L) }
    var controlsVisible by remember(stream) { mutableStateOf(true) }
    var qualityOpen by remember(stream) { mutableStateOf(false) }
    var qualityOptions by remember(stream) { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember(stream) { mutableStateOf("Auto") }
    var interactionTick by remember(stream) { mutableStateOf(0) }
    val controlFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val videoFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val qualityFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val progressCallback by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val errorCallback by androidx.compose.runtime.rememberUpdatedState(onError)
    val player = remember(stream) {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; TV)")
            .setDefaultRequestProperties(mapOf("Referer" to stream.referer))
        ExoPlayer.Builder(context).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().apply {
            val builder = MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8)
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(stream.subtitleUrl))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            setMediaItem(builder.build())
            if (savedPositionMs > 0) seekTo(savedPositionMs)
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
                override fun onTracksChanged(tracks: Tracks) {
                    qualityOptions = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
                        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                            val format = group.getTrackFormat(index)
                            val name = if (format.height > 0) "${format.height}p" else "Video ${index + 1}"
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
                    errorCallback("Playback failed. The stream may have expired. Return to episodes and retry.")
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            if (positionMs > 0) progressCallback(positionMs, durationMs)
        }
    }
    DisposableEffect(player) {
        onDispose {
            if (player.currentPosition > 0) progressCallback(player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
        }
    }
    androidx.compose.runtime.LaunchedEffect(controlsVisible, qualityOpen) {
        when {
            !controlsVisible -> videoFocus.requestFocus()
            qualityOpen -> qualityFocus.requestFocus()
            else -> controlFocus.requestFocus()
        }
    }
    androidx.compose.runtime.LaunchedEffect(controlsVisible, qualityOpen, playing, interactionTick) {
        if (controlsVisible && playing && !qualityOpen) {
            delay(6_000)
            controlsVisible = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                controlsVisible && !qualityOpen && event.key == Key.DirectionDown -> {
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
            modifier = Modifier.fillMaxSize()
        )
        if (controlsVisible) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xDD0F0F23)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Episodes") }
                Button(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Text("−10s") }
                Button(onClick = { if (player.playWhenReady) player.pause() else player.play() }, modifier = Modifier.focusRequester(controlFocus)) {
                    Text(if (playing) "Pause" else "Play")
                }
                Button(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0))) }) { Text("+10s") }
                Button(onClick = {
                    captions = !captions
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captions).build()
                }) { Text(if (captions) "Captions on" else "Captions off") }
                if (qualityOptions.size > 1) Button(onClick = { qualityOpen = !qualityOpen }) { Text("Quality: $selectedQuality") }
                else Text("Auto quality", color = Color.White)
                Text("${positionMs / 60_000}:${(positionMs / 1_000 % 60).toString().padStart(2, '0')} / " +
                    "${durationMs / 60_000}:${(durationMs / 1_000 % 60).toString().padStart(2, '0')}", color = Color.White)
            }
            if (qualityOpen) Column(Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 16.dp)
                .background(Color(0xEE0F0F23)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    selectedQuality = "Auto"
                    qualityOpen = false
                }, modifier = Modifier.focusRequester(qualityFocus)) { Text("Auto") }
                qualityOptions.forEach { option ->
                    Button(onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(option.group, option.index)).build()
                        selectedQuality = option.label.substringBefore(" ·")
                        qualityOpen = false
                    }) { Text(option.label) }
                }
            }
        }
        if (buffering) Text("Buffering…", modifier = Modifier.align(Alignment.Center), color = Color.White)
    }
}
