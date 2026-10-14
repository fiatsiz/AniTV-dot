package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.source.AudioVersion
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.EpisodeServer

@Composable
internal fun EpisodeContent(
    animeTitle: String, episode: Episode, servers: List<EpisodeServer>, loading: Boolean,
    error: String?, firstFocus: FocusRequester, onBack: () -> Unit, onPlay: (EpisodeServer) -> Unit,
    onRetry: () -> Unit
) {
    Button(
        onClick = onBack,
        modifier = Modifier.focusRequester(firstFocus)
    ) { Text(stringResource(R.string.back_to_episodes)) }
    Text("$animeTitle · ${localizedEpisodeTitle(episode)}", fontSize = 26.sp)
    if (loading) Text(stringResource(R.string.loading_stream))
    error?.let { Text(it, color = Color(0xFFFF9999)) }
    if (!loading && servers.isNotEmpty()) {
        Text(stringResource(R.string.audio_version), fontSize = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            servers.forEach { server ->
                Button(onClick = { onPlay(server) }) {
                    Text(stringResource(if (server.audio == AudioVersion.SUB) R.string.audio_sub else R.string.audio_dub))
                }
            }
        }
        if (servers.any { it.audio == AudioVersion.DUB })
            Text(
                stringResource(R.string.audio_source_hint),
                color = Color(0xFFB9C5D8),
                fontSize = 16.sp
            )
    }
    if (error != null && !loading) Button(onClick = onRetry) { Text(stringResource(R.string.retry_playback)) }
}
