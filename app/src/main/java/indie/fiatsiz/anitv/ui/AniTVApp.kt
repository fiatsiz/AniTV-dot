package indie.fiatsiz.anitv.ui

import android.app.LocaleManager
import android.os.LocaleList
import android.graphics.BitmapFactory
import android.webkit.WebSettings
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.source.EpisodeServer
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.HiAnimeSource
import indie.fiatsiz.anitv.source.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import indie.fiatsiz.anitv.AnimeDetailCache
import indie.fiatsiz.anitv.LibraryStore
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.WatchProgress
import indie.fiatsiz.anitv.TranslationSettingsStore
import indie.fiatsiz.anitv.localizedError
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.playback.Playback

internal enum class Page { HOME, SEARCH, FAVORITES, WATCHING, COMPLETED, SETTINGS }

@Composable
internal fun AniTVApp(lifecycle: Lifecycle) {
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
    var episodeServers by remember { mutableStateOf<List<EpisodeServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<EpisodeServer?>(null) }
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

    fun playServer(selected: EpisodeServer) {
        selectedServer = selected
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

    fun selectEpisode(selected: Episode) {
        episode = selected
        episodeServers = emptyList()
        selectedServer = null
        stream = null
        error = null
        loading = true
        val current = ++request
        scope.launch {
            try {
                val available = withContext(Dispatchers.IO) { source.servers(selected) }
                if (current != request) return@launch
                episodeServers = available
                if (available.size == 1) playServer(available.single())
            } catch (e: Exception) { if (current == request) error = localizedError(context, e, R.string.stream_failed) }
            finally { if (current == request) loading = false }
        }
    }

    fun back() {
        request++
        error = null
        loading = false
        when {
            episode != null -> { episode = null; stream = null; episodeServers = emptyList(); selectedServer = null }
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
        Playback(lifecycle, stream!!, saved, translationSettings, context.resources.configuration.locales[0].language, userAgent, onBack = ::back, onTranslationSettings = { next ->
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
                EpisodeContent(anime?.title.orEmpty(), episode!!, episodeServers, loading, error, firstFocus,
                    onBack = ::back, onPlay = ::playServer, onRetry = {
                        selectedServer?.let(::playServer) ?: selectEpisode(episode!!)
                    })
            }
            anime != null -> {
                val selected = anime!!
                val entry = library.firstOrNull { it.anime.id == selected.id }
                DetailContent(selected, entry, episodes, details, poster, detailError, loading, error, firstFocus, ::back,
                    onFavorite = { save { LibraryStore.update(it, selected) { item -> item.copy(favorite = !item.favorite) } } },
                    onCompleted = { save { LibraryStore.update(it, selected) { item -> item.copy(completed = !item.completed) } } },
                    onEpisode = ::selectEpisode, onRetry = { selectAnime(selected) })
            }
            page == Page.HOME -> {
                HomeContent(library, firstFocus) { page = it }
            }
            page == Page.SEARCH -> {
                SearchContent(query, loading, searched, error, results, firstFocus, ::back, { query = it }, ::search, ::selectAnime)
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
                LibraryContent(page, library, firstFocus, ::back, ::selectAnime)
            }
        }
    }
}
