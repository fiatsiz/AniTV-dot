package indie.fiatsiz.anitv

import android.app.LocaleManager
import android.os.LocaleList
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.TypedValue
import android.webkit.WebSettings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.HiAnimeSource
import indie.fiatsiz.anitv.source.Stream
import indie.fiatsiz.anitv.source.SubtitleCue
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

private enum class Page { HOME, SEARCH, FAVORITES, WATCHING, COMPLETED, SETTINGS }

@Composable
private fun AniTV() {
    val context = LocalContext.current
    val userAgent = remember(context) {
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: System.getProperty("http.agent")?.takeIf { it.isNotBlank() }
            ?: "AniTV (Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL})"
    }
    val source = remember(userAgent) { HiAnimeSource(userAgent) }
    val store = remember { LibraryStore(context) }
    val detailCache = remember { AnimeDetailCache(context) }
    val translationStore = remember { TranslationSettingsStore(context) }
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf(store.read()) }
    var translationSettings by remember { mutableStateOf(translationStore.read()) }
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
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
            } catch (e: Exception) { if (current == request) error = localizedError(context, e, R.string.search_failed) }
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
            } catch (e: Exception) { if (current == request) error = localizedError(context, e, R.string.episodes_failed) }
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
                if (decoded == null) {
                    if (current == request) detailError = context.getString(R.string.poster_unreadable)
                    return@launch
                }
                withContext(Dispatchers.IO) { detailCache.save(selected, fresh, bytes, System.currentTimeMillis()) }
                if (current == request) poster = decoded
            } catch (e: Exception) {
                if (current == request) detailError = localizedError(context, e, R.string.detail_unavailable)
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
            } catch (e: Exception) { if (current == request) error = localizedError(context, e, R.string.stream_failed) }
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
        Playback(stream!!, saved, translationSettings, context.resources.configuration.locales[0].language, userAgent, onBack = ::back, onTranslationSettings = { next ->
            translationSettings = next
            translationStore.save(next)
        }, onProgress = { position, duration ->
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
        BrandHeader()
        when {
            episode != null -> {
                Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.back_to_episodes)) }
                Text("${anime?.title} · ${localizedEpisodeTitle(episode!!)}", fontSize = 26.sp)
                if (loading) Text(stringResource(R.string.loading_stream))
                error?.let { Text(it, color = Color(0xFFFF9999)) }
                if (error != null) Button(onClick = { selectEpisode(episode!!) }) { Text(stringResource(R.string.retry_playback)) }
            }
            anime != null -> {
                val selected = anime!!
                val entry = library.firstOrNull { it.anime.id == selected.id }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.back)) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                            Box(Modifier.width(180.dp).height(280.dp).background(Color(0xFF27273B))) {
                                poster?.let { Image(it, stringResource(R.string.poster_description, selected.title), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                if (poster == null) Text(stringResource(if (detailError == null) R.string.artwork_loading else R.string.artwork_unavailable),
                                    modifier = Modifier.align(Alignment.Center).padding(12.dp), color = Color.White)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(selected.title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                                details?.let { info ->
                                    Text(info.description, maxLines = 7, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                                    info.facts.forEach { fact -> LocalizedFact(fact) }
                                }
                                detailError?.let { Text(it, color = Color(0xFFFF9999)) }
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { save { LibraryStore.update(it, selected) { item -> item.copy(favorite = !item.favorite) } } }) {
                                Text(stringResource(if (entry?.favorite == true) R.string.remove_favorite else R.string.add_favorite))
                            }
                            Button(onClick = { save { LibraryStore.update(it, selected) { item -> item.copy(completed = !item.completed) } } }) {
                                Text(stringResource(if (entry?.completed == true) R.string.move_to_watching else R.string.mark_completed))
                            }
                        }
                    }
                    if (loading) item { Text(stringResource(R.string.loading_episodes)) }
                    error?.let { message -> item { Text(message, color = Color(0xFFFF9999)) } }
                    if (!loading && episodes.isNotEmpty()) {
                        item { Text(stringResource(R.string.episodes_heading), fontSize = 15.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp) }
                        items(episodes, key = { it.id }) { episodeItem ->
                            EpisodeButton(episodeItem, entry?.progress) { selectEpisode(episodeItem) }
                        }
                    }
                    if (episodes.isEmpty() && !loading) item {
                        Button(onClick = { selectAnime(selected) }) { Text(stringResource(R.string.retry_episodes)) }
                    }
                }
            }
            page == Page.HOME -> {
                HomeContent(library, firstFocus) { page = it }
            }
            page == Page.SEARCH -> {
                Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.home)) }
                Text(stringResource(R.string.search_anime), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                SearchField(query, { query = it }, ::search)
                Button(onClick = ::search, enabled = query.isNotBlank() && !loading) { Text(stringResource(R.string.search)) }
                if (loading) Text(stringResource(R.string.searching))
                error?.let { Text(it, color = Color(0xFFFF9999)) }
                if (searched && !loading && error == null && results.isEmpty()) Text(stringResource(R.string.no_results))
                if (results.isNotEmpty()) {
                    Text(stringResource(R.string.results_heading), fontSize = 15.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results, key = { it.id }) { item ->
                            Button(onClick = { selectAnime(item) }, modifier = Modifier.fillMaxWidth()) { Text(item.title) }
                        }
                    }
                }
            }
            page == Page.SETTINGS -> {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                val appLocales = localeManager.applicationLocales
                val selectedLanguage = if (appLocales.isEmpty) "" else appLocales[0].language
                SettingsContent(selectedLanguage, translationSettings, firstFocus, ::back, { tag ->
                    if (tag != selectedLanguage)
                        localeManager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                }) { next ->
                    translationSettings = next
                    translationStore.save(next)
                }
            }
            else -> {
                Button(onClick = ::back, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.home)) }
                val entries = when (page) {
                    Page.FAVORITES -> library.filter { it.favorite }
                    Page.WATCHING -> LibraryStore.watching(library)
                    else -> library.filter { it.completed }
                }
                Text(stringResource(when (page) { Page.FAVORITES -> R.string.favorites; Page.WATCHING -> R.string.watching; else -> R.string.completed_anime }),
                    fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                if (entries.isEmpty()) Text(stringResource(R.string.empty_library), color = Color(0xFFB9C5D8))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(entries, key = { it.anime.id }) { item ->
                        Button(onClick = { selectAnime(item.anime) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.anime.title + (item.progress?.let { "  ·  ${stringResource(R.string.episode_number, it.episodeNumber)}" } ?: ""))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.anitv_logo_vector), "AniTV", Modifier.width(120.dp).height(60.dp), contentScale = ContentScale.Crop)
        Text(stringResource(R.string.tagline), fontSize = 14.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp)
    }
}

