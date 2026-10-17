package indie.fiatsiz.anitv.playback

import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import java.util.Locale
import indie.fiatsiz.anitv.TranslationSettings
import indie.fiatsiz.anitv.TranslationProvider
import indie.fiatsiz.anitv.CaptionFont
import indie.fiatsiz.anitv.CaptionColor
import indie.fiatsiz.anitv.CaptionStyleSettings
import indie.fiatsiz.anitv.captionSizes
import indie.fiatsiz.anitv.R

internal enum class CaptionMode { OFF, ORIGINAL, TRANSLATED }
internal enum class CaptionMenu { CLOSED, OPTIONS, LANGUAGES, STYLES, FONT, SIZE, BACKGROUND, FOREGROUND }

private val captionLanguages =
    listOf("ar", "de", "en", "es", "fr", "id", "it", "ja", "ko", "nl", "pl", "pt", "ru", "tr", "uk")
private val captionBackgroundColors =
    listOf(CaptionColor.BLACK, CaptionColor.BLUE, CaptionColor.WHITE)
private val captionForegroundColors =
    listOf(CaptionColor.WHITE, CaptionColor.YELLOW, CaptionColor.CYAN, CaptionColor.BLACK)

private fun captionFontLabel(font: CaptionFont) = when (font) {
    CaptionFont.SANS -> R.string.caption_font_sans
    CaptionFont.SERIF -> R.string.caption_font_serif
    CaptionFont.MONOSPACE -> R.string.caption_font_monospace
}

private fun captionColorLabel(color: CaptionColor) = when (color) {
    CaptionColor.BLACK -> R.string.caption_color_black
    CaptionColor.WHITE -> R.string.caption_color_white
    CaptionColor.YELLOW -> R.string.caption_color_yellow
    CaptionColor.BLUE -> R.string.caption_color_blue
    CaptionColor.CYAN -> R.string.caption_color_cyan
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun styleCaptionView(view: SubtitleView, style: CaptionStyleSettings) {
    val typeface = when (style.font) {
        CaptionFont.SANS -> Typeface.SANS_SERIF
        CaptionFont.SERIF -> Typeface.SERIF
        CaptionFont.MONOSPACE -> Typeface.MONOSPACE
    }
    view.setApplyEmbeddedStyles(false)
    view.setStyle(
        CaptionStyleCompat(
            style.foreground.argb, style.backgroundArgb,
            android.graphics.Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE,
            android.graphics.Color.TRANSPARENT, typeface
        )
    )
    view.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 24f * style.sizePercent / 100f)
}

