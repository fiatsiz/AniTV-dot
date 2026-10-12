package indie.fiatsiz.anitv

import android.content.Context
import indie.fiatsiz.anitv.source.Anime
import indie.fiatsiz.anitv.source.AnimeDetails
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedAnimeDetails(val details: AnimeDetails, val poster: ByteArray, val savedAt: Long) {
    fun isFresh(now: Long): Boolean = now >= savedAt && now - savedAt < AnimeDetailCache.TTL_MS
}

class AnimeDetailCache(context: Context) {
    private val prefs = context.getSharedPreferences("anime_details", Context.MODE_PRIVATE)
    private val directory = File(context.cacheDir, "anime_banners").apply { mkdirs() }

    fun read(anime: Anime): CachedAnimeDetails? = runCatching {
        val metadata = JSONObject(prefs.getString(anime.id, null) ?: return null)
        val image = File(directory, anime.id).takeIf { it.isFile }?.readBytes() ?: return null
        CachedAnimeDetails(
            AnimeDetails(metadata.getString("description"), metadata.getString("posterUrl"),
                metadata.getJSONArray("facts").let { array -> (0 until array.length()).map { array.getString(it) } }),
            image, metadata.getLong("savedAt"))
    }.getOrNull()

    fun save(anime: Anime, details: AnimeDetails, poster: ByteArray, now: Long) {
        val target = File(directory, anime.id)
        val temporary = File(directory, "${anime.id}.tmp")
        temporary.writeBytes(poster)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return
        }
        val facts = JSONArray().apply { details.facts.forEach(::put) }
        prefs.edit().putString(anime.id, JSONObject().put("description", details.description)
            .put("posterUrl", details.posterUrl).put("facts", facts).put("savedAt", now).toString()).apply()
    }

    companion object { const val TTL_MS = 24 * 60 * 60 * 1000L }
}
