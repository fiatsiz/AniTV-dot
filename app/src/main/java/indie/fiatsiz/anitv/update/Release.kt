package indie.fiatsiz.anitv.update

import org.json.JSONObject
import java.math.BigInteger
import java.net.URI

internal const val MAX_METADATA_BYTES = 1024 * 1024
internal const val MAX_APK_BYTES = 100L * 1024 * 1024
internal const val RELEASE_API = "https://api.github.com/repos/fiatsiz/AniTV-dot/releases/latest"

@ConsistentCopyVisibility
internal data class StableVersion private constructor(val name: String) :
    Comparable<StableVersion> {
    override fun compareTo(other: StableVersion): Int {
        name.split('.').zip(other.name.split('.')).forEach { (a, b) ->
            BigInteger(a).compareTo(BigInteger(b)).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    companion object {
        fun parse(name: String): StableVersion? = name.takeIf {
            it.length <= 100 && Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(
                it
            )
        }?.let(::StableVersion)
    }
}

internal enum class UpdateProblem {
    NETWORK, HTTP, METADATA, RATE_LIMIT, STORAGE, DOWNLOAD, SIZE, DIGEST, UNSAFE_URL,
    APK_INVALID, PACKAGE, CERTIFICATE, VERSION, SDK
}

internal class UpdateException(val problem: UpdateProblem) : Exception(problem.name)

internal fun requireUpdate(condition: Boolean, problem: UpdateProblem) {
    if (!condition) throw UpdateException(problem)
}

internal data class Release(
    val version: StableVersion,
    val notes: String,
    val url: String,
    val size: Long,
    val sha256: String
) {
    val fileName get() = "AniTV-${version.name}.apk"

    companion object {
        fun parse(bytes: ByteArray): Release? {
            requireUpdate(bytes.size <= MAX_METADATA_BYTES, UpdateProblem.METADATA)
            try {
                val root = JSONObject(bytes.toString(Charsets.UTF_8))
                if (root.getBoolean("draft") || root.getBoolean("prerelease")) return null
                val tag = root.getString("tag_name")
                val version = tag.takeIf { it.startsWith('v') }?.drop(1)?.let(StableVersion::parse)
                    ?: throw UpdateException(UpdateProblem.METADATA)
                val assets = root.getJSONArray("assets")
                val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .filter { it.optString("name") == "AniTV-${version.name}.apk" }
                requireUpdate(matches.size == 1, UpdateProblem.METADATA)
                val asset = matches.single()
                requireUpdate(asset.getString("state") == "uploaded", UpdateProblem.METADATA)
                val sizeValue = asset.get("size").toString()
                val size = sizeValue.toLongOrNull() ?: throw UpdateException(UpdateProblem.METADATA)
                requireUpdate(size in 1..MAX_APK_BYTES, UpdateProblem.SIZE)
                val digest = asset.getString("digest")
                requireUpdate(
                    Regex("sha256:[a-fA-F0-9]{64}").matches(digest),
                    UpdateProblem.METADATA
                )
                val url = asset.getString("browser_download_url")
                validateInitialUrl(url, version)
                return Release(
                    version, root.optString("body", "").takeUnless { it == "null" }.orEmpty(),
                    url, size, digest.substringAfter(':').lowercase()
                )
            } catch (e: UpdateException) {
                throw e
            } catch (_: Exception) {
                throw UpdateException(UpdateProblem.METADATA)
            }
        }
    }
}

internal fun validateInitialUrl(url: String, version: StableVersion) {
    val expected =
        "https://github.com/fiatsiz/AniTV-dot/releases/download/v${version.name}/AniTV-${version.name}.apk"
    requireUpdate(url == expected, UpdateProblem.UNSAFE_URL)
}

internal fun validateRedirect(url: String) {
    val uri = try {
        URI(url)
    } catch (_: Exception) {
        throw UpdateException(UpdateProblem.UNSAFE_URL)
    }
    requireUpdate(
        uri.scheme == "https" && uri.host in setOf(
            "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"
        ) && uri.userInfo == null && uri.port in setOf(-1, 443) && uri.fragment == null,
        UpdateProblem.UNSAFE_URL
    )
}

internal data class ApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val certificates: Set<String>
)

internal fun validateApk(
    candidate: ApkIdentity,
    installed: ApkIdentity,
    release: Release,
    sdk: Int
) {
    requireUpdate(
        candidate.packageName == "indie.fiatsiz.anitv" &&
                candidate.packageName == installed.packageName, UpdateProblem.PACKAGE
    )
    requireUpdate(
        candidate.versionName == release.version.name && candidate.versionCode > installed.versionCode &&
                release.version > (StableVersion.parse(installed.versionName)
            ?: throw UpdateException(UpdateProblem.VERSION)), UpdateProblem.VERSION
    )
    requireUpdate(candidate.minSdk <= sdk, UpdateProblem.SDK)
    requireUpdate(
        candidate.certificates.isNotEmpty() && candidate.certificates == installed.certificates,
        UpdateProblem.CERTIFICATE
    )
}
