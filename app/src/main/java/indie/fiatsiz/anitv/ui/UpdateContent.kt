package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.R
import indie.fiatsiz.anitv.update.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun UpdateContent(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    rulesChecking: Boolean = false
) {
    val busy = state.download in setOf(DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING)
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateFormat = remember(locale) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            locale
        )
    }
    val status = when (state.check) {
        CheckStatus.NEVER -> R.string.update_never
        CheckStatus.CHECKING -> R.string.update_checking
        CheckStatus.CURRENT -> R.string.update_current
        CheckStatus.AVAILABLE -> R.string.update_available
        CheckStatus.NO_STABLE -> R.string.update_no_stable
        CheckStatus.ERROR -> R.string.update_stale
        CheckStatus.RATE_LIMIT -> R.string.update_rate_limit
        CheckStatus.DEBOUNCED -> R.string.update_debounce
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.updates), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.update_installed, state.installedVersion), fontSize = 18.sp)
        Text(
            stringResource(
                R.string.update_latest,
                state.release?.version?.name ?: stringResource(R.string.update_unknown)
            ), fontSize = 18.sp
        )
        Text(
            stringResource(
                R.string.update_last_check,
                state.lastSuccess?.let { dateFormat.format(Date(it)) }
                    ?: stringResource(R.string.update_unknown)), fontSize = 18.sp)
        Text(stringResource(status), fontSize = 18.sp)
        state.problem?.let { Text(updateProblemText(it), fontSize = 18.sp) }
        if (state.retryAt > 0) Text(
            stringResource(
                R.string.update_retry_at,
                dateFormat.format(Date(state.retryAt))
            ), fontSize = 18.sp
        )
        Button(
            onClick = onCheck,
            enabled = !busy && state.check != CheckStatus.CHECKING && !rulesChecking,
            modifier = Modifier.heightIn(min = 48.dp),
            scale = ButtonDefaults.scale(focusedScale = 1f)
        ) {
            Text(stringResource(R.string.update_check))
        }
        if (state.available) {
            if (state.download == DownloadStatus.DOWNLOADING) {
                val fraction = (state.bytes.toFloat() / (state.release?.size ?: 1)).coerceIn(0f, 1f)
                Text(
                    stringResource(
                        R.string.update_progress, (fraction * 100).toInt(), state.bytes / 1024,
                        (state.release?.size ?: 0) / 1024
                    ), fontSize = 18.sp
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                        }) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
            val downloadStatus = when (state.download) {
                DownloadStatus.VERIFYING -> R.string.update_verifying
                DownloadStatus.READY -> R.string.update_ready
                DownloadStatus.CANCELLED -> R.string.update_cancelled
                DownloadStatus.ERROR -> R.string.update_download_failed
                else -> null
            }
            downloadStatus?.let { Text(stringResource(it), fontSize = 18.sp) }
            state.downloadProblem?.let { Text(updateProblemText(it), fontSize = 18.sp) }
            val installation = when (state.install) {
                InstallStatus.PERMISSION -> R.string.update_permission
                InstallStatus.PERMISSION_DENIED -> R.string.update_permission_denied
                InstallStatus.OFFER_INSTALL -> R.string.update_permission_granted
                InstallStatus.INSTALLER -> R.string.update_installer
                InstallStatus.NOT_INSTALLED -> R.string.update_not_installed
                InstallStatus.UNAVAILABLE -> R.string.update_installer_missing
                InstallStatus.IDLE -> null
            }
            installation?.let { Text(stringResource(it), fontSize = 18.sp) }
            // One persistent button keeps remote focus as download and installation states change.
            Button(
                onClick = {
                    when {
                        busy -> onCancel()
                        state.download == DownloadStatus.READY -> onInstall()
                        else -> onDownload()
                    }
                },
                enabled = state.check != CheckStatus.CHECKING,
                modifier = Modifier.heightIn(min = 48.dp),
                scale = ButtonDefaults.scale(focusedScale = 1f)
            ) {
                Text(
                    stringResource(
                        when {
                            busy -> R.string.update_cancel
                            state.download == DownloadStatus.READY -> R.string.update_install
                            state.download in setOf(
                                DownloadStatus.ERROR,
                                DownloadStatus.CANCELLED
                            ) -> R.string.update_retry

                            else -> R.string.update_download
                        }
                    )
                )
            }
        }
        Text(
            stringResource(R.string.update_schedule),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.release?.let { release ->
            var expanded by remember(release.version) { mutableStateOf(false) }
            Button(
                onClick = { expanded = !expanded },
                scale = ButtonDefaults.scale(focusedScale = 1f)
            ) {
                Text(stringResource(if (expanded) R.string.update_hide_notes else R.string.update_notes))
            }
            if (expanded) {
                val notes = release.notes.ifBlank { stringResource(R.string.update_empty_notes) }
                // Short focusable text sections let the D-pad scroll through long, plain-text release notes.
                val chunkSize =
                    (400 / (configuration.fontScale * configuration.fontScale).coerceAtLeast(1f))
                        .toInt().coerceAtLeast(40)
                for (part in notes.chunked(chunkSize)) {
                    var focused by remember { mutableStateOf(false) }
                    Text(
                        part, fontSize = 18.sp, modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (focused) 2.dp else 0.dp,
                                if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun updateProblemText(problem: UpdateProblem): String = stringResource(
    when (problem) {
        UpdateProblem.NETWORK -> R.string.update_error_network
        UpdateProblem.HTTP -> R.string.update_error_http
        UpdateProblem.METADATA -> R.string.update_error_metadata
        UpdateProblem.RATE_LIMIT -> R.string.update_rate_limit
        UpdateProblem.STORAGE -> R.string.update_error_storage
        UpdateProblem.DOWNLOAD -> R.string.update_error_download
        UpdateProblem.SIZE -> R.string.update_error_size
        UpdateProblem.DIGEST -> R.string.update_error_digest
        UpdateProblem.UNSAFE_URL -> R.string.update_error_url
        UpdateProblem.APK_INVALID -> R.string.update_error_apk
        UpdateProblem.PACKAGE -> R.string.update_error_package
        UpdateProblem.CERTIFICATE -> R.string.update_error_certificate
        UpdateProblem.VERSION -> R.string.update_error_version
        UpdateProblem.SDK -> R.string.update_error_sdk
    }
)
