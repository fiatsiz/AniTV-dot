package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.source.AudioVersion
import indie.fiatsiz.anitv.source.EpisodeServer
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import indie.fiatsiz.anitv.playback.EpisodeSidebarContent
import indie.fiatsiz.anitv.LibraryEntry
import indie.fiatsiz.anitv.WatchProgress
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import indie.fiatsiz.anitv.TranslationSettings
import indie.fiatsiz.anitv.R

private val previewUpdateState = indie.fiatsiz.anitv.update.UpdateState(
    "1.1.0", release = indie.fiatsiz.anitv.update.Release(
        indie.fiatsiz.anitv.update.StableVersion.parse("1.2.0")!!,
        "Sample stable release notes.",
        "https://github.com/fiatsiz/AniTV-dot/releases/download/v1.2.0/AniTV-1.2.0.apk",
        30_000_000, "0".repeat(64)), check = indie.fiatsiz.anitv.update.CheckStatus.AVAILABLE)

@Preview(name = "TV home", widthDp = 960, heightDp = 540)
@Composable
private fun HomePreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrandHeader()
                HomeContent(
                    emptyList(),
                    remember { androidx.compose.ui.focus.FocusRequester() },
                    Modifier.weight(1f),
                    updateVersion = "1.2.0",
                    onNavigate = {}) {}
            }
        }
    }
}

@Preview(name = "TV episodes", widthDp = 960, heightDp = 540)
@Composable
private fun EpisodesPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BrandHeader()
                Text("Sample anime", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.episodes_heading),
                    fontSize = 15.sp,
                    color = Color(0xFFB9C5D8),
                    letterSpacing = 2.sp
                )
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
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrandHeader()
                SettingsContent(
                    "en",
                    TranslationSettings(),
                    remember { androidx.compose.ui.focus.FocusRequester() },
                    {},
                    {},
                    updateState = previewUpdateState,
                    onTranslationSettings = {})
            }
        }
    }
}

@Preview(name = "TV settings (Turkish)", widthDp = 960, heightDp = 540, locale = "tr")
@Composable
private fun SettingsTurkishPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrandHeader()
                SettingsContent(
                    "tr",
                    TranslationSettings(),
                    remember { androidx.compose.ui.focus.FocusRequester() },
                    {},
                    {},
                    updateState = previewUpdateState.copy(
                        download = indie.fiatsiz.anitv.update.DownloadStatus.DOWNLOADING, bytes = 12_000_000),
                    onTranslationSettings = {})
            }
        }
    }
}

@Preview(name = "TV audio choice", widthDp = 960, heightDp = 540)
@Composable
private fun AudioChoicePreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrandHeader()
                EpisodeContent(
                    "Sample anime",
                    Episode("1", "1", "Episode 1"),
                    listOf(
                        EpisodeServer(AudioVersion.SUB, "https://player.example/sub"),
                        EpisodeServer(AudioVersion.DUB, "https://player.example/dub")
                    ),
                    false,
                    null,
                    remember { androidx.compose.ui.focus.FocusRequester() },
                    {},
                    {},
                    {})
            }
        }
    }
}

@Preview(name = "TV continue watching", widthDp = 960, heightDp = 540)
@Composable
private fun ContinueWatchingPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrandHeader()
                Text(
                    stringResource(R.string.continue_watching),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ContinueWatchingCard(
                    LibraryEntry(
                        Anime("1", "Sample anime"),
                        progress = WatchProgress("2", "2", 753_000, 1_440_000, AudioVersion.DUB)
                    ),
                    AnimeDetails(
                        "A sample description for the title you last watched.",
                        "https://image.example/poster.webp",
                        emptyList()
                    ),
                    null, false
                ) {}
            }
        }
    }
}

@Preview(name = "TV episode sidebar", widthDp = 960, heightDp = 540)
@Composable
private fun EpisodeSidebarPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            EpisodeSidebarContent(
                "Sample anime", Episode("3", "3", "Episode 3"),
                (1..12).map { Episode("$it", "$it", "Episode $it") }, false, null, {}, {}, {})
        }
    }
}

@Preview(name = "TV favorite card", widthDp = 960, heightDp = 540)
@Composable
private fun FavoriteCardPreview() {
    AniTVTheme(isInDarkTheme = true) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F0F23))
                    .padding(horizontal = 56.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                BrandHeader()
                Text(
                    stringResource(R.string.favorites),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
                FavoriteCard(
                    LibraryEntry(Anime("1", "Sample favorite"), favorite = true),
                    AnimeDetails(
                        "A saved title with artwork, a description, and details.",
                        "https://image.example/poster.webp",
                        listOf("Status: Currently Airing", "Genres: Adventure")
                    ),
                    null, false
                ) {}
            }
        }
    }
}
