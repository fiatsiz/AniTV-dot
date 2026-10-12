package indie.fiatsiz.anitv

import indie.fiatsiz.anitv.source.AnimeDetails
import org.junit.Assert.*
import org.junit.Test

class AnimeDetailCacheTest {
    private val entry = CachedAnimeDetails(AnimeDetails("Description", "https://hianime.at/poster.webp", emptyList()), byteArrayOf(1), 1_000)

    @Test fun cacheExpiresAfterOneDayAndRejectsFutureTimestamp() {
        assertTrue(entry.isFresh(1_000))
        assertTrue(entry.isFresh(1_000 + AnimeDetailCache.TTL_MS - 1))
        assertFalse(entry.isFresh(1_000 + AnimeDetailCache.TTL_MS))
        assertFalse(entry.isFresh(999))
    }
}
