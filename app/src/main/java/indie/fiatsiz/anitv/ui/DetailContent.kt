package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.WatchProgress
import indie.fiatsiz.anitv.R

@Composable
internal fun DetailContent(selected: Anime, entry: LibraryEntry?, episodes: List<Episode>, details: AnimeDetails?,
    poster: ImageBitmap?, detailError: String?, loading: Boolean, error: String?,
    firstFocus: androidx.compose.ui.focus.FocusRequester, onBack: () -> Unit, onFavorite: () -> Unit,
    onCompleted: () -> Unit, onEpisode: (Episode) -> Unit, onRetry: () -> Unit) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Button(onClick = onBack, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.back)) }
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
                            Button(onClick = { onFavorite() }) {
                                Text(stringResource(if (entry?.favorite == true) R.string.remove_favorite else R.string.add_favorite))
                            }
                            Button(onClick = { onCompleted() }) {
                                Text(stringResource(if (entry?.completed == true) R.string.move_to_watching else R.string.mark_completed))
                            }
                        }
                    }
                    if (loading) item { Text(stringResource(R.string.loading_episodes)) }
                    error?.let { message -> item { Text(message, color = Color(0xFFFF9999)) } }
                    if (!loading && episodes.isNotEmpty()) {
                        item { Text(stringResource(R.string.episodes_heading), fontSize = 15.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp) }
                        items(episodes, key = { it.id }) { episodeItem ->
                            EpisodeButton(episodeItem, entry?.progress) { onEpisode(episodeItem) }
                        }
                    }
                    if (episodes.isEmpty() && !loading) item {
                        Button(onClick = { onRetry() }) { Text(stringResource(R.string.retry_episodes)) }
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
internal fun EpisodeButton(episode: Episode, progress: WatchProgress?, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(focusedContainerColor = Color(0xFF4338CA), focusedContentColor = Color.White)) {
        val resume = progress?.takeIf { it.episodeId == episode.id && it.positionMs > 0 }
        val title = stringResource(R.string.episode_button, episode.number, localizedEpisodeTitle(episode))
        Text(if (resume == null) title else stringResource(R.string.episode_resume, title, resume.positionMs / 60_000))
    }
}

@Composable
internal fun localizedEpisodeTitle(episode: Episode): String =
    if (episode.title == "Episode ${episode.number}") stringResource(R.string.episode_number, episode.number) else episode.title