@Composable
private fun HomeContent(library: List<LibraryEntry>, firstFocus: androidx.compose.ui.focus.FocusRequester, onNavigate: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.home_heading), modifier = Modifier.weight(1f), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Button(onClick = { onNavigate(Page.SETTINGS) }, shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings))
        }
    }
    Text(stringResource(R.string.home_description), color = Color(0xFFB9C5D8), fontSize = 18.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        HomeButton(stringResource(R.string.search), stringResource(R.string.find_title), Icons.Filled.Search, Modifier.weight(1f).focusRequester(firstFocus)) { onNavigate(Page.SEARCH) }
        HomeButton(stringResource(R.string.watching), stringResource(R.string.title_count, LibraryStore.watching(library).size), Icons.Filled.PlayArrow, Modifier.weight(1f)) { onNavigate(Page.WATCHING) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        HomeButton(stringResource(R.string.favorites), stringResource(R.string.saved_count, library.count { it.favorite }), Icons.Filled.Favorite, Modifier.weight(1f)) { onNavigate(Page.FAVORITES) }
        HomeButton(stringResource(R.string.completed), stringResource(R.string.archived_count, library.count { it.completed }), Icons.Filled.CheckCircle, Modifier.weight(1f)) { onNavigate(Page.COMPLETED) }
    }
}

@Composable
private fun LanguageChoice(label: Int, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.semantics { this.selected = selected },
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))) {
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(label))
    }
}

