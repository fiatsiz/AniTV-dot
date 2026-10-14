package indie.fiatsiz.anitv.update

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal data class UpdateRecord(
    val metadata: String? = null,
    val etag: String? = null,
    val lastAttempt: Long? = null,
    val lastManual: Long? = null,
    val lastSuccess: Long? = null,
    val retryAt: Long = 0,
    val problem: UpdateProblem? = null
)

internal class UpdateStore(private val directory: File) {
    fun read(): UpdateRecord = runCatching {
        val file = File(directory, "state.json")
        require(file.length() <= MAX_METADATA_BYTES * 2L)
        val json = JSONObject(file.readText())
        fun time(key: String) = if (json.has(key) && !json.isNull(key)) json.getLong(key) else null
        UpdateRecord(
            json.optString("metadata").takeIf { it.isNotEmpty() },
            json.optString("etag").takeIf { it.isNotEmpty() },
            time("lastAttempt"),
            time("lastManual"),
            time("lastSuccess"),
            json.optLong("retryAt", 0),
            json.optString("problem").takeIf { it.isNotEmpty() }?.let(UpdateProblem::valueOf)
        ).also { it.metadata?.let { value -> Release.parse(value.toByteArray()) } }
    }.getOrDefault(UpdateRecord())

    fun save(record: UpdateRecord) {
        check(directory.mkdirs() || directory.isDirectory)
        val json = JSONObject().put("metadata", record.metadata).put("etag", record.etag)
            .put("lastAttempt", record.lastAttempt).put("lastManual", record.lastManual)
            .put("lastSuccess", record.lastSuccess).put("retryAt", record.retryAt)
            .put("problem", record.problem?.name)
        val temporary = File(directory, "state.tmp")
        try {
            temporary.outputStream().use { stream ->
                stream.write(json.toString().toByteArray())
                stream.fd.sync()
            }
            Files.move(
                temporary.toPath(), File(directory, "state.json").toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            temporary.delete()
        }
    }
}
