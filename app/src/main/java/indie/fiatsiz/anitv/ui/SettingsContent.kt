package indie.fiatsiz.anitv.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import indie.fiatsiz.anitv.TranslationSettings
import indie.fiatsiz.anitv.TranslationProvider
import indie.fiatsiz.anitv.BuildConfig
import indie.fiatsiz.anitv.source.RulesState
import indie.fiatsiz.anitv.source.RulesOutcome
import indie.fiatsiz.anitv.R

@Composable
private fun LanguageChoice(label: Int, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.semantics { this.selected = selected },
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp))
    ) {
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(label))
    }
}

@Composable
internal fun SettingsContent(
    selectedLanguage: String,
    translationSettings: TranslationSettings,
    firstFocus: androidx.compose.ui.focus.FocusRequester,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    rulesState: RulesState = RulesState(1, RulesOutcome.CHECKING),
    updateState: indie.fiatsiz.anitv.update.UpdateState = indie.fiatsiz.anitv.update.UpdateState(
        BuildConfig.VERSION_NAME
    ),
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onTranslationSettings: (TranslationSettings) -> Unit
) {
    var keyFocused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack, modifier = Modifier.focusRequester(firstFocus)) {
            Text(
                stringResource(R.string.home)
            )
        }
        Text(stringResource(R.string.settings), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), fontSize = 18.sp)
        Text(stringResource(R.string.rules_revision, rulesState.revision), fontSize = 18.sp)
        Text(
            stringResource(
                when (rulesState.outcome) {
                    RulesOutcome.CHECKING -> R.string.rules_checking
                    RulesOutcome.UPDATED -> R.string.rules_updated
                    RulesOutcome.CURRENT -> R.string.rules_current
                    RulesOutcome.OFFLINE -> R.string.rules_offline
                    RulesOutcome.TIMEOUT -> R.string.rules_timeout
                    RulesOutcome.REJECTED -> R.string.rules_rejected
                    RulesOutcome.CACHE_FAILED -> R.string.rules_cache_failed
                }
            ), color = Color(0xFFB9C5D8), fontSize = 18.sp
        )
        UpdateContent(updateState, onCheckUpdate, onDownloadUpdate, onCancelUpdate, onInstallUpdate)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.language_hint), color = Color(0xFFB9C5D8), fontSize = 18.sp)
        Text(stringResource(R.string.language), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LanguageChoice(R.string.language_system, selectedLanguage.isEmpty()) { onSelect("") }
            LanguageChoice(R.string.language_english, selectedLanguage == "en") { onSelect("en") }
            LanguageChoice(R.string.language_turkish, selectedLanguage == "tr") { onSelect("tr") }
        }
        Text(
            stringResource(R.string.caption_translation),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(stringResource(R.string.translation_hint), color = Color(0xFFB9C5D8), fontSize = 16.sp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChoice(
                R.string.translation_off,
                translationSettings.provider == TranslationProvider.OFF
            ) {
                onTranslationSettings(translationSettings.copy(provider = TranslationProvider.OFF))
            }
            LanguageChoice(
                R.string.translation_google,
                translationSettings.provider == TranslationProvider.GOOGLE
            ) {
                onTranslationSettings(translationSettings.copy(provider = TranslationProvider.GOOGLE))
            }
            LanguageChoice(
                R.string.translation_deepl,
                translationSettings.provider == TranslationProvider.DEEPL
            ) {
                onTranslationSettings(translationSettings.copy(provider = TranslationProvider.DEEPL))
            }
        }
        Text(
            stringResource(R.string.translation_key),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .border(
                    if (keyFocused) 3.dp else 1.dp,
                    if (keyFocused) Color.White else Color.Gray,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = translationSettings.apiKey,
                onValueChange = { onTranslationSettings(translationSettings.copy(apiKey = it)) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 18.sp
                ),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { keyFocused = it.isFocused }
            )
            if (translationSettings.apiKey.isEmpty() && !keyFocused) Text(
                stringResource(R.string.translation_key_hint),
                color = Color.LightGray
            )
        }
    }
}
