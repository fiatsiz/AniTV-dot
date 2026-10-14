package indie.fiatsiz.anitv.source

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

enum class RulesOutcome { CHECKING, UPDATED, CURRENT, OFFLINE, TIMEOUT, REJECTED, CACHE_FAILED }
data class RulesState(val revision: Long, val outcome: RulesOutcome)
data class RulesResponse(
    val status: Int,
    val body: ByteArray = byteArrayOf(),
    val etag: String? = null
)

fun interface RulesTransport {
    fun fetch(etag: String?, deadlineNanos: Long): RulesResponse
}

internal data class CachedRules(val signed: SignedRules, val etag: String?)

/** Current and previous files contain their ETag and signed envelope in one atomic record. */
class RulesCache(private val directory: File) {
    internal fun load(
        publicKey: PublicKey,
        appVersionCode: Int,
        bundled: SignedRules
    ): CachedRules {
        var best = CachedRules(bundled, null)
        for (name in listOf("previous.json", "current.json")) {
            runCatching {
                val record = JSONObject(
                    File(directory, name).inputStream()
                        .use { readLimited(it, SignedRules.MAX_BYTES * 2) }.toString(Charsets.UTF_8)
                )
                val signed = SignedRules.verify(
                    Base64.getDecoder().decode(record.getString("envelope")),
                    publicKey,
                    appVersionCode
                )
                signed.requireSuccessorOf(best.signed)
                best = CachedRules(signed, record.optString("etag").takeIf { it.isNotBlank() })
            }
        }
        return best
    }

    internal fun save(value: CachedRules, previous: CachedRules) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create rules cache" }
        val current = File(directory, "current.json")
        if (value.signed.rules.revision > previous.signed.rules.revision || !current.isFile)
            atomicWrite(File(directory, "previous.json"), encode(previous))
        atomicWrite(current, encode(value))
    }

    private fun encode(value: CachedRules): ByteArray {
        val record = JSONObject().put(
            "envelope",
            Base64.getEncoder().encodeToString(value.signed.envelope())
        )
        value.etag?.let { record.put("etag", it) }
        return record.toString().toByteArray(Charsets.UTF_8)
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            Files.move(
                temporary.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }
}

internal fun readLimited(
    input: InputStream,
    maximum: Int,
    deadlineNanos: Long = Long.MAX_VALUE
): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        if (System.nanoTime() >= deadlineNanos) throw TimeoutException()
        val count = input.read(buffer)
        if (count < 0) return output.toByteArray()
        require(output.size() + count <= maximum) { "Rules response too large" }
        output.write(buffer, 0, count)
    }
}

class GitHubRulesTransport : RulesTransport {
    override fun fetch(etag: String?, deadlineNanos: Long): RulesResponse {
        val connection = URI(URL).toURL().openConnection() as HttpURLConnection
        fun remainingMillis(): Int {
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
            if (remaining <= 0) throw TimeoutException()
            return remaining.coerceAtMost(3000).toInt()
        }
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = remainingMillis()
            connection.readTimeout = remainingMillis()
            connection.setRequestProperty("Accept", "application/vnd.github.raw+json")
            connection.setRequestProperty(
                "User-Agent",
                "AniTV/1.0.0 (signed HiAnime rules; github.com/fiatsiz/AniTV-dot)"
            )
            etag?.let { connection.setRequestProperty("If-None-Match", it) }
            val status = connection.responseCode
            val body = if (status == 200) {
                require(connection.contentLengthLong <= SignedRules.MAX_BYTES) { "Rules response too large" }
                connection.readTimeout = remainingMillis()
                connection.inputStream.use { readLimited(it, SignedRules.MAX_BYTES, deadlineNanos) }
            } else byteArrayOf()
            return RulesResponse(status, body, connection.getHeaderField("ETag"))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val URL =
            "https://api.github.com/repos/fiatsiz/AniTV-dot/contents/hianime.signed.json?ref=rules"
    }
}

