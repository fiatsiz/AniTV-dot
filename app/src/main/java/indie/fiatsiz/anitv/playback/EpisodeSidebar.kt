package indie.fiatsiz.anitv.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.ui.localizedEpisodeTitle

@Composable
internal fun EpisodeSidebar(animeTitle: String, currentEpisode: Episode, episodes: List<Episode>, loading: Boolean,
    error: String?, onDismiss: () -> Unit, onRetry: () -> Unit, onSelect: (Episode) -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color(0x66000000))) {
            EpisodeSidebarContent(animeTitle, currentEpisode, episodes, loading, error, onDismiss, onRetry, onSelect)
        }
    }
}

@Composable
internal fun EpisodeSidebarContent(animeTitle: String, currentEpisode: Episode, episodes: List<Episode>, loading: Boolean,
    error: String?, onDismiss: () -> Unit, onRetry: () -> Unit, onSelect: (Episode) -> Unit) {
    val currentFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val focusIndex = episodeSidebarFocusIndex(episodes, currentEpisode.id)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = focusIndex.coerceAtLeast(0))
    LaunchedEffect(episodes, currentEpisode.id, loading) {
        if (focusIndex >= 0) {
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            currentFocus.requestFocus()
        } else closeFocus.requestFocus()
    }
    Column(Modifier.width(420.dp).fillMaxHeight().background(Color(0xFF0F0F23)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.episodes), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(animeTitle, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.now_playing_episode, currentEpisode.number), color = Color(0xFFB9C5D8), fontSize = 18.sp)
        Button(onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus)) { Text(stringResource(R.string.close_episodes)) }
        if (loading) Text(stringResource(R.string.loading_episodes))
        error?.let { Text(it, color = Color(0xFFFF9999)) }
        if (!loading && (error != null || episodes.isEmpty())) {
            if (error == null) Text(stringResource(R.string.error_no_episodes), color = Color(0xFFFF9999))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry_episodes)) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                val isPlaying = episode.id == currentEpisode.id
                Button(onClick = { onSelect(episode) },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (index == focusIndex) Modifier.focusRequester(currentFocus) else Modifier)
                        .semantics { selected = isPlaying },
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isPlaying) Color(0xFF28235A) else Color(0xFF27273B),
                        contentColor = Color.White, focusedContainerColor = Color(0xFF4338CA), focusedContentColor = Color.White)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.episode_button, episode.number, localizedEpisodeTitle(episode)),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (isPlaying) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.episode_playing), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

internal fun episodeSidebarFocusIndex(episodes: List<Episode>, currentId: String): Int =
    if (episodes.isEmpty()) -1 else episodes.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
