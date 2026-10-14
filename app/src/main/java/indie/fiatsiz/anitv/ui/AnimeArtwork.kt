package indie.fiatsiz.anitv.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import indie.fiatsiz.anitv.AnimeDetailCache
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.source.HiAnimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class AnimeArtwork(
    val details: AnimeDetails?,
    val poster: ImageBitmap?,
    val loading: Boolean
)

@Composable
internal fun rememberAnimeArtwork(
    anime: Anime,
    cache: AnimeDetailCache,
    source: HiAnimeSource
): AnimeArtwork {
    var details by remember(anime) { mutableStateOf<AnimeDetails?>(null) }
    var poster by remember(anime) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(anime) { mutableStateOf(true) }
    LaunchedEffect(anime, cache, source) {
        try {
            val cached = withContext(Dispatchers.IO) { cache.read(anime) }
            if (cached != null) {
                details = cached.details
                poster = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(cached.poster, 0, cached.poster.size)
                        ?.asImageBitmap()
                }
            }
            if (cached?.isFresh(System.currentTimeMillis()) == true && poster != null) return@LaunchedEffect
            val fresh = withContext(Dispatchers.IO) { source.details(anime) }
            details = fresh
            val bytes = withContext(Dispatchers.IO) { source.poster(fresh.posterUrl) }
            val decoded = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
            if (decoded != null) {
                withContext(Dispatchers.IO) {
                    cache.save(
                        anime,
                        fresh,
                        bytes,
                        System.currentTimeMillis()
                    )
                }
                poster = decoded
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Cached information and the resume action remain available if artwork refresh fails.
        } finally {
            loading = false
        }
    }
    return AnimeArtwork(details, poster, loading)
}