@Composable
private fun SettingsContent(selectedLanguage: String, translationSettings: TranslationSettings,
    firstFocus: androidx.compose.ui.focus.FocusRequester, onBack: () -> Unit, onSelect: (String) -> Unit,
    onTranslationSettings: (TranslationSettings) -> Unit) {
    var keyFocused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Button(onClick = onBack, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.home)) }
    Text(stringResource(R.string.settings), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.language_hint), color = Color(0xFFB9C5D8), fontSize = 18.sp)
    Text(stringResource(R.string.language), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguageChoice(R.string.language_system, selectedLanguage.isEmpty()) { onSelect("") }
        LanguageChoice(R.string.language_english, selectedLanguage == "en") { onSelect("en") }
        LanguageChoice(R.string.language_turkish, selectedLanguage == "tr") { onSelect("tr") }
    }
    Text(stringResource(R.string.caption_translation), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.translation_hint), color = Color(0xFFB9C5D8), fontSize = 16.sp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageChoice(R.string.translation_off, translationSettings.provider == TranslationProvider.OFF) {
            onTranslationSettings(translationSettings.copy(provider = TranslationProvider.OFF))
        }
        LanguageChoice(R.string.translation_google, translationSettings.provider == TranslationProvider.GOOGLE) {
            onTranslationSettings(translationSettings.copy(provider = TranslationProvider.GOOGLE))
        }
        LanguageChoice(R.string.translation_deepl, translationSettings.provider == TranslationProvider.DEEPL) {
            onTranslationSettings(translationSettings.copy(provider = TranslationProvider.DEEPL))
        }
    }
    Text(stringResource(R.string.translation_key), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Box(Modifier.fillMaxWidth().height(54.dp)
        .border(if (keyFocused) 3.dp else 1.dp, if (keyFocused) Color.White else Color.Gray, RoundedCornerShape(8.dp))
        .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
        BasicTextField(
            value = translationSettings.apiKey,
            onValueChange = { onTranslationSettings(translationSettings.copy(apiKey = it)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 18.sp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().onFocusChanged { keyFocused = it.isFocused }
        )
        if (translationSettings.apiKey.isEmpty() && !keyFocused) Text(stringResource(R.string.translation_key_hint), color = Color.LightGray)
    }
    }
}

@Composable
private fun LocalizedFact(fact: String) {
    val label = fact.substringBefore(':')
    val labelId = when (label) {
        "Aired" -> R.string.fact_aired
        "Status" -> R.string.fact_status
        "Genres" -> R.string.fact_genres
        "MAL Score" -> R.string.fact_mal_score
        else -> null
    }
    Text(if (labelId == null) fact else "${stringResource(labelId)}:${fact.substringAfter(':')}",
        color = Color(0xFFB9C5D8), fontSize = 15.sp)
}

@Composable
private fun HomeButton(title: String, subtitle: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(112.dp), shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 15.sp)
        }
    }
}

@Composable
private fun EpisodeButton(episode: Episode, progress: WatchProgress?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(focusedContainerColor = Color(0xFF4338CA), focusedContentColor = Color.White)) {
        val resume = progress?.takeIf { it.episodeId == episode.id && it.positionMs > 0 }
        val title = stringResource(R.string.episode_button, episode.number, localizedEpisodeTitle(episode))
        Text(if (resume == null) title else stringResource(R.string.episode_resume, title, resume.positionMs / 60_000))
    }
}

@Composable
private fun localizedEpisodeTitle(episode: Episode): String =
    if (episode.title == "Episode ${episode.number}") stringResource(R.string.episode_number, episode.number) else episode.title

@Preview(name = "TV home", widthDp = 960, heightDp = 540)
@Composable
private fun HomePreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0F0F23)).padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandHeader()
                HomeContent(emptyList(), remember { androidx.compose.ui.focus.FocusRequester() }) {}
            }
        }
    }
}

@Preview(name = "TV episodes", widthDp = 960, heightDp = 540)
@Composable
private fun EpisodesPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0F0F23)).padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandHeader()
                Text("Sample anime", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.episodes_heading), fontSize = 15.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp)
                EpisodeButton(Episode("preview-1", "1", "Episode 1"), null) {}
                EpisodeButton(Episode("preview-2", "2", "Episode 2"), null) {}
            }
        }
    }
}

@Preview(name = "TV settings (English)", widthDp = 960, heightDp = 540, locale = "en")
@Composable
private fun SettingsEnglishPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0F0F23)).padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandHeader()
                SettingsContent("en", TranslationSettings(), remember { androidx.compose.ui.focus.FocusRequester() }, {}, {}, {})
            }
        }
    }
}

@Preview(name = "TV settings (Turkish)", widthDp = 960, heightDp = 540, locale = "tr")
@Composable
private fun SettingsTurkishPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().background(Color(0xFF0F0F23)).padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandHeader()
                SettingsContent("tr", TranslationSettings(), remember { androidx.compose.ui.focus.FocusRequester() }, {}, {}, {})
            }
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
        if (value.isEmpty() && !focused) Text(stringResource(R.string.search_hint), color = Color.LightGray)
    }
}

