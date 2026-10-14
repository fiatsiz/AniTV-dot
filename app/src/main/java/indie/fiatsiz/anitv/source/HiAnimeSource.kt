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
enum class AudioVersion { SUB, DUB }
data class EpisodeServer(val audio: AudioVersion, val embedUrl: String)
data class Stream(
    val url: String,
    val subtitleUrl: String?,
    val referer: String,
    val audio: AudioVersion = AudioVersion.SUB
)

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

enum class SourceFailure {
    DETAIL_MISSING, DETAIL_LINK_INVALID, EPISODES_LOAD, SERVERS_LOAD, NO_SUPPORTED_SERVER,
    SERVER_LINK_CHANGED, REQUEST_BLOCKED, REQUEST_FAILED, RESPONSE_TOO_LARGE, NETWORK,
    SEARCH_BLOCKED, SEARCH_CHANGED, DETAIL_CHANGED, DESCRIPTION_CHANGED, NO_EPISODES,
    PLAYER_PAGE_CHANGED, PLAYER_STREAM_CHANGED, NO_SUBTITLE, INSECURE_LINK
}

class SourceException(val failure: SourceFailure, val status: Int = 0) : IOException(failure.name)

class HiAnimeSource(
    private val userAgent: String,
    private val rulesProvider: () -> SourceRules = { SourceRules.bundled },
    private val request: ((String, String, Int) -> ByteArray)? = null
) {
    private val base = "https://hianime.at"

    fun search(query: String): List<Anime> {
        val rules = rulesProvider()
        return search(query, rules)
    }

    private fun search(query: String, rules: SourceRules): List<Anime> {
        val endpoint =
            rules.search.endpoint.replace("{query}", URLEncoder.encode(query.trim(), "UTF-8"))
        return parseSearch(get(base + endpoint), rules)
    }

    fun details(anime: Anime): AnimeDetails {
        val rules = rulesProvider()
        val url = anime.detailUrl ?: search(
            anime.title,
            rules
        ).firstOrNull { it.id == anime.id }?.detailUrl
        ?: throw SourceException(SourceFailure.DETAIL_MISSING)
        if (!runCatching { URI(url).let { it.scheme == "https" && it.host == "hianime.at" } }.getOrDefault(
                false
            )
        )
            throw SourceException(SourceFailure.DETAIL_LINK_INVALID)
        return parseDetails(get(url), rules)
    }

    fun poster(url: String): ByteArray {
        rulesProvider()
        return getBytes(url, maxBytes = 8_000_000)
    }

    fun subtitleCues(stream: Stream): List<SubtitleCue> {
        val subtitle = stream.subtitleUrl ?: throw SourceException(SourceFailure.NO_SUBTITLE)
        rulesProvider()
        return parseVtt(get(subtitle, stream.referer))
    }

    fun episodes(anime: Anime): List<Episode> {
        val rules = rulesProvider()
        requireNumericId(anime.id)
        val body = get(base + rules.episodes.endpoint.replace("{animeId}", anime.id))
        return parseEpisodes(rules.episodes.response.html(body, SourceFailure.EPISODES_LOAD), rules)
    }

    fun servers(episode: Episode): List<EpisodeServer> {
        val rules = rulesProvider()
        requireNumericId(episode.id)
        val body = get(base + rules.servers.endpoint.replace("{episodeId}", episode.id))
        return parseServers(rules.servers.response.html(body, SourceFailure.SERVERS_LOAD), rules)
    }

    fun stream(server: EpisodeServer): Stream {
        val rules = rulesProvider()
        requireHttps(server.embedUrl)
        val player = get(server.embedUrl, "$base/")
        return parsePlayer(
            player,
            URI(server.embedUrl).let { "${it.scheme}://${it.authority}/" },
            server.audio,
            rules
        )
    }

    private fun get(url: String, referer: String = "$base/"): String =
        String(getBytes(url, referer), StandardCharsets.UTF_8)

    private fun getBytes(
        url: String,
        referer: String = "$base/",
        maxBytes: Int = 8_000_000
    ): ByteArray {
        requireHttps(url)
        request?.let { return it(url, referer, maxBytes) }
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", referer)
        }
        try {
            val code = connection.responseCode
            if (code == 403 || code == 429 || code == 503) throw SourceException(
                SourceFailure.REQUEST_BLOCKED,
                code
            )
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
        private fun requireNumericId(id: String) {
            if (!id.matches(Regex("[0-9]+"))) throw SourceException(SourceFailure.DETAIL_LINK_INVALID)
        }

        fun parseVtt(vtt: String): List<SubtitleCue> =
            vtt.replace("\r", "").split("\n\n").mapNotNull { block ->
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

        fun parseSearch(html: String, rules: SourceRules = SourceRules.bundled): List<Anime> {
            if (html.contains("Just a moment", ignoreCase = true)) throw SourceException(
                SourceFailure.SEARCH_BLOCKED
            )
            val config = rules.search
            val document = Jsoup.parse(config.response.html(html, SourceFailure.SEARCH_CHANGED))
            val results = document.select(config.items).mapNotNull { item ->
                val title = config.title.read(item)
                val id = config.id.read(item)
                val url = config.link.read(item).takeIf { it.isNotBlank() }
                    ?.let {
                        runCatching {
                            URI("https://hianime.at").resolve(it).toString()
                        }.getOrNull()
                    }
                    ?.takeIf {
                        runCatching { URI(it).let { uri -> uri.scheme == "https" && uri.host == "hianime.at" } }.getOrDefault(
                            false
                        )
                    }
                if (title.isBlank() || !id.matches(Regex("[0-9]+"))) null else Anime(id, title, url)
            }
            if (results.isEmpty() && document.selectFirst(config.container) == null)
                throw SourceException(SourceFailure.SEARCH_CHANGED)
            return results
        }

        fun parseDetails(html: String, rules: SourceRules = SourceRules.bundled): AnimeDetails {
            val config = rules.detail
            val detail = Jsoup.parse(config.response.html(html, SourceFailure.DETAIL_CHANGED))
                .selectFirst(config.root)
                ?: throw SourceException(SourceFailure.DETAIL_CHANGED)
            val description = config.description.read(detail)
            val poster = config.poster.read(detail)
            requireHttps(poster)
            if (description.isBlank()) throw SourceException(SourceFailure.DESCRIPTION_CHANGED)
            val facts = detail.select(config.facts).mapNotNull { item ->
                val name = item.selectFirst(config.factName)?.text()?.trimEnd(':')
                    ?: return@mapNotNull null
                if (name !in config.factNames) return@mapNotNull null
                val value = item.text().removePrefix("$name:").trim()
                if (value.isBlank()) null else "$name: $value"
            }
            return AnimeDetails(description, poster, facts)
        }

        fun parseEpisodes(html: String, rules: SourceRules = SourceRules.bundled): List<Episode> {
            val document = Jsoup.parse(html)
            val episodes = document.select(rules.episodes.items).mapNotNull { item ->
                val id = item.attr(rules.episodes.idAttribute)
                val number = item.attr(rules.episodes.numberAttribute)
                if (!id.matches(Regex("[0-9]+")) || number.isBlank()) null
                else Episode(
                    id,
                    number,
                    item.attr(rules.episodes.titleAttribute).ifBlank { "Episode $number" })
            }
            if (episodes.isEmpty()) throw SourceException(SourceFailure.NO_EPISODES)
            return episodes
        }

        fun parseServers(
            html: String,
            rules: SourceRules = SourceRules.bundled
        ): List<EpisodeServer> {
            val document = Jsoup.parse(html)
            val servers = AudioVersion.entries.mapNotNull { audio ->
                val config = rules.servers
                val type = if (audio == AudioVersion.SUB) config.sub else config.dub
                val server = document.select(config.items).firstOrNull {
                    it.attr(config.audioAttribute) == type && it.attr(config.nameAttribute) == config.name
                } ?: return@mapNotNull null
                val embed = try {
                    val value = server.attr(config.embedAttribute)
                    if (config.base64) String(
                        Base64.getDecoder().decode(value),
                        StandardCharsets.UTF_8
                    ) else value
                } catch (_: IllegalArgumentException) {
                    throw SourceException(SourceFailure.SERVER_LINK_CHANGED)
                }
                requireHttps(embed)
                EpisodeServer(audio, embed)
            }
            if (servers.isEmpty()) throw SourceException(SourceFailure.NO_SUPPORTED_SERVER)
            return servers
        }

        fun parsePlayer(
            html: String, referer: String, audio: AudioVersion = AudioVersion.SUB,
            rules: SourceRules = SourceRules.bundled
        ): Stream {
            val config = rules.player
            val match =
                Regex(Regex.escape(config.variable) + "\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)')").find(
                    html
                )
                    ?: throw SourceException(SourceFailure.PLAYER_PAGE_CHANGED)
            val blob = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val data = try {
                val bytes =
                    if (config.base64) Base64.getDecoder().decode(blob) else blob.toByteArray(
                        StandardCharsets.UTF_8
                    )
                val clear = if (config.xor) ByteArray(bytes.size) { i ->
                    (bytes[i].toInt() xor config.key[i % config.key.length].code).toByte()
                } else bytes
                JSONObject(String(clear, StandardCharsets.UTF_8))
            } catch (_: Exception) {
                throw SourceException(SourceFailure.PLAYER_STREAM_CHANGED)
            }
            val url = jsonValue(data, config.streamPath) as? String ?: throw SourceException(
                SourceFailure.PLAYER_STREAM_CHANGED
            )
            requireHttps(url)
            val tracks = jsonValue(data, config.subtitlesPath) as? org.json.JSONArray
            val captions =
                (0 until (tracks?.length() ?: 0)).mapNotNull { tracks?.optJSONObject(it) }
                    .filter { isHttps(jsonValue(it, config.subtitleUrlPath) as? String ?: "") }
            val subtitle =
                (captions.firstOrNull { jsonValue(it, config.subtitleDefaultPath) == true }
                    ?: captions.firstOrNull())?.let {
                    jsonValue(
                        it,
                        config.subtitleUrlPath
                    ) as String
                }
            if (subtitle == null && audio == AudioVersion.SUB) throw SourceException(SourceFailure.NO_SUBTITLE)
            return Stream(url, subtitle, referer, audio)
        }

        private fun isHttps(url: String) = runCatching {
            URI(url).let { it.scheme == "https" && it.host != null && it.userInfo == null }
        }.getOrDefault(false)

        private fun requireHttps(url: String) {
            if (isHttps(url)) return
            throw SourceException(SourceFailure.INSECURE_LINK)
        }
    }
}
