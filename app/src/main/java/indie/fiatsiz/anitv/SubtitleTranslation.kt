package indie.fiatsiz.anitv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class TranslationProvider { OFF, GOOGLE, DEEPL }
enum class CaptionFont { SANS, SERIF, MONOSPACE }
enum class CaptionColor(val argb: Int) {
    BLACK(0xFF000000.toInt()), WHITE(0xFFFFFFFF.toInt()), YELLOW(0xFFFFFF00.toInt()), BLUE(
        0xFF123B8A.toInt()
    ),
    CYAN(0xFF00FFFF.toInt())
}

internal val captionSizes = listOf(50, 75, 100, 150, 200)
internal fun captionSizePercent(value: Int) = value.takeIf { it in captionSizes } ?: 100

data class CaptionStyleSettings(
    val font: CaptionFont = CaptionFont.SANS,
    val sizePercent: Int = 100,
    val background: CaptionColor = CaptionColor.BLACK,
    val foreground: CaptionColor = CaptionColor.WHITE
) {
    val backgroundArgb: Int get() = 0xCC000000.toInt() or (background.argb and 0x00FFFFFF)
}

data class TranslationSettings(
    val provider: TranslationProvider = TranslationProvider.OFF,
    val apiKey: String = "",
    val targetLanguage: String = "",
    val captionStyle: CaptionStyleSettings = CaptionStyleSettings()
)

internal fun subtitleBatchEnd(start: Int, cueCount: Int) = minOf(start + 4, cueCount)
internal fun shouldPrefetchNextBatch(currentCue: Int, translatedThrough: Int) =
    currentCue >= translatedThrough - 2

internal fun deeplTargetLanguage(language: String) = when (language.lowercase()) {
    "en" -> "EN-US"
    "pt" -> "PT-BR"
    else -> language.uppercase()
}

class TranslationSettingsStore(context: Context) {
    private val preferences =
        context.getSharedPreferences("subtitle_translation", Context.MODE_PRIVATE)

    fun read() = TranslationSettings(
        runCatching {
            TranslationProvider.valueOf(
                preferences.getString(
                    "provider",
                    "OFF"
                )!!
            )
        }.getOrDefault(TranslationProvider.OFF),
        runCatching { decrypt(preferences.getString("api_key", "").orEmpty()) }.getOrDefault(""),
        preferences.getString("target_language", "").orEmpty(),
        CaptionStyleSettings(
            runCatching {
                CaptionFont.valueOf(
                    preferences.getString(
                        "caption_font",
                        "SANS"
                    )!!
                )
            }.getOrDefault(CaptionFont.SANS),
            captionSizePercent(preferences.getInt("caption_size", 100)),
            runCatching {
                CaptionColor.valueOf(
                    preferences.getString(
                        "caption_background",
                        "BLACK"
                    )!!
                )
            }.getOrDefault(CaptionColor.BLACK),
            runCatching {
                CaptionColor.valueOf(
                    preferences.getString(
                        "caption_foreground",
                        "WHITE"
                    )!!
                )
            }.getOrDefault(CaptionColor.WHITE)
        )
    )

    fun save(settings: TranslationSettings) {
        preferences.edit().putString("provider", settings.provider.name)
            .putString("api_key", encrypt(settings.apiKey))
            .putString("target_language", settings.targetLanguage)
            .putString("caption_font", settings.captionStyle.font.name)
            .putInt("caption_size", captionSizePercent(settings.captionStyle.sizePercent))
            .putString("caption_background", settings.captionStyle.background.name)
            .putString("caption_foreground", settings.captionStyle.foreground.name).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    private fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, payload.copyOfRange(0, 12))
        )
        return String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("anitv-caption-translation", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    "anitv-caption-translation",
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()
            )
        }.generateKey()
    }
}

class SubtitleTranslator {
    fun translate(
        provider: TranslationProvider,
        apiKey: String,
        targetLanguage: String,
        texts: List<String>
    ): List<String> {
        require(provider != TranslationProvider.OFF && apiKey.isNotBlank() && texts.isNotEmpty())
        val deepl = provider == TranslationProvider.DEEPL
        val endpoint = if (deepl) {
            val host = if (apiKey.endsWith(
                    ":fx",
                    ignoreCase = true
                )
            ) "api-free.deepl.com" else "api.deepl.com"
            "https://$host/v2/translate"
        } else "https://translation.googleapis.com/language/translate/v2?key=${
            URLEncoder.encode(
                apiKey,
                "UTF-8"
            )
        }"
        val body = if (deepl) JSONObject().put("text", JSONArray(texts))
            .put("target_lang", deeplTargetLanguage(targetLanguage))
        else JSONObject().put("q", JSONArray(texts)).put("target", targetLanguage.lowercase())
            .put("format", "text")
        val connection = (URI(endpoint).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (deepl) setRequestProperty("Authorization", "DeepL-Auth-Key $apiKey")
        }
        try {
            connection.outputStream.use {
                it.write(
                    body.toString().toByteArray(StandardCharsets.UTF_8)
                )
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Translation request failed ($status)")
            val response = connection.inputStream.use { input ->
                val bytes = indie.fiatsiz.anitv.source.readLimited(input, 1_000_001)
                if (bytes.size > 1_000_000) throw IOException("Translation response too large")
                JSONObject(String(bytes, StandardCharsets.UTF_8))
            }
            val translated = if (deepl) response.getJSONArray("translations").let { array ->
                (0 until array.length()).map { array.getJSONObject(it).getString("text") }
            } else response.getJSONObject("data").getJSONArray("translations").let { array ->
                (0 until array.length()).map {
                    Jsoup.parseBodyFragment(
                        array.getJSONObject(it).getString("translatedText")
                    ).text()
                }
            }
            if (translated.size != texts.size) throw IOException("Incomplete translation response")
            return translated
        } finally {
            connection.disconnect()
        }
    }
}
