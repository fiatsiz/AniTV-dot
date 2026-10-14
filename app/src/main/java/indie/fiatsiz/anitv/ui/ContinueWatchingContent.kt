package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.AnimeDetailCache
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.WatchProgress
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.source.AudioVersion
import indie.fiatsiz.anitv.source.HiAnimeSource

@Composable
internal fun ContinueWatchingSection(
    entry: LibraryEntry,
    cache: AnimeDetailCache,
    source: HiAnimeSource,
    onContinue: () -> Unit
) {
    val artwork = rememberAnimeArtwork(entry.anime, cache, source)
    ContinueWatchingCard(entry, artwork.details, artwork.poster, artwork.loading, onContinue)
}

@Composable
internal fun ContinueWatchingCard(
    entry: LibraryEntry, details: AnimeDetails?, poster: ImageBitmap?, loading: Boolean,
    onContinue: () -> Unit
) {
    val progress = entry.progress ?: return
    Button(
        onClick = onContinue, modifier = Modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(
            focusedContainerColor = Color(0xFF4338CA),
            focusedContentColor = Color.White
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(140.dp)
                    .height(200.dp)
                    .background(Color(0xFF27273B))
            ) {
                if (poster != null) Image(
                    poster,
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else Text(
                    stringResource(if (loading) R.string.artwork_loading else R.string.artwork_unavailable),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        stringResource(R.string.continue_watching),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    entry.anime.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(resumePositionLabel(progress), fontSize = 18.sp)
                Text(
                    details?.description ?: stringResource(R.string.continue_hint),
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ContinueWatchingButton(progress: WatchProgress, onContinue: () -> Unit) {
    Button(onClick = onContinue, shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.continue_watching))
            Text(resumePositionLabel(progress), fontSize = 14.sp)
        }
    }
}

@Composable
private fun resumePositionLabel(progress: WatchProgress): String {
    val position = formatResumePosition(progress.positionMs)
    val timing =
        if (progress.durationMs > 0) "$position / ${formatResumePosition(progress.durationMs)}" else position
    return stringResource(
        R.string.continue_episode_position, progress.episodeNumber, timing,
        stringResource(if (progress.audio == AudioVersion.DUB) R.string.audio_dub else R.string.audio_sub)
    )
}

internal fun formatResumePosition(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
