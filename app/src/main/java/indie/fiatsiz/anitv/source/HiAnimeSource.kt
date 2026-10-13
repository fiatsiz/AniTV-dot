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
data class AnimeDetails(val description: String, val posterUrl: String, val facts: List<String>)
data class Episode(val id: String, val number: String, val title: String)
data class Stream(val url: String, val subtitleUrl: String, val referer: String)
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

enum class SourceFailure {
    DETAIL_MISSING, DETAIL_LINK_INVALID, EPISODES_LOAD, SERVERS_LOAD, NO_SUB_SERVER,
    SERVER_LINK_CHANGED, REQUEST_BLOCKED, REQUEST_FAILED, RESPONSE_TOO_LARGE, NETWORK,
    SEARCH_BLOCKED, SEARCH_CHANGED, DETAIL_CHANGED, DESCRIPTION_CHANGED, NO_EPISODES,
    PLAYER_PAGE_CHANGED, PLAYER_STREAM_CHANGED, NO_SUBTITLE, INSECURE_LINK
}

class SourceException(val failure: SourceFailure, val status: Int = 0) : IOException(failure.name)

class HiAnimeSource(private val userAgent: String) {
    private val base = "https://hianime.at"

    fun search(query: String): List<Anime> {
        val html = get("$base/search?keyword=${URLEncoder.encode(query.trim(), "UTF-8")}")
        return parseSearch(html)
    }

    fun details(anime: Anime): AnimeDetails {
        val url = anime.detailUrl ?: search(anime.title).firstOrNull { it.id == anime.id }?.detailUrl
            ?: throw SourceException(SourceFailure.DETAIL_MISSING)
        if (!runCatching { URI(url).let { it.scheme == "https" && it.host == "hianime.at" } }.getOrDefault(false))
            throw SourceException(SourceFailure.DETAIL_LINK_INVALID)
        return parseDetails(get(url))
    }

    fun poster(url: String): ByteArray = getBytes(url, maxBytes = 8_000_000)

    fun subtitleCues(stream: Stream): List<SubtitleCue> = parseVtt(get(stream.subtitleUrl, stream.referer))

    fun episodes(anime: Anime): List<Episode> {
        val json = JSONObject(get("$base/api/theme/episode/list/${anime.id}"))
        if (!json.optBoolean("status")) throw SourceException(SourceFailure.EPISODES_LOAD)
        return parseEpisodes(json.getString("html"))
    }

