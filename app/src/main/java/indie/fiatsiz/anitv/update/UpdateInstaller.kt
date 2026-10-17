package indie.fiatsiz.anitv.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

class UpdateFileProvider : FileProvider()

internal object UpdateInstaller {
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun install(activity: Activity, repository: UpdateRepository) {
        repository.forInstall { file ->
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed ||
                    (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true
                ) return@runOnUiThread
                try {
                    if (!canInstall(activity)) {
                        repository.installStatus(InstallStatus.PERMISSION)
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                "package:${activity.packageName}".toUri()
                            )
                        )
                    } else {
                        val uri = FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.updates",
                            file
                        )
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(
                            uri,
                            "application/vnd.android.package-archive"
                        )
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        intent.clipData = ClipData.newRawUri("AniTV update", uri)
                        repository.installStatus(InstallStatus.INSTALLER)
                        activity.startActivity(intent)
                    }
                } catch (_: ActivityNotFoundException) {
                    repository.installStatus(InstallStatus.UNAVAILABLE)
                } catch (_: SecurityException) {
                    repository.installStatus(InstallStatus.PERMISSION_DENIED)
                } catch (_: IllegalArgumentException) {
                    repository.installStatus(InstallStatus.UNAVAILABLE)
                }
            }
        }
    }
}
