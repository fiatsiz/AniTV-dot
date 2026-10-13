package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.LibraryStore
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.R

@Composable
internal fun LibraryContent(page: Page, library: List<LibraryEntry>, firstFocus: androidx.compose.ui.focus.FocusRequester,
    onBack: () -> Unit, onAnime: (Anime) -> Unit, favoriteContent: @Composable (LibraryEntry) -> Unit) {
                Button(onClick = onBack, modifier = Modifier.focusRequester(firstFocus)) { Text(stringResource(R.string.home)) }
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
                        if (page == Page.FAVORITES) favoriteContent(item)
                        else Button(onClick = { onAnime(item.anime) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.anime.title + (item.progress?.let { "  ·  ${stringResource(R.string.episode_number, it.episodeNumber)}" } ?: ""))
                        }
                    }
                }
}
