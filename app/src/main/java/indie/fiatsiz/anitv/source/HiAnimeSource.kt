package indie.fiatsiz.anitv.source

import org.jsoup.Jsoup
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class Anime(val id: String, val title: String, val detailUrl: String? = null)
data class AnimeDetails(val description: String, val bannerUrl: String, val facts: List<String>)
data class Episode(val id: String, val number: String, val title: String)
data class Stream(val url: String, val subtitleUrl: String, val referer: String)

class SourceException(message: String) : IOException(message)

class HiAnimeSource {
    private val base = "https://hianime.at"
    private val userAgent = "Mozilla/5.0 (Linux; Android 14; TV) AppleWebKit/537.36 Chrome/120.0 Safari/537.36"

    fun search(query: String): List<Anime> {
        val html = get("$base/search?keyword=${URLEncoder.encode(query.trim(), "UTF-8")}")
        return parseSearch(html)
    }

    fun details(anime: Anime): AnimeDetails {
        val url = anime.detailUrl ?: search(anime.title).firstOrNull { it.id == anime.id }?.detailUrl
            ?: throw SourceException("Could not find this anime's detail page. Search for it again.")
        if (!runCatching { URI(url).let { it.scheme == "https" && it.host == "hianime.at" } }.getOrDefault(false))
            throw SourceException("HiAnime returned an invalid detail link.")
        return parseDetails(get(url))
    }

    fun banner(url: String): ByteArray = getBytes(url, maxBytes = 8_000_000)

    fun episodes(anime: Anime): List<Episode> {
        val json = JSONObject(get("$base/api/theme/episode/list/${anime.id}"))
        if (!json.optBoolean("status")) throw SourceException("HiAnime could not load episodes.")
        return parseEpisodes(json.getString("html"))
    }

    fun stream(episode: Episode): Stream {
        val json = JSONObject(get("$base/api/theme/episode/servers?episodeId=${episode.id}"))
        if (!json.optBoolean("status")) throw SourceException("HiAnime could not load subtitle servers.")
        val server = Jsoup.parse(json.getString("html"))
            .select(".server-item[data-type=sub][data-server-name=ZokoAnime]").firstOrNull()
            ?: throw SourceException("No supported subtitled server is available for this episode.")
        val embed = try {
            String(Base64.getDecoder().decode(server.attr("data-hash")), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw SourceException("HiAnime changed its server link format.")
        }
        requireHttps(embed)
        val player = get(embed, "$base/")
        return parsePlayer(player, URI(embed).let { "${it.scheme}://${it.authority}/" })
    }

    private fun get(url: String, referer: String = "$base/"): String =
        String(getBytes(url, referer), StandardCharsets.UTF_8)

    private fun getBytes(url: String, referer: String = "$base/", maxBytes: Int = 8_000_000): ByteArray {
        requireHttps(url)
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", referer)
        }
        try {
            val code = connection.responseCode
            if (code == 403 || code == 429 || code == 503) throw SourceException("HiAnime or its player blocked the request ($code). Try again later.")
            if (code !in 200..299) throw SourceException("Source request failed ($code). The link may have expired.")
            requireHttps(connection.url.toString())
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw SourceException("Source response is too large.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (e: SourceException) {
            throw e
        } catch (_: IOException) {
            throw SourceException("Could not reach HiAnime or its player. Check the connection and try again.")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEY = "otaku-embed-v1"

        fun parseSearch(html: String): List<Anime> {
            if (html.contains("Just a moment", ignoreCase = true)) throw SourceException("HiAnime blocked the search request.")
            val document = Jsoup.parse(html)
            val results = document.select(".film_list-wrap .flw-item").mapNotNull { item ->
                val link = item.selectFirst(".film-poster-ahref[data-id]") ?: return@mapNotNull null
                val title = item.selectFirst(".film-name .dynamic-name")?.text().orEmpty()
                val id = link.attr("data-id")
                val url = item.selectFirst(".film-name .dynamic-name[href]")?.attr("href")
                    ?.let { runCatching { URI("https://hianime.at").resolve(it).toString() }.getOrNull() }
                    ?.takeIf { runCatching { URI(it).let { uri -> uri.scheme == "https" && uri.host == "hianime.at" } }.getOrDefault(false) }
                if (title.isBlank() || !id.matches(Regex("[0-9]+"))) null else Anime(id, title, url)
            }
            if (results.isEmpty() && document.selectFirst(".film_list-wrap") == null)
                throw SourceException("HiAnime changed its search page. Try again after an app update.")
            return results
        }

        fun parseDetails(html: String): AnimeDetails {
            val detail = Jsoup.parse(html).selectFirst("#ani_detail")
                ?: throw SourceException("HiAnime changed its anime detail page.")
            val description = detail.selectFirst(".film-description .text")?.text().orEmpty()
            val style = detail.selectFirst(".anis-cover")?.attr("style").orEmpty()
            val banner = Regex("url\\(['\"]?([^'\")]+)").find(style)?.groupValues?.get(1)
                ?: detail.selectFirst(".anisc-poster img")?.attr("src").orEmpty()
            requireHttps(banner)
            if (description.isBlank()) throw SourceException("HiAnime changed its anime description.")
            val facts = detail.select(".anisc-info .item").mapNotNull { item ->
                val name = item.selectFirst(".item-head")?.text()?.trimEnd(':') ?: return@mapNotNull null
                if (name !in setOf("Aired", "Status", "Genres", "MAL Score")) return@mapNotNull null
                val value = item.text().removePrefix("$name:").trim()
                if (value.isBlank()) null else "$name: $value"
            }
            return AnimeDetails(description, banner, facts)
        }

        fun parseEpisodes(html: String): List<Episode> {
            val document = Jsoup.parse(html)
            val episodes = document.select("a.ep-item[data-id]").mapNotNull { item ->
                val id = item.attr("data-id")
                val number = item.attr("data-number")
                if (!id.matches(Regex("[0-9]+")) || number.isBlank()) null
                else Episode(id, number, item.attr("title").ifBlank { "Episode $number" })
            }
            if (episodes.isEmpty()) throw SourceException("No episodes found. HiAnime may have changed its episode list.")
            return episodes
        }

        fun parsePlayer(html: String, referer: String): Stream {
            val blob = Regex("window\\.__P=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw SourceException("The subtitle player changed its page format.")
            val data = try {
                val bytes = Base64.getDecoder().decode(blob)
                val clear = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor KEY[i % KEY.length].code).toByte() }
                JSONObject(String(clear, StandardCharsets.UTF_8))
            } catch (_: Exception) {
                throw SourceException("The subtitle player changed its stream format.")
            }
            val url = data.optString("src")
            requireHttps(url)
            val tracks = data.optJSONArray("subtitles")
            val subtitle = (0 until (tracks?.length() ?: 0)).map { tracks!!.getJSONObject(it) }
                .firstOrNull { it.optBoolean("default") && it.optString("src").startsWith("https://") }
                ?.optString("src")
                ?: (0 until (tracks?.length() ?: 0)).map { tracks!!.getJSONObject(it) }
                    .firstOrNull { it.optString("src").startsWith("https://") }?.optString("src")
            if (subtitle == null) throw SourceException("This episode has no usable subtitle track.")
            return Stream(url, subtitle, referer)
        }

        private fun requireHttps(url: String) {
            if (runCatching { URI(url).scheme == "https" && URI(url).host != null }.getOrDefault(false)) return
            throw SourceException("Source returned an invalid or insecure link.")
        }
    }
}