private data class QualityOption(val group: TrackGroup, val index: Int, val label: String)
private enum class CaptionMode { OFF, ORIGINAL, TRANSLATED }
private enum class CaptionMenu { CLOSED, OPTIONS, LANGUAGES, STYLES, FONT, SIZE, BACKGROUND, FOREGROUND }
private val captionLanguages = listOf("ar", "de", "en", "es", "fr", "id", "it", "ja", "ko", "nl", "pl", "pt", "ru", "tr", "uk")
private val captionBackgroundColors = listOf(CaptionColor.BLACK, CaptionColor.BLUE, CaptionColor.WHITE)
private val captionForegroundColors = listOf(CaptionColor.WHITE, CaptionColor.YELLOW, CaptionColor.CYAN, CaptionColor.BLACK)
private fun captionFontLabel(font: CaptionFont) = when (font) {
    CaptionFont.SANS -> R.string.caption_font_sans
    CaptionFont.SERIF -> R.string.caption_font_serif
    CaptionFont.MONOSPACE -> R.string.caption_font_monospace
}
private fun captionColorLabel(color: CaptionColor) = when (color) {
    CaptionColor.BLACK -> R.string.caption_color_black
    CaptionColor.WHITE -> R.string.caption_color_white
    CaptionColor.YELLOW -> R.string.caption_color_yellow
    CaptionColor.BLUE -> R.string.caption_color_blue
    CaptionColor.CYAN -> R.string.caption_color_cyan
}
private fun styleCaptionView(view: SubtitleView, style: CaptionStyleSettings) {
    val typeface = when (style.font) {
        CaptionFont.SANS -> Typeface.SANS_SERIF
        CaptionFont.SERIF -> Typeface.SERIF
        CaptionFont.MONOSPACE -> Typeface.MONOSPACE
    }
    view.setApplyEmbeddedStyles(false)
    view.setStyle(CaptionStyleCompat(style.foreground.argb, style.backgroundArgb,
        android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE,
        android.graphics.Color.TRANSPARENT, typeface))
    view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * style.sizePercent / 100f)
}

