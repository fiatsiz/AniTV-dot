package indie.fiatsiz.anitv.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal class AndroidApkInspector(private val context: Context) {
    private val flags =
        PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())

    fun installed(): ApkIdentity =
        identity(context.packageManager.getPackageInfo(context.packageName, flags))

    fun inspect(file: File, release: Release, installed: ApkIdentity) {
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException(UpdateProblem.APK_INVALID)
        validateApk(identity(candidate), installed, release, Build.VERSION.SDK_INT)
    }

    private fun identity(info: PackageInfo) = ApkIdentity(
        info.packageName, info.versionName ?: "", info.longVersionCode,
        info.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE,
        info.signingInfo?.apkContentsSigners?.map { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet().orEmpty()
    )
}

/** Keeps checks and downloads alive across Activity and locale recreation. */
internal object ProcessUpdates {
    @Volatile
    private var repository: UpdateRepository? = null
    fun get(context: Context): UpdateRepository = repository ?: synchronized(this) {
        repository ?: context.applicationContext.let { app ->
            val inspector = AndroidApkInspector(app)
            UpdateRepository(
                UpdateStore(File(app.filesDir, "update")), File(app.cacheDir, "update"),
                inspector::installed, inspector::inspect
            )
        }.also { repository = it }
    }
}
