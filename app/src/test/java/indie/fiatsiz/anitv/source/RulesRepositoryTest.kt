package indie.fiatsiz.anitv.source

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RulesRepositoryTest {
    private fun directory() = Files.createTempDirectory("anitv-rules-test").toFile().apply { deleteOnExit() }
    private fun repository(cache: RulesCache = RulesCache(directory()), deadline: Long = 3000,
        transport: RulesTransport) = RulesRepository(cache, transport, RulesTestSupport.signed(), RulesTestSupport.keys.public, 1, deadline)
    private fun response(revision: Int = 2) = RulesResponse(200, RulesTestSupport.signed(revision).envelope(), "\"rev-$revision\"")

    @Test fun verifiesBundledRulesAndRejectsTamperingWrongKeysAndOversizedEnvelopes() {
        assertEquals(1L, SignedRules.bundled.rules.revision)
        val signed = RulesTestSupport.signed()
        val tampered = JSONObject(signed.envelope().toString(Charsets.UTF_8)).put("payload", Base64.getEncoder().encodeToString("{}".toByteArray())).toString().toByteArray()
        assertThrows(Exception::class.java) { SignedRules.verify(tampered, RulesTestSupport.keys.public, 1) }
        assertThrows(Exception::class.java) { SignedRules.verify(signed.envelope(), SignedRules.publicKey, 1) }
        assertThrows(Exception::class.java) { SignedRules.verify(ByteArray(SignedRules.MAX_BYTES + 1), RulesTestSupport.keys.public, 1) }
        assertThrows(Exception::class.java) { readLimited(ByteArray(SignedRules.MAX_BYTES + 1).inputStream(), SignedRules.MAX_BYTES) }
    }

    @Test fun rejectsUnsupportedSchemaMinimumVersionMalformedFieldsAndSelectors() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("schemaVersion", 2) }, { it.put("minAppVersionCode", 2) },
            { it.put("revision", 0) }, { it.put("revision", 1.5) }, { it.remove("player") },
            { it.getJSONObject("search").put("items", "[") },
            { it.getJSONObject("episodes").put("idAttribute", "bad attribute") },
            { it.getJSONObject("player").put("key", "") },
            { it.getJSONObject("player").put("variable", "alert(1)") },
            { it.getJSONObject("player").put("streamPath", org.json.JSONArray()) },
            { it.getJSONObject("search").getJSONObject("response").put("format", "script") }
        )
        changes.forEach { change -> assertThrows(Exception::class.java) { RulesTestSupport.signed(change = change) } }
    }

    @Test fun rejectsUnsafeOrMalformedEndpointTemplates() {
        listOf("https://evil.example/{query}", "//evil.example/{query}", "/../{query}",
            "/search?q={other}", "/search?q={query}&x={query}", "/search?q={query}#fragment",
            "/\\evil/{query}", "/search", "/search?{query}{").forEach { template ->
            assertThrows(Exception::class.java) { RulesTestSupport.signed { it.getJSONObject("search").put("endpoint", template) } }
        }
    }

    @Test fun enforcesRevisionOrderingAndExactSameRevisionPayload() {
        val one = RulesTestSupport.signed()
        RulesTestSupport.signed().requireSuccessorOf(one)
        RulesTestSupport.signed(2).requireSuccessorOf(one)
        assertThrows(Exception::class.java) { one.requireSuccessorOf(RulesTestSupport.signed(2)) }
        val conflict = RulesTestSupport.signed { it.getJSONObject("servers").put("name", "Other") }
        assertThrows(Exception::class.java) { conflict.requireSuccessorOf(one) }
    }

    @Test fun checksOnlyOnceAndFreezesSelectedSnapshot() {
        val calls = AtomicInteger()
        val repo = repository { _, _ -> calls.incrementAndGet(); response() }
        val snapshot = repo.snapshot()
        repeat(10) { assertSame(snapshot, repo.snapshot()) }
        assertEquals(2L, snapshot.revision)
        assertEquals(RulesOutcome.UPDATED, repo.state.outcome)
        assertEquals(1, calls.get())
    }

    @Test fun usesSavedEtagAndPreservesPreviousValidatedCopyAcrossInterruptedWrite() {
        val dir = directory()
        val cache = RulesCache(dir)
        val one = CachedRules(RulesTestSupport.signed(), "\"one\"")
        val two = CachedRules(RulesTestSupport.signed(2), "\"two\"")
        cache.save(one, one)
        cache.save(two, one)
        File(dir, "current.json.tmp").writeText("interrupted")
        val repo = repository(cache) { etag, _ -> assertEquals("\"two\"", etag); RulesResponse(304) }
        assertEquals(2L, repo.snapshot().revision)
        assertEquals(RulesOutcome.CURRENT, repo.state.outcome)
        File(dir, "current.json").writeText("corrupt")
        val restored = cache.load(RulesTestSupport.keys.public, 1, one.signed)
        assertEquals(1L, restored.signed.rules.revision)
        cache.save(two, restored)
        File(dir, "current.json").writeText("corrupt again")
        assertEquals(1L, cache.load(RulesTestSupport.keys.public, 1, one.signed).signed.rules.revision)
    }

    @Test fun ignoresInvalidOrOlderCacheAndDefaultsOffline() {
        val dir = directory()
        File(dir, "current.json").writeText("invalid")
        val repo = repository(RulesCache(dir)) { _, _ -> throw java.io.IOException("offline") }
        assertEquals(1L, repo.snapshot().revision)
        assertEquals(RulesOutcome.OFFLINE, repo.state.outcome)
        val cache = RulesCache(directory())
        val old = CachedRules(RulesTestSupport.signed(), "old")
        cache.save(old, old)
        assertEquals(2L, cache.load(RulesTestSupport.keys.public, 1, RulesTestSupport.signed(2)).signed.rules.revision)
    }

    @Test fun retainsLocalRulesOnHttpFailuresAndInvalidDownloads() {
        val responses = listOf(RulesResponse(403), RulesResponse(429), RulesResponse(404),
            RulesResponse(200, "bad json".toByteArray()), RulesResponse(200, ByteArray(SignedRules.MAX_BYTES + 1)),
            RulesResponse(200, SignedRules.bundled.envelope()))
        responses.forEach { response ->
            val repo = repository { _, _ -> response }
            assertEquals(1L, repo.snapshot().revision)
            assertTrue(repo.state.outcome in setOf(RulesOutcome.OFFLINE, RulesOutcome.REJECTED))
        }
    }

    @Test fun rejectsDowngradeAndSameRevisionConflictWithoutChangingEtag() {
        val cache = RulesCache(directory())
        val two = CachedRules(RulesTestSupport.signed(2), "two")
        cache.save(two, CachedRules(RulesTestSupport.signed(), null))
        val conflict = RulesTestSupport.signed(2) { it.getJSONObject("servers").put("name", "Changed") }
        for (signed in listOf(RulesTestSupport.signed(), conflict)) {
            val repo = repository(cache) { _, _ -> RulesResponse(200, signed.envelope(), "rejected") }
            assertEquals(2L, repo.snapshot().revision)
            assertEquals(RulesOutcome.REJECTED, repo.state.outcome)
            assertEquals("two", cache.load(RulesTestSupport.keys.public, 1, RulesTestSupport.signed()).etag)
        }
    }

    @Test fun deadlineAppliesToStartupOnceAndDiscardsLateDownloadAndEtag() {
        val cache = RulesCache(directory())
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val repo = repository(cache, 100) { _, _ -> started.countDown(); release.await(2, TimeUnit.SECONDS); finished.countDown(); response() }
        assertTrue(started.await(1, TimeUnit.SECONDS))
        val start = System.nanoTime()
        assertEquals(1L, repo.snapshot().revision)
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 500)
        assertEquals(RulesOutcome.TIMEOUT, repo.state.outcome)
        release.countDown()
        assertTrue(finished.await(1, TimeUnit.SECONDS))
        Thread.sleep(30)
        assertEquals(1L, repo.snapshot().revision)
        assertNull(cache.load(RulesTestSupport.keys.public, 1, RulesTestSupport.signed()).etag)
        val frozenStart = System.nanoTime()
        repeat(20) { repo.snapshot() }
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - frozenStart) < 100)
    }

    @Test fun cachedRulesRemainUsableWhenTransportNeverFinishesBeforeDeadline() {
        val cache = RulesCache(directory())
        val two = CachedRules(RulesTestSupport.signed(2), "two")
        cache.save(two, CachedRules(RulesTestSupport.signed(), null))
        val release = CountDownLatch(1)
        val repo = repository(cache, 100) { _, _ -> release.await(1, TimeUnit.SECONDS); response(3) }
        assertEquals(2L, repo.snapshot().revision)
        release.countDown()
        assertEquals(RulesOutcome.TIMEOUT, repo.state.outcome)
    }

    @Test fun unchangedPayloadCanStoreEtagButUnsolicited304Cannot() {
        val cached = RulesTestSupport.signed()
        val repo = repository { _, _ -> RulesResponse(200, cached.envelope(), "unchanged") }
        assertEquals(1L, repo.snapshot().revision)
        assertEquals(RulesOutcome.CURRENT, repo.state.outcome)
        val unsolicited = repository { _, _ -> RulesResponse(304) }
        unsolicited.snapshot()
        assertEquals(RulesOutcome.REJECTED, unsolicited.state.outcome)
    }
    @Test fun storageFailureDoesNotDiscardValidatedProcessRules() {
        val blocked = File(directory(), "file-instead-of-directory").apply { writeText("existing") }
        val repo = repository(RulesCache(blocked)) { _, _ -> response() }
        assertEquals(2L, repo.snapshot().revision)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (repo.state.outcome != RulesOutcome.CACHE_FAILED && System.nanoTime() < deadline) Thread.yield()
        assertEquals(RulesOutcome.CACHE_FAILED, repo.state.outcome)
        assertEquals("existing", blocked.readText())
    }

    @Test fun signedIncompatibleSchemaAndMinimumVersionKeepLocalSnapshot() {
        for (name in listOf("schemaVersion", "minAppVersionCode")) {
            val payload = RulesTestSupport.payload(2).put(name, 2).toString().toByteArray()
            val repo = repository { _, _ -> RulesResponse(200, RulesTestSupport.sign(payload)) }
            assertEquals(1L, repo.snapshot().revision)
            assertEquals(RulesOutcome.REJECTED, repo.state.outcome)
        }
    }

}
