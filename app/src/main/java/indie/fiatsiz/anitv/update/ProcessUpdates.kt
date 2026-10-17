package indie.fiatsiz.anitv.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.security.MessageDigest

@Suppress("DEPRECATION")
internal class AndroidApkInspector(private val context: Context) {
    private val flags = if (Build.VERSION.SDK_INT >= 28)
        PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    fun installed(): ApkIdentity =
        identity(context.packageManager.getPackageInfo(context.packageName, flags))

    fun inspect(file: File, release: Release, installed: ApkIdentity) {
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: throw UpdateException(UpdateProblem.APK_INVALID)
        candidate.applicationInfo?.apply {
            sourceDir = file.absolutePath
            publicSourceDir = file.absolutePath
        }
        validateApk(identity(candidate), installed, release, Build.VERSION.SDK_INT)
    }

    private fun identity(info: PackageInfo) = ApkIdentity(
        info.packageName, info.versionName ?: "", PackageInfoCompat.getLongVersionCode(info),
        minimumSdk(info),
        (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
        else info.signatures)?.map { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }?.toSet().orEmpty()
    )

    private fun minimumSdk(info: PackageInfo): Int {
        val application = info.applicationInfo ?: throw UpdateException(UpdateProblem.APK_INVALID)
        if (Build.VERSION.SDK_INT >= 24) return application.minSdkVersion
        // API 23 does not expose ApplicationInfo.minSdkVersion; inspect the packaged manifest instead.
        context.packageManager.getResourcesForApplication(application).assets
            .openXmlResourceParser("AndroidManifest.xml").use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "uses-sdk") {
                        return parser.getAttributeIntValue(
                            "http://schemas.android.com/apk/res/android",
                            "minSdkVersion",
                            Int.MAX_VALUE
                        )
                    }
                }
            }
        return 1
    }
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
