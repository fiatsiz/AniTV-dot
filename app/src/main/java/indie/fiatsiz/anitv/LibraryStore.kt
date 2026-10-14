package indie.fiatsiz.anitv

import android.content.Context
import indie.fiatsiz.anitv.source.AudioVersion
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.Anime
import org.json.JSONArray
import org.json.JSONObject

data class WatchProgress(
    val episodeId: String, val episodeNumber: String, val positionMs: Long, val durationMs: Long,
    val audio: AudioVersion = AudioVersion.SUB, val watchedAt: Long = 0
)

data class LibraryEntry(
    val anime: Anime,
    val favorite: Boolean = false,
    val completed: Boolean = false,
    val progress: WatchProgress? = null
)

class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun read(): List<LibraryEntry> = decode(prefs.getString("entries", "[]") ?: "[]")

    fun save(entries: List<LibraryEntry>) {
        prefs.edit().putString("entries", encode(entries)).apply()
    }

    companion object {
        fun encode(entries: List<LibraryEntry>): String = JSONArray().apply {
            entries.forEach { entry ->
                put(
                    JSONObject().put("id", entry.anime.id).put("title", entry.anime.title)
                        .put("detailUrl", entry.anime.detailUrl).put("favorite", entry.favorite)
                        .put("completed", entry.completed)
                        .apply {
                            entry.progress?.let {
                                put(
                                    "progress", JSONObject()
                                        .put("episodeId", it.episodeId)
                                        .put("episodeNumber", it.episodeNumber)
                                        .put("positionMs", it.positionMs)
                                        .put("durationMs", it.durationMs)
                                        .put("audio", it.audio.name).put("watchedAt", it.watchedAt)
                                )
                            }
                        })
            }
        }.toString()

        fun decode(json: String): List<LibraryEntry> = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                val title = item.optString("title")
                if (!id.matches(Regex("[0-9]+")) || title.isBlank()) return@mapNotNull null
                val progress = item.optJSONObject("progress")?.let {
                    val episodeId = it.optString("episodeId")
                    if (!episodeId.matches(Regex("[0-9]+"))) null else WatchProgress(
                        episodeId,
                        it.optString("episodeNumber"),
                        it.optLong("positionMs").coerceAtLeast(0),
                        it.optLong("durationMs").coerceAtLeast(0),
                        runCatching {
                            AudioVersion.valueOf(
                                it.optString(
                                    "audio",
                                    "SUB"
                                )
                            )
                        }.getOrDefault(AudioVersion.SUB),
                        it.optLong("watchedAt").coerceAtLeast(0)
                    )
                }
                LibraryEntry(
                    Anime(
                        id,
                        title,
                        item.optString("detailUrl")
                            .takeIf { it.startsWith("https://hianime.at/") }),
                    item.optBoolean("favorite"),
                    item.optBoolean("completed"),
                    progress
                )
            }
        }.getOrDefault(emptyList())

        fun update(
            entries: List<LibraryEntry>,
            anime: Anime,
            change: (LibraryEntry) -> LibraryEntry
        ): List<LibraryEntry> {
            val current = entries.firstOrNull { it.anime.id == anime.id } ?: LibraryEntry(anime)
            val changed = change(current)
            return listOf(changed) + entries.filterNot { it.anime.id == anime.id }
        }

        fun resumeEpisode(entry: LibraryEntry): Episode? =
            entry.progress?.takeIf { it.positionMs > 0 }?.let {
                Episode(it.episodeId, it.episodeNumber, "Episode ${it.episodeNumber}")
            }

        fun continueWatching(entries: List<LibraryEntry>): LibraryEntry? =
            watching(entries).maxByOrNull { it.progress!!.watchedAt }

        fun watching(entries: List<LibraryEntry>): List<LibraryEntry> = entries.filter {
            !it.completed && it.progress != null && it.progress.positionMs > 0
        }
    }
}
