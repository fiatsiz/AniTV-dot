package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.LibraryStore
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.R
import kotlinx.coroutines.launch

@Composable
internal fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.anitv_logo_vector), "AniTV", Modifier.width(120.dp).height(60.dp), contentScale = ContentScale.Crop)
        Text(stringResource(R.string.tagline), fontSize = 14.sp, color = Color(0xFFB9C5D8), letterSpacing = 2.sp)
    }
}

// Use minimum-distance scrolling instead of the TV pivot that moves already visible controls.
internal object HomeBringIntoViewSpec : BringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeContent(library: List<LibraryEntry>, firstFocus: androidx.compose.ui.focus.FocusRequester,
    modifier: Modifier = Modifier, onNavigate: (Page) -> Unit, continueWatching: @Composable (LibraryEntry) -> Unit) {
    val latest = LibraryStore.continueWatching(library)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(LocalBringIntoViewSpec provides HomeBringIntoViewSpec) {
        LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "main_menu") {
                Column(Modifier.fillMaxWidth().onFocusChanged {
                    if (it.hasFocus) scope.launch { listState.scrollToItem(0) }
                }.focusGroup(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
            }
            if (latest != null) {
                item(key = "continue_heading") {
                    Text(stringResource(R.string.continue_watching), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                }
                item(key = "continue_${latest.anime.id}") { continueWatching(latest) }
            }
        }
    }
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