@Composable
private fun Playback(stream: Stream, savedPositionMs: Long, translation: TranslationSettings, targetLanguage: String, userAgent: String,
    onBack: () -> Unit, onTranslationSettings: (TranslationSettings) -> Unit,
    onProgress: (Long, Long) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val displayLocale = context.resources.configuration.locales[0]
    val source = remember(userAgent) { HiAnimeSource(userAgent) }
    val translator = remember { SubtitleTranslator() }
    val translationReady = translation.provider != TranslationProvider.OFF && translation.apiKey.isNotBlank()
    val captionStyle = translation.captionStyle
    val effectiveTarget = translation.targetLanguage.ifBlank { targetLanguage }
    var captionMode by remember(stream) { mutableStateOf(if (translationReady) CaptionMode.TRANSLATED else CaptionMode.ORIGINAL) }
    val translationOn = captionMode == CaptionMode.TRANSLATED && translationReady
    var buffering by remember(stream) { mutableStateOf(true) }
    var playing by remember(stream) { mutableStateOf(true) }
    var captionMenu by remember(stream) { mutableStateOf(CaptionMenu.CLOSED) }
    var positionMs by remember(stream) { mutableStateOf(savedPositionMs) }
    var durationMs by remember(stream) { mutableStateOf(0L) }
    var controlsVisible by remember(stream) { mutableStateOf(true) }
    var qualityOpen by remember(stream) { mutableStateOf(false) }
    var qualityOptions by remember(stream) { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember(stream) { mutableStateOf(context.getString(R.string.auto)) }
    var interactionTick by remember(stream) { mutableStateOf(0) }
    var captionCues by remember(stream, translationOn, effectiveTarget) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var translatedTexts by remember(stream, translationOn, effectiveTarget) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var translationFailed by remember(stream, translationOn, effectiveTarget) { mutableStateOf(false) }
    val controlFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val videoFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val qualityFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val captionFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val languageFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val styleFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val choiceFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val progressCallback by androidx.compose.runtime.rememberUpdatedState(onProgress)
    val errorCallback by androidx.compose.runtime.rememberUpdatedState(onError)
    val player = remember(stream) {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(mapOf("Referer" to stream.referer))
        ExoPlayer.Builder(context).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().apply {
            val builder = MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8)
            builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(stream.subtitleUrl))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
            setMediaItem(builder.build())
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, captionMode == CaptionMode.OFF || translationOn).build()
            if (savedPositionMs > 0) seekTo(savedPositionMs)
            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
                override fun onTracksChanged(tracks: Tracks) {
                    qualityOptions = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
                        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
                            val format = group.getTrackFormat(index)
                            val name = if (format.height > 0) "${format.height}p" else context.getString(R.string.video_track, index + 1)
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
                    errorCallback(context.getString(R.string.playback_failed))
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
    androidx.compose.runtime.LaunchedEffect(player) {
        while (true) {
            delay(250)
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
        }
    }
    androidx.compose.runtime.LaunchedEffect(stream, translationOn, translation.provider, translation.apiKey, effectiveTarget) {
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
    DisposableEffect(player) {
        onDispose {
            if (player.currentPosition > 0) progressCallback(player.currentPosition, player.duration.coerceAtLeast(0))
            player.release()
        }
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
    androidx.compose.runtime.LaunchedEffect(controlsVisible, qualityOpen, captionMenu) {
        when {
            !controlsVisible -> videoFocus.requestFocus()
            captionMenu == CaptionMenu.LANGUAGES -> languageFocus.requestFocus()
            captionMenu == CaptionMenu.STYLES -> styleFocus.requestFocus()
            captionMenu in listOf(CaptionMenu.FONT, CaptionMenu.SIZE, CaptionMenu.BACKGROUND, CaptionMenu.FOREGROUND) -> choiceFocus.requestFocus()
            captionMenu == CaptionMenu.OPTIONS -> captionFocus.requestFocus()
            qualityOpen -> qualityFocus.requestFocus()
            else -> controlFocus.requestFocus()
        }
    }
    androidx.compose.runtime.LaunchedEffect(controlsVisible, qualityOpen, captionMenu, playing, interactionTick) {
        if (controlsVisible && playing && !qualityOpen && captionMenu == CaptionMenu.CLOSED) {
            delay(6_000)
            controlsVisible = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                controlsVisible && !qualityOpen && captionMenu == CaptionMenu.CLOSED && event.key == Key.DirectionDown -> {
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
            update = { view -> view.subtitleView?.let { styleCaptionView(it, captionStyle) } },
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
                Button(onClick = onBack) { Text(stringResource(R.string.episodes)) }
                Button(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) { Text(stringResource(R.string.seek_back)) }
                Button(onClick = { if (player.playWhenReady) player.pause() else player.play() }, modifier = Modifier.focusRequester(controlFocus)) {
                    Text(stringResource(if (playing) R.string.pause else R.string.play))
                }
                Button(onClick = { player.seekTo((player.currentPosition + 10_000).coerceAtMost(player.duration.coerceAtLeast(0))) }) { Text(stringResource(R.string.seek_forward)) }
                Button(onClick = {
                    qualityOpen = false
                    captionMenu = if (captionMenu == CaptionMenu.CLOSED) CaptionMenu.OPTIONS else CaptionMenu.CLOSED
                }) { Text(stringResource(R.string.captions_settings)) }
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
                    selectedQuality = context.getString(R.string.auto)
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
            if (captionMenu != CaptionMenu.CLOSED) Column(Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = 16.dp)
                .width(320.dp).background(Color(0xEE0F0F23)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (captionMenu) {
                CaptionMenu.OPTIONS -> {
                    Text(stringResource(R.string.captions_settings), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { captionMode = CaptionMode.OFF; captionMenu = CaptionMenu.CLOSED },
                        modifier = Modifier.fillMaxWidth().then(if (captionMode == CaptionMode.OFF) Modifier.focusRequester(captionFocus) else Modifier)
                            .semantics { selected = captionMode == CaptionMode.OFF }) {
                        Text(stringResource(R.string.captions_off))
                    }
                    Button(onClick = { captionMode = CaptionMode.ORIGINAL; captionMenu = CaptionMenu.CLOSED },
                        modifier = Modifier.fillMaxWidth().then(if (captionMode == CaptionMode.ORIGINAL) Modifier.focusRequester(captionFocus) else Modifier)
                            .semantics { selected = captionMode == CaptionMode.ORIGINAL }) {
                        Text(stringResource(R.string.captions_original))
                    }
                    Button(onClick = { captionMode = CaptionMode.TRANSLATED; captionMenu = CaptionMenu.CLOSED }, enabled = translationReady,
                        modifier = Modifier.fillMaxWidth().then(if (captionMode == CaptionMode.TRANSLATED) Modifier.focusRequester(captionFocus) else Modifier)
                            .semantics { selected = captionMode == CaptionMode.TRANSLATED }) {
                        Text(stringResource(R.string.captions_translate, stringResource(if (translation.provider == TranslationProvider.DEEPL) R.string.translation_deepl else R.string.translation_google)))
                    }
                    if (!translationReady) Text(stringResource(R.string.translation_setup_hint), color = Color(0xFFB9C5D8), fontSize = 14.sp)
                    Button(onClick = { captionMenu = CaptionMenu.LANGUAGES }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.captions_language, Locale.forLanguageTag(effectiveTarget).getDisplayLanguage(displayLocale)))
                    }
                    Button(onClick = { captionMenu = CaptionMenu.STYLES }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.caption_style))
                    }
                }
                CaptionMenu.LANGUAGES -> {
                    Text(stringResource(R.string.captions_language_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.height(320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        captionLanguages.forEach { language ->
                            Button(onClick = {
                                onTranslationSettings(translation.copy(targetLanguage = language))
                                captionMenu = CaptionMenu.OPTIONS
                            }, modifier = Modifier.fillMaxWidth()
                                .then(if (language == effectiveTarget || (language == captionLanguages.first() && effectiveTarget !in captionLanguages)) Modifier.focusRequester(languageFocus) else Modifier)
                                .semantics { selected = language == effectiveTarget }) {
                                Text(Locale.forLanguageTag(language).getDisplayLanguage(displayLocale))
                            }
                        }
                    }
                }
                CaptionMenu.STYLES -> {
                    Text(stringResource(R.string.caption_style), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { captionMenu = CaptionMenu.FONT }, modifier = Modifier.fillMaxWidth().focusRequester(styleFocus)) {
                        Text(stringResource(R.string.caption_font, stringResource(captionFontLabel(captionStyle.font))))
                    }
                    Button(onClick = { captionMenu = CaptionMenu.SIZE }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.caption_size, captionStyle.sizePercent))
                    }
                    Button(onClick = { captionMenu = CaptionMenu.BACKGROUND }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.caption_background, stringResource(captionColorLabel(captionStyle.background))))
                    }
                    Button(onClick = { captionMenu = CaptionMenu.FOREGROUND }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.caption_foreground, stringResource(captionColorLabel(captionStyle.foreground))))
                    }
                }
                CaptionMenu.FONT -> {
                    Text(stringResource(R.string.caption_font_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    CaptionFont.entries.forEach { font ->
                        Button(onClick = {
                            onTranslationSettings(translation.copy(captionStyle = captionStyle.copy(font = font)))
                            captionMenu = CaptionMenu.STYLES
                        }, modifier = Modifier.fillMaxWidth()
                            .then(if (font == captionStyle.font) Modifier.focusRequester(choiceFocus) else Modifier)
                            .semantics { selected = font == captionStyle.font }) {
                            Text(stringResource(captionFontLabel(font)))
                        }
                    }
                }
                CaptionMenu.SIZE -> {
                    Text(stringResource(R.string.caption_size_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    captionSizes.forEach { size ->
                        Button(onClick = {
                            onTranslationSettings(translation.copy(captionStyle = captionStyle.copy(sizePercent = size)))
                            captionMenu = CaptionMenu.STYLES
                        }, modifier = Modifier.fillMaxWidth()
                            .then(if (size == captionStyle.sizePercent) Modifier.focusRequester(choiceFocus) else Modifier)
                            .semantics { selected = size == captionStyle.sizePercent }) { Text("$size%") }
                    }
                }
                CaptionMenu.BACKGROUND, CaptionMenu.FOREGROUND -> {
                    val background = captionMenu == CaptionMenu.BACKGROUND
                    Text(stringResource(if (background) R.string.caption_background_title else R.string.caption_foreground_title),
                        fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    val selectedColor = if (background) captionStyle.background else captionStyle.foreground
                    (if (background) captionBackgroundColors else captionForegroundColors).forEach { color ->
                        Button(onClick = {
                            val next = if (background) captionStyle.copy(background = color) else captionStyle.copy(foreground = color)
                            onTranslationSettings(translation.copy(captionStyle = next))
                            captionMenu = CaptionMenu.STYLES
                        }, modifier = Modifier.fillMaxWidth()
                            .then(if (color == selectedColor) Modifier.focusRequester(choiceFocus) else Modifier)
                            .semantics { selected = color == selectedColor }) {
                            Text(stringResource(captionColorLabel(color)))
                        }
                    }
                }
                CaptionMenu.CLOSED -> Unit
                }
            }
        }
        if (buffering) Text(stringResource(R.string.buffering), modifier = Modifier.align(Alignment.Center), color = Color.White)
    }
}
