package indie.fiatsiz.anitv.playback

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.source.OpeningInterval

internal enum class OpeningStatus { LOADING, READY, NOT_FOUND, UNAVAILABLE }

internal fun openingSkipTarget(
    interval: OpeningInterval?,
    positionMs: Long,
    durationMs: Long,
    active: Boolean
): Long? {
    if (!active || interval == null || interval.startMs < 0 ||
        interval.endMs <= interval.startMs || interval.endMs >= durationMs ||
        positionMs !in interval.startMs until interval.endMs
    ) return null
    return interval.endMs
}

@Composable
internal fun OpeningSkipSettings(enabled: Boolean, status: OpeningStatus, onToggle: () -> Unit) {
    Column {
        Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (enabled) R.string.opening_skip_on else R.string.opening_skip_off))
        }
        Text(
            stringResource(
                if (!enabled) R.string.opening_skip_hint else when (status) {
                    OpeningStatus.LOADING -> R.string.opening_skip_loading
                    OpeningStatus.READY -> R.string.opening_skip_ready
                    OpeningStatus.NOT_FOUND -> R.string.opening_skip_not_found
                    OpeningStatus.UNAVAILABLE -> R.string.opening_skip_unavailable
                }
            )
        )
    }
}