/** Start once in the process owner. All operations share one frozen rules snapshot. */
class RulesRepository(
    private val cache: RulesCache,
    private val transport: RulesTransport = GitHubRulesTransport(),
    private val bundled: SignedRules = SignedRules.bundled,
    private val publicKey: PublicKey = SignedRules.publicKey,
    private val appVersionCode: Int = 1,
    deadlineMillis: Long = 3000
) {
    private val lock = Any()
    private val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMillis)
    private val selected = CompletableFuture<CachedRules>()

    @Volatile
    private var local = CachedRules(bundled, null)
    private val mutableState = kotlinx.coroutines.flow.MutableStateFlow(
        RulesState(
            bundled.rules.revision,
            RulesOutcome.CHECKING
        )
    )
    val states: kotlinx.coroutines.flow.StateFlow<RulesState> = mutableState
    val state: RulesState get() = states.value
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(
            task,
            "AniTV-rules-deadline"
        ).apply { isDaemon = true }
    }
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(
            task,
            "AniTV-rules-startup"
        ).apply { isDaemon = true }
    }

    init {
        scheduler.schedule(
            { finish(local, RulesOutcome.TIMEOUT) },
            deadlineMillis,
            TimeUnit.MILLISECONDS
        )
        worker.execute {
            try {
                val cached = cache.load(publicKey, appVersionCode, bundled)
                synchronized(lock) { if (!selected.isDone) local = cached }
                if (selected.isDone || System.nanoTime() >= deadlineNanos) return@execute
                val response = transport.fetch(cached.etag, deadlineNanos)
                if (selected.isDone || System.nanoTime() >= deadlineNanos) return@execute
                when (response.status) {
                    304 -> finish(
                        cached,
                        if (cached.etag != null) RulesOutcome.CURRENT else RulesOutcome.REJECTED
                    )

                    200 -> {
                        val downloaded =
                            SignedRules.verify(response.body, publicKey, appVersionCode)
                        downloaded.requireSuccessorOf(cached.signed)
                        val accepted = CachedRules(downloaded, response.etag)
                        if (finish(
                                accepted,
                                if (downloaded.rules.revision > cached.signed.rules.revision) RulesOutcome.UPDATED else RulesOutcome.CURRENT
                            )
                        ) {
                            // Persist only content selected before the deadline; late downloads never reach storage.
                            runCatching { cache.save(accepted, cached) }.onFailure {
                                mutableState.value = RulesState(
                                    accepted.signed.rules.revision,
                                    RulesOutcome.CACHE_FAILED
                                )
                            }
                        }
                    }

                    else -> finish(cached, RulesOutcome.OFFLINE)
                }
            } catch (_: java.io.IOException) {
                finish(local, RulesOutcome.OFFLINE)
            } catch (_: TimeoutException) {
                finish(local, RulesOutcome.TIMEOUT)
            } catch (_: Exception) {
                finish(local, RulesOutcome.REJECTED)
            } finally {
                if (!selected.isDone) finish(local, RulesOutcome.TIMEOUT)
                worker.shutdown()
            }
        }
    }

    private fun finish(value: CachedRules, outcome: RulesOutcome): Boolean = synchronized(lock) {
        if (selected.isDone) return false
        val onTime = System.nanoTime() < deadlineNanos
        val chosen = if (onTime) value else local
        mutableState.value =
            RulesState(chosen.signed.rules.revision, if (onTime) outcome else RulesOutcome.TIMEOUT)
        selected.complete(chosen)
        scheduler.shutdown()
        onTime
    }

    fun snapshot(): SourceRules {
        if (!selected.isDone) {
            try {
                selected.get(
                    (deadlineNanos - System.nanoTime()).coerceAtLeast(0),
                    TimeUnit.NANOSECONDS
                )
            } catch (_: TimeoutException) {
                finish(local, RulesOutcome.TIMEOUT)
            }
        }
        return selected.get().signed.rules
    }
}
