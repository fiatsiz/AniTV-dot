package indie.fiatsiz.anitv.source

import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

/** Schema 1 contains data only; network safety and parser algorithms remain compiled. */
data class SourceRules(
    val schemaVersion: Int, val revision: Long, val minAppVersionCode: Int,
    val search: SearchRules, val detail: DetailRules, val episodes: EpisodeRules,
    val servers: ServerRules, val player: PlayerRules
) {
    companion object {
        val bundled: SourceRules by lazy { SignedRules.bundled.rules }

        fun parse(bytes: ByteArray, appVersionCode: Int): SourceRules {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            fun integer(name: String): Long {
                val value = json.get(name)
                require(
                    value is Number && value.toString().matches(Regex("[0-9]+"))
                ) { "Invalid $name" }
                return value.toString().toLong()
            }

            val schema = integer("schemaVersion")
            require(schema == 1L) { "Unsupported rules schema" }
            val revision = integer("revision").also { require(it > 0) }
            val minimum =
                integer("minAppVersionCode").also { require(it in 1..appVersionCode.toLong()) }
            val s = json.getJSONObject("search")
            val d = json.getJSONObject("detail")
            val e = json.getJSONObject("episodes")
            val v = json.getJSONObject("servers")
            val p = json.getJSONObject("player")
            val rules = SourceRules(
                1, revision, minimum.toInt(),
                SearchRules(
                    endpoint(s.getString("endpoint"), "query"),
                    response(s),
                    selector(s, "container"),
                    selector(s, "items"),
                    field(s, "id"),
                    field(s, "title"),
                    field(s, "link")
                ),
                DetailRules(
                    response(d),
                    selector(d, "root"),
                    field(d, "description"),
                    field(d, "poster"),
                    selector(d, "facts"),
                    selector(d, "factName"),
                    strings(d, "factNames").also { require(it.isNotEmpty()) }),
                EpisodeRules(
                    endpoint(e.getString("endpoint"), "animeId"),
                    response(e),
                    selector(e, "items"),
                    attribute(e, "idAttribute"),
                    attribute(e, "numberAttribute"),
                    attribute(e, "titleAttribute")
                ),
                ServerRules(
                    endpoint(v.getString("endpoint"), "episodeId"),
                    response(v),
                    selector(v, "items"),
                    attribute(v, "audioAttribute"),
                    attribute(v, "nameAttribute"),
                    text(v, "name"),
                    text(v, "sub"),
                    text(v, "dub"),
                    attribute(v, "embedAttribute"),
                    v.getBoolean("base64")
                ),
                PlayerRules(
                    text(
                        p,
                        "variable"
                    ).also { require(it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))) },
                    p.getBoolean("base64"),
                    p.getBoolean("xor"),
                    p.getString("key"),
                    path(p, "streamPath"),
                    path(p, "subtitlesPath"),
                    path(p, "subtitleUrlPath"),
                    path(p, "subtitleDefaultPath")
                )
            )
            require(rules.servers.sub != rules.servers.dub)
            require(!rules.player.xor || rules.player.key.isNotEmpty())
            require(rules.player.key.length <= 256 && rules.player.key.all { it.code in 32..126 })
            return rules
        }

        private fun text(json: JSONObject, name: String) =
            json.getString(name).also { require(it.isNotBlank() && it.length <= 512) }

        private fun strings(json: JSONObject, name: String): List<String> {
            val array = json.getJSONArray(name)
            require(array.length() <= 32)
            return (0 until array.length()).map {
                array.getString(it)
                    .also { value -> require(value.isNotBlank() && value.length <= 128) }
            }
        }

        private fun selector(json: JSONObject, name: String) =
            text(json, name).also { Jsoup.parse("").select(it) }

        private fun attribute(json: JSONObject, name: String) =
            text(json, name).also { require(it.matches(Regex("[A-Za-z_:][A-Za-z0-9_:.-]*"))) }

        private fun field(json: JSONObject, name: String): HtmlField {
            val value = json.getJSONObject(name)
            return HtmlField(selector(value, "selector"), value.getString("attribute").let {
                if (it.isEmpty()) null else attribute(value, "attribute")
            })
        }

        private fun path(json: JSONObject, name: String) =
            strings(json, name).also { require(it.isNotEmpty() && it.size <= 16) }

        private fun response(json: JSONObject): ResponseRules {
            val value = json.getJSONObject("response")
            val format = value.getString("format")
            require(format == "html" || format == "json")
            val html = strings(value, "htmlPath")
            val status = strings(value, "statusPath")
            require(html.size <= 16 && status.size <= 16)
            require(if (format == "html") html.isEmpty() && status.isEmpty() else html.isNotEmpty())
            return ResponseRules(format, html, status)
        }

        internal fun endpoint(template: String, parameter: String): String {
            require(template.length <= 512 && template.startsWith("/") && !template.startsWith("//"))
            require(!template.contains('\\') && !template.contains('#') && !template.contains(".."))
            require(Regex("\\{[^}]*\\}").findAll(template).map { it.value }
                .toList() == listOf("{$parameter}"))
            val expanded = template.replace("{$parameter}", "1")
            val uri = URI(expanded)
            require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawFragment == null)
            require(!expanded.contains('{') && !expanded.contains('}'))
            return template
        }
    }
}

data class HtmlField(val selector: String, val attribute: String?) {
    fun read(element: org.jsoup.nodes.Element): String = element.selectFirst(selector)?.let {
        if (attribute == null) it.text() else it.attr(attribute)
    }.orEmpty()
}

data class ResponseRules(
    val format: String,
    val htmlPath: List<String>,
    val statusPath: List<String>
) {
    fun html(body: String, failure: SourceFailure): String {
        if (format == "html") return body
        return try {
            val json = JSONObject(body)
            if (statusPath.isNotEmpty() && jsonValue(
                    json,
                    statusPath
                ) != true
            ) throw SourceException(failure)
            jsonValue(json, htmlPath) as? String ?: throw SourceException(failure)
        } catch (_: org.json.JSONException) {
            throw SourceException(failure)
        }
    }
}

internal fun jsonValue(json: JSONObject, path: List<String>): Any? {
    var value: Any? = json
    path.forEach { key -> value = (value as? JSONObject)?.opt(key) }
    return value
}

data class SearchRules(
    val endpoint: String, val response: ResponseRules, val container: String, val items: String,
    val id: HtmlField, val title: HtmlField, val link: HtmlField
)

data class DetailRules(
    val response: ResponseRules,
    val root: String,
    val description: HtmlField,
    val poster: HtmlField,
    val facts: String,
    val factName: String,
    val factNames: List<String>
)

data class EpisodeRules(
    val endpoint: String, val response: ResponseRules, val items: String,
    val idAttribute: String, val numberAttribute: String, val titleAttribute: String
)

data class ServerRules(
    val endpoint: String,
    val response: ResponseRules,
    val items: String,
    val audioAttribute: String,
    val nameAttribute: String,
    val name: String,
    val sub: String,
    val dub: String,
    val embedAttribute: String,
    val base64: Boolean
)

data class PlayerRules(
    val variable: String,
    val base64: Boolean,
    val xor: Boolean,
    val key: String,
    val streamPath: List<String>,
    val subtitlesPath: List<String>,
    val subtitleUrlPath: List<String>,
    val subtitleDefaultPath: List<String>
)
