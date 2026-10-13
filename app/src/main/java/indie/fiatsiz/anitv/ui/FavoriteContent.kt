package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.source.AnimeDetails

@Composable
internal fun FavoriteCard(entry: LibraryEntry, details: AnimeDetails?, poster: ImageBitmap?, loading: Boolean,
    onAnime: () -> Unit) {
    Button(onClick = onAnime, modifier = Modifier.fillMaxWidth(),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(focusedContainerColor = Color(0xFF4338CA), focusedContentColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(140.dp).height(200.dp).background(Color(0xFF27273B))) {
                if (poster != null) Image(poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text(stringResource(if (loading) R.string.artwork_loading else R.string.artwork_unavailable),
                    modifier = Modifier.align(Alignment.Center).padding(12.dp), color = Color.White, fontSize = 14.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(entry.anime.title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry.progress?.let { Text(stringResource(R.string.episode_number, it.episodeNumber), fontSize = 16.sp) }
                Text(details?.description ?: stringResource(if (loading) R.string.loading_details else R.string.detail_unavailable),
                    fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!details?.facts.isNullOrEmpty()) Text(details.facts.joinToString("  ·  "),
                    fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
