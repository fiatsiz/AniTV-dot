package indie.fiatsiz.anitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.HiAnimeSource
import indie.fiatsiz.anitv.source.Stream
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import kotlinx.coroutines.Dispatchers
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

@Composable
private fun AniTV() {
    val source = remember { HiAnimeSource() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var anime by remember { mutableStateOf<Anime?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var episode by remember { mutableStateOf<Episode?>(null) }
    var stream by remember { mutableStateOf<Stream?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(0) }

    fun search() {
        if (query.isBlank() || loading) return
        loading = true
        error = null
        searched = true
        results = emptyList()
        val current = ++request
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { source.search(query) }
                if (current == request) results = found
            } catch (e: Exception) { if (current == request) error = e.message ?: "Search failed. Try again." }
            finally { if (current == request) loading = false }
        }
    }

    fun selectAnime(selected: Anime) {
        anime = selected
        episodes = emptyList()
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

    BackHandler(episode != null || anime != null) {
        request++
        if (episode != null) { episode = null; stream = null; error = null; loading = false }
        else { anime = null; episodes = emptyList(); error = null; loading = false }
    }

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AniTV", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        when {
            episode != null -> {
                Text("${anime?.title} · ${episode!!.title}", fontSize = 22.sp)
                if (loading) Text("Loading subtitled stream…")
                error?.let { Text(it, color = Color(0xFFFF8080)) }
                if (stream != null) Playback(stream!!, onError = { error = it })
                Button(onClick = { request++; episode = null; stream = null; error = null; loading = false }) { Text("Back to episodes") }
            }
            anime != null -> {
                Text(anime!!.title, fontSize = 22.sp)
                if (loading) Text("Loading episodes…")
                error?.let { Text(it, color = Color(0xFFFF8080)) }
                if (!loading && episodes.isNotEmpty()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(episodes, key = { it.id }) { item ->
                            Button(onClick = { selectEpisode(item) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${item.number}. ${item.title}")
                            }
                        }
                    }
                }
                if (episodes.isEmpty() && !loading) Button(onClick = { selectAnime(anime!!) }) { Text("Retry episodes") }
            }
            else -> {
                Text("Search anime", fontSize = 22.sp)
                SearchField(query, { query = it }, ::search)
                Button(onClick = ::search, enabled = query.isNotBlank() && !loading) { Text("Search") }
                if (loading) Text("Searching HiAnime…")
                error?.let { Text(it, color = Color(0xFFFF8080)) }
                if (searched && !loading && error == null && results.isEmpty()) Text("No results found. Try another title.")
                if (results.isNotEmpty()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results, key = { it.id }) { item ->
                            Button(onClick = { selectAnime(item) }, modifier = Modifier.fillMaxWidth()) { Text(item.title) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
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
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
        )
        if (value.isEmpty() && !focused) Text("Type a title with your remote", color = Color.LightGray)
    }
}

@Composable
private fun Playback(stream: Stream, onError: (String) -> Unit) {
    val context = LocalContext.current
    var buffering by remember(stream) { mutableStateOf(true) }
    val player = remember(stream) {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; TV)")
            .setDefaultRequestProperties(mapOf("Referer" to stream.referer))
        ExoPlayer.Builder(context).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().apply {
            val builder = MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8)
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(stream.subtitleUrl))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            setMediaItem(builder.build())
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
                override fun onPlayerError(error: PlaybackException) { onError("Playback failed. The stream may have expired. Return to episodes and retry.") }
            })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(1f).height(380.dp)) {
            AndroidView(
                factory = { PlayerView(it).apply {
                    this.player = player
                    useController = true
                    isFocusable = true
                    post { requestFocus(); showController() }
                } },
                modifier = Modifier.fillMaxSize()
            )
            if (buffering) Text("Buffering…", modifier = Modifier.align(Alignment.Center))
        }
    }
    Text("Select pauses or shows controls. Use D-pad to seek and choose captions; Back returns to episodes.", fontSize = 14.sp)
}
