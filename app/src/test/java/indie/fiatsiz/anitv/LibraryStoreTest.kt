package indie.fiatsiz.anitv

import indie.fiatsiz.anitv.source.AudioVersion
import indie.fiatsiz.anitv.source.Episode
import indie.fiatsiz.anitv.source.Anime
import org.junit.Assert.*
import org.junit.Test

class LibraryStoreTest {
    private val anime = Anime("123", "Example")

    @Test fun progressSurvivesRoundTripWithoutCompletingTitle() {
        val entries = LibraryStore.update(emptyList(), anime) {
            it.copy(progress = WatchProgress("45", "2", 42_000, 1_200_000))
        }
        val restored = LibraryStore.decode(LibraryStore.encode(entries))
        assertEquals(entries, restored)
        assertFalse(restored.single().completed)
        assertEquals(restored, LibraryStore.watching(restored))
    }

    @Test fun completionAndFavoritesRemainIndependent() {
        val favorite = LibraryStore.update(emptyList(), anime) { it.copy(favorite = true) }
        val completed = LibraryStore.update(favorite, anime) { it.copy(completed = true) }
        assertTrue(completed.single().favorite)
        assertTrue(completed.single().completed)
        assertTrue(LibraryStore.watching(completed).isEmpty())
        assertEquals(completed, LibraryStore.decode(LibraryStore.encode(completed)))
    }

    @Test fun detailLinkSurvivesRoundTrip() {
        val linked = Anime("123", "Example", "https://hianime.at/example-123")
        val restored = LibraryStore.decode(LibraryStore.encode(listOf(LibraryEntry(linked))))
        assertEquals(linked, restored.single().anime)
    }

    @Test fun badOrOldLibraryDataDoesNotCrash() {
        assertTrue(LibraryStore.decode("broken").isEmpty())
        assertTrue(LibraryStore.decode("[{\"id\":\"bad\",\"title\":\"X\"}]").isEmpty())
    }

    @Test fun resumeAudioAndWatchTimeSurviveRoundTrip() {
        val entry = LibraryEntry(anime, favorite = true,
            progress = WatchProgress("45", "2", 42_123, 1_200_000, AudioVersion.DUB, 10_000))
        assertEquals(listOf(entry), LibraryStore.decode(LibraryStore.encode(listOf(entry))))
    }

    @Test fun olderProgressPreservesPositionAndDefaultsNewFields() {
        val old = """[{"id":"123","title":"Example","favorite":true,"completed":false,
            "progress":{"episodeId":"45","episodeNumber":"2","positionMs":42123,"durationMs":1200000}}]"""
        val restored = LibraryStore.decode(old).single()
        assertTrue(restored.favorite)
        assertFalse(restored.completed)
        assertEquals(WatchProgress("45", "2", 42_123, 1_200_000), restored.progress)
        val unknown = old.replace("\"positionMs\":42123", "\"audio\":\"UNKNOWN\",\"watchedAt\":-1,\"positionMs\":42123")
        assertEquals(restored, LibraryStore.decode(unknown).single())
    }

    @Test fun continueWatchingUsesWatchTimeInsteadOfLastLibraryEdit() {
        val older = LibraryEntry(anime, progress = WatchProgress("45", "2", 42_123, 1_200_000, watchedAt = 1_000))
        val recent = LibraryEntry(Anime("456", "Recent"),
            progress = WatchProgress("67", "3", 70_000, 1_200_000, AudioVersion.DUB, 2_000))
        val completed = LibraryEntry(Anime("789", "Completed"), completed = true,
            progress = WatchProgress("89", "4", 80_000, 1_200_000, watchedAt = 3_000))
        val updated = LibraryStore.update(listOf(older, recent, completed), older.anime) { it.copy(favorite = true) }
        assertEquals(recent, LibraryStore.continueWatching(updated))
        assertEquals(recent, LibraryStore.continueWatching(LibraryStore.decode(LibraryStore.encode(updated))))
    }

    @Test fun continueWatchingKeepsLegacyOrderAndExcludesUnstartedTitles() {
        val first = LibraryEntry(anime, progress = WatchProgress("45", "2", 42_123, 1_200_000))
        val second = LibraryEntry(Anime("456", "Second"), progress = WatchProgress("67", "3", 1_000, 1_200_000))
        assertEquals(first, LibraryStore.continueWatching(listOf(first, second)))
        assertNull(LibraryStore.continueWatching(emptyList()))
        assertNull(LibraryStore.continueWatching(listOf(LibraryEntry(anime), first.copy(completed = true),
            second.copy(progress = second.progress!!.copy(positionMs = 0)))))
    }

    @Test fun resumeTargetsSavedEpisodeWithoutAdvancingOrResettingTimestamp() {
        val entry = LibraryEntry(anime, progress = WatchProgress("45", "2", 42_123, 1_200_000))
        assertEquals(Episode("45", "2", "Episode 2"), LibraryStore.resumeEpisode(entry))
        assertEquals(42_123L, entry.progress!!.positionMs)
        assertEquals(Episode("45", "2", "Episode 2"),
            LibraryStore.resumeEpisode(entry.copy(progress = entry.progress.copy(positionMs = 1_200_000))))
        assertNull(LibraryStore.resumeEpisode(entry.copy(progress = null)))
        assertNull(LibraryStore.resumeEpisode(entry.copy(progress = entry.progress.copy(positionMs = 0))))
    }
}
