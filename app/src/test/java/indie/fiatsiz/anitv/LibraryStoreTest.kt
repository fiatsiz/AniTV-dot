package indie.fiatsiz.anitv

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
}
