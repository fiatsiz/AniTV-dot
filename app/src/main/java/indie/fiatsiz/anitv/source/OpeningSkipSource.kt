package indie.fiatsiz.anitv.source

import indie.fiatsiz.anitv.AniTVApplication
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToLong

data class OpeningInterval(val startMs: Long, val endMs: Long)

/** Independent AniSkip integration; ani-skip is a behavior reference only. */
class OpeningSkipSource(
    private val userAgent: String,
    private val request: ((String) -> String)? = null
) {
    fun opening(anime: Anime, episode: Episode, durationMs: Long): OpeningInterval? {
        if (durationMs <= 0 || !episode.number.matches(Regex("[0-9]+(?:\\.[0-9]+)?")) ||
            episode.number.toDoubleOrNull()?.isFinite() != true
        ) return null
        val titles = listOfNotNull(anime.japaneseTitle, anime.title).filter { it.isNotBlank() }
        if (titles.isEmpty()) return null
        val query = URLEncoder.encode(titles.first(), "UTF-8")
        val malId = matchingMalId(
            get("https://myanimelist.net/search/prefix.json?type=anime&keyword=$query&v=1"), titles
        ) ?: return null
        return parseOpening(
            get("https://api.aniskip.com/v2/skip-times/$malId/${episode.number}?types[]=op&episodeLength=${durationMs / 1000.0}"),
            durationMs
        )
    }

    private fun get(url: String): String {
        request?.let { return it(url) }
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent).header("Accept", "application/json").build()
        AniTVApplication.openingHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Opening lookup unavailable")
            return response.body.byteStream().use {
                val bytes = readLimited(it, 256 * 1024 + 1)
                if (bytes.size > 256 * 1024) throw IOException("Opening response too large")
                bytes.toString(Charsets.UTF_8)
            }
        }
    }

    companion object {
        fun matchingMalId(json: String, titles: List<String>): Int? {
            fun normalize(value: String) =
                value.trim().replace(Regex("\\s+"), " ").lowercase(java.util.Locale.ROOT)

            val names = titles.map(::normalize).filter { it.isNotEmpty() }.toSet()
            val categories = JSONObject(json).getJSONArray("categories")
            val matches = mutableSetOf<Int>()
            for (index in 0 until categories.length()) {
                val category = categories.getJSONObject(index)
                if (category.optString("type") != "anime") continue
                val items = category.getJSONArray("items")
                for (itemIndex in 0 until items.length()) {
                    val item = items.getJSONObject(itemIndex)
                    if (item.optString("type") != "anime" || normalize(item.optString("name")) !in names) continue
                    val id = (item.opt("id") as? Number)?.toString()?.toIntOrNull()
                    if (id != null && id > 0) matches.add(id)
                }
            }
            // shortcut: exact names only; add explicit title selection if aliases cannot be matched safely.
            return matches.singleOrNull()
        }

        fun parseOpening(json: String, durationMs: Long): OpeningInterval? {
            if (durationMs <= 0) return null
            val root = JSONObject(json)
            if (root.getBoolean("found") == false) return null
            val results = root.getJSONArray("results")
            val intervals = mutableSetOf<OpeningInterval>()
            for (index in 0 until results.length()) {
                val result = results.optJSONObject(index) ?: continue
                if (result.optString("skipType") != "op") continue
                val interval = result.optJSONObject("interval") ?: continue
                val start = (interval.opt("startTime") as? Number)?.toDouble() ?: continue
                val end = (interval.opt("endTime") as? Number)?.toDouble() ?: continue
                val length = (result.opt("episodeLength") as? Number)?.toDouble() ?: continue
                if (!start.isFinite() || !end.isFinite() || !length.isFinite() || start < 0 ||
                    end <= start || end * 1000 >= durationMs ||
                    abs(length * 1000 - durationMs) > 30_000
                ) continue
                intervals.add(
                    OpeningInterval(
                        (start * 1000).roundToLong(),
                        (end * 1000).roundToLong()
                    )
                )
            }
            return intervals.singleOrNull()
        }
    }
}