@Composable
internal fun CaptionSettings(
    captionMenu: CaptionMenu,
    captionMode: CaptionMode,
    translation: TranslationSettings,
    effectiveTarget: String,
    captionFocus: androidx.compose.ui.focus.FocusRequester,
    languageFocus: androidx.compose.ui.focus.FocusRequester,
    styleFocus: androidx.compose.ui.focus.FocusRequester,
    choiceFocus: androidx.compose.ui.focus.FocusRequester,
    modifier: Modifier = Modifier,
    onMenu: (CaptionMenu) -> Unit,
    onMode: (CaptionMode) -> Unit,
    onTranslationSettings: (TranslationSettings) -> Unit
) {
    val displayLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val captionStyle = translation.captionStyle
    val translationReady =
        translation.provider != TranslationProvider.OFF && translation.apiKey.isNotBlank()
    var lastEnabledMode by remember { mutableStateOf(CaptionMode.ORIGINAL) }
    val captionsEnabled = captionMode != CaptionMode.OFF
    Column(
        modifier
            .width(320.dp)
            .background(Color(0xEE0F0F23))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (captionMenu) {
            CaptionMenu.OPTIONS -> {
                Text(
                    stringResource(R.string.captions_settings),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = {
                        if (captionsEnabled) {
                            lastEnabledMode = captionMode
                            onMode(CaptionMode.OFF)
                        } else {
                            onMode(if (lastEnabledMode == CaptionMode.TRANSLATED && !translationReady) CaptionMode.ORIGINAL else lastEnabledMode)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(captionFocus)
                        .semantics { selected = captionsEnabled }) {
                    Text(stringResource(if (captionsEnabled) R.string.captions_on else R.string.captions_off))
                }
                Button(
                    onClick = { onMode(CaptionMode.ORIGINAL); onMenu(CaptionMenu.CLOSED) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { selected = captionMode == CaptionMode.ORIGINAL }) {
                    Text(stringResource(R.string.captions_original))
                }
                Button(
                    onClick = { onMode(CaptionMode.TRANSLATED); onMenu(CaptionMenu.CLOSED) },
                    enabled = translationReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { selected = captionMode == CaptionMode.TRANSLATED }) {
                    Text(
                        stringResource(
                            R.string.captions_translate,
                            stringResource(if (translation.provider == TranslationProvider.DEEPL) R.string.translation_deepl else R.string.translation_google)
                        )
                    )
                }
                if (!translationReady) Text(
                    stringResource(R.string.translation_setup_hint),
                    color = Color(0xFFB9C5D8),
                    fontSize = 14.sp
                )
                Button(
                    onClick = { onMenu(CaptionMenu.LANGUAGES) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.captions_language,
                            Locale.forLanguageTag(effectiveTarget).getDisplayLanguage(displayLocale)
                        )
                    )
                }
                Button(
                    onClick = { onMenu(CaptionMenu.STYLES) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.caption_style))
                }
            }

            CaptionMenu.LANGUAGES -> {
                Text(
                    stringResource(R.string.captions_language_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    Modifier
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    captionLanguages.forEach { language ->
                        Button(
                            onClick = {
                                onTranslationSettings(translation.copy(targetLanguage = language))
                                onMenu(CaptionMenu.OPTIONS)
                            }, modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (language == effectiveTarget || (language == captionLanguages.first() && effectiveTarget !in captionLanguages)) Modifier.focusRequester(
                                        languageFocus
                                    ) else Modifier
                                )
                                .semantics { selected = language == effectiveTarget }) {
                            Text(Locale.forLanguageTag(language).getDisplayLanguage(displayLocale))
                        }
                    }
                }
            }

            CaptionMenu.STYLES -> {
                Text(
                    stringResource(R.string.caption_style),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = { onMenu(CaptionMenu.FONT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(styleFocus)
                ) {
                    Text(
                        stringResource(
                            R.string.caption_font,
                            stringResource(captionFontLabel(captionStyle.font))
                        )
                    )
                }
                Button(onClick = { onMenu(CaptionMenu.SIZE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.caption_size, captionStyle.sizePercent))
                }
                Button(
                    onClick = { onMenu(CaptionMenu.BACKGROUND) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.caption_background,
                            stringResource(captionColorLabel(captionStyle.background))
                        )
                    )
                }
                Button(
                    onClick = { onMenu(CaptionMenu.FOREGROUND) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            R.string.caption_foreground,
                            stringResource(captionColorLabel(captionStyle.foreground))
                        )
                    )
                }
            }

            CaptionMenu.FONT -> {
                Text(
                    stringResource(R.string.caption_font_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                CaptionFont.entries.forEach { font ->
                    Button(
                        onClick = {
                            onTranslationSettings(
                                translation.copy(
                                    captionStyle = captionStyle.copy(
                                        font = font
                                    )
                                )
                            )
                            onMenu(CaptionMenu.STYLES)
                        }, modifier = Modifier
                            .fillMaxWidth()
                            .then(if (font == captionStyle.font) Modifier.focusRequester(choiceFocus) else Modifier)
                            .semantics { selected = font == captionStyle.font }) {
                        Text(stringResource(captionFontLabel(font)))
                    }
                }
            }

            CaptionMenu.SIZE -> {
                Text(
                    stringResource(R.string.caption_size_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                captionSizes.forEach { size ->
                    Button(
                        onClick = {
                            onTranslationSettings(
                                translation.copy(
                                    captionStyle = captionStyle.copy(
                                        sizePercent = size
                                    )
                                )
                            )
                            onMenu(CaptionMenu.STYLES)
                        }, modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (size == captionStyle.sizePercent) Modifier.focusRequester(
                                    choiceFocus
                                ) else Modifier
                            )
                            .semantics {
                                selected = size == captionStyle.sizePercent
                            }) { Text("$size%") }
                }
            }

            CaptionMenu.BACKGROUND, CaptionMenu.FOREGROUND -> {
                val background = captionMenu == CaptionMenu.BACKGROUND
                Text(
                    stringResource(if (background) R.string.caption_background_title else R.string.caption_foreground_title),
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                )
                val selectedColor =
                    if (background) captionStyle.background else captionStyle.foreground
                (if (background) captionBackgroundColors else captionForegroundColors).forEach { color ->
                    Button(
                        onClick = {
                            val next =
                                if (background) captionStyle.copy(background = color) else captionStyle.copy(
                                    foreground = color
                                )
                            onTranslationSettings(translation.copy(captionStyle = next))
                            onMenu(CaptionMenu.STYLES)
                        }, modifier = Modifier
                            .fillMaxWidth()
                            .then(if (color == selectedColor) Modifier.focusRequester(choiceFocus) else Modifier)
                            .semantics { selected = color == selectedColor }) {
                        Text(stringResource(captionColorLabel(color)))
                    }
                }
            }

            CaptionMenu.CLOSED -> Unit
        }
    }
}