    fun stream(episode: Episode): Stream {
        val json = JSONObject(get("$base/api/theme/episode/servers?episodeId=${episode.id}"))
        if (!json.optBoolean("status")) throw SourceException(SourceFailure.SERVERS_LOAD)
        val server = Jsoup.parse(json.getString("html"))
            .select(".server-item[data-type=sub][data-server-name=ZokoAnime]").firstOrNull()
            ?: throw SourceException(SourceFailure.NO_SUB_SERVER)
        val embed = try {
            String(Base64.getDecoder().decode(server.attr("data-hash")), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw SourceException(SourceFailure.SERVER_LINK_CHANGED)
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
            if (code == 403 || code == 429 || code == 503) throw SourceException(SourceFailure.REQUEST_BLOCKED, code)
            if (code !in 200..299) throw SourceException(SourceFailure.REQUEST_FAILED, code)
            requireHttps(connection.url.toString())
            return connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > maxBytes) throw SourceException(SourceFailure.RESPONSE_TOO_LARGE)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (e: SourceException) {
            throw e
        } catch (_: IOException) {
            throw SourceException(SourceFailure.NETWORK)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val KEY = "otaku-embed-v1"

        fun parseVtt(vtt: String): List<SubtitleCue> = vtt.replace("\r", "").split("\n\n").mapNotNull { block ->
            val lines = block.lines()
            val timing = lines.indexOfFirst { it.contains(" --> ") }
            if (timing < 0 || timing + 1 >= lines.size) return@mapNotNull null
            val times = lines[timing].split(" --> ", limit = 2)
            if (times.size != 2) return@mapNotNull null
            fun parseTime(value: String): Long? {
                val parts = value.substringBefore(' ').split(':')
                if (parts.size !in 2..3) return null
                val seconds = parts.last().split('.')
                val wholeSeconds = seconds[0].toLongOrNull() ?: return null
                val millis = seconds.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
                val minutes = parts[parts.lastIndex - 1].toLongOrNull() ?: return null
                val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
                return ((hours * 60 + minutes) * 60 + wholeSeconds) * 1000 + millis
            }
            val start = parseTime(times[0]) ?: return@mapNotNull null
            val end = parseTime(times[1]) ?: return@mapNotNull null
            val text = Jsoup.parse(lines.drop(timing + 1).joinToString(" ")).text().trim()
            if (end <= start || text.isEmpty()) null else SubtitleCue(start, end, text)
        }

        fun parseSearch(html: String): List<Anime> {
            if (html.contains("Just a moment", ignoreCase = true)) throw SourceException(SourceFailure.SEARCH_BLOCKED)
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
                throw SourceException(SourceFailure.SEARCH_CHANGED)
            return results
        }

        fun parseDetails(html: String): AnimeDetails {
            val detail = Jsoup.parse(html).selectFirst("#ani_detail")
                ?: throw SourceException(SourceFailure.DETAIL_CHANGED)
            val description = detail.selectFirst(".film-description .text")?.text().orEmpty()
            val poster = detail.selectFirst(".anisc-poster img[src]")?.attr("src").orEmpty()
            requireHttps(poster)
            if (description.isBlank()) throw SourceException(SourceFailure.DESCRIPTION_CHANGED)
            val facts = detail.select(".anisc-info .item").mapNotNull { item ->
                val name = item.selectFirst(".item-head")?.text()?.trimEnd(':') ?: return@mapNotNull null
                if (name !in setOf("Aired", "Status", "Genres", "MAL Score")) return@mapNotNull null
                val value = item.text().removePrefix("$name:").trim()
                if (value.isBlank()) null else "$name: $value"
            }
            return AnimeDetails(description, poster, facts)
        }

        fun parseEpisodes(html: String): List<Episode> {
            val document = Jsoup.parse(html)
            val episodes = document.select("a.ep-item[data-id]").mapNotNull { item ->
                val id = item.attr("data-id")
                val number = item.attr("data-number")
                if (!id.matches(Regex("[0-9]+")) || number.isBlank()) null
                else Episode(id, number, item.attr("title").ifBlank { "Episode $number" })
            }
            if (episodes.isEmpty()) throw SourceException(SourceFailure.NO_EPISODES)
            return episodes
        }

        fun parsePlayer(html: String, referer: String): Stream {
            val blob = Regex("window\\.__P=\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw SourceException(SourceFailure.PLAYER_PAGE_CHANGED)
            val data = try {
                val bytes = Base64.getDecoder().decode(blob)
                val clear = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor KEY[i % KEY.length].code).toByte() }
                JSONObject(String(clear, StandardCharsets.UTF_8))
            } catch (_: Exception) {
                throw SourceException(SourceFailure.PLAYER_STREAM_CHANGED)
            }
            val url = data.optString("src")
            requireHttps(url)
            val tracks = data.optJSONArray("subtitles")
            val subtitle = (0 until (tracks?.length() ?: 0)).map { tracks!!.getJSONObject(it) }
                .firstOrNull { it.optBoolean("default") && it.optString("src").startsWith("https://") }
                ?.optString("src")
                ?: (0 until (tracks?.length() ?: 0)).map { tracks!!.getJSONObject(it) }
                    .firstOrNull { it.optString("src").startsWith("https://") }?.optString("src")
            if (subtitle == null) throw SourceException(SourceFailure.NO_SUBTITLE)
            return Stream(url, subtitle, referer)
        }

        private fun requireHttps(url: String) {
            if (runCatching { URI(url).scheme == "https" && URI(url).host != null }.getOrDefault(false)) return
            throw SourceException(SourceFailure.INSECURE_LINK)
        }
    }
}
