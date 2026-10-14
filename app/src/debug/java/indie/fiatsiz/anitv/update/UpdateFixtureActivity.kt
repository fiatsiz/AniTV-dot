package indie.fiatsiz.anitv.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import indie.fiatsiz.anitv.ui.AniTVApp
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import java.io.File

/** Debug-only emulator harness. Local fixtures never enter the release APK or production repository. */
class UpdateFixtureActivity : ComponentActivity() {
    private lateinit var repository: UpdateRepository

    companion object {
        private var cached: UpdateRepository? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fixture = File(filesDir, "updater-fixture")
        val inspector = AndroidApkInspector(applicationContext)
        repository = cached ?: UpdateRepository(
            UpdateStore(File(fixture, "state")), File(cacheDir, "update/fixture"),
            inspector::installed, inspector::inspect, object : UpdateTransport {
                override fun latest(etag: String?) = ReleaseResponse(
                    200,
                    File(fixture, "release.json").readBytes(), "fixture"
                )

                override fun download(
                    release: Release,
                    target: File,
                    cancellation: DownloadCancellation,
                    progress: (Long) -> Unit
                ) {
                    File(fixture, "candidate.apk").inputStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var count: Int
                            var total = 0L
                            while (input.read(buffer).also { count = it } != -1) {
                                cancellation.check()
                                output.write(buffer, 0, count)
                                total += count
                                progress(total)
                                Thread.sleep(10)
                            }
                        }
                    }
                }
            }).also { cached = it }
        setContent {
            AniTVTheme {
                Surface(Modifier.fillMaxSize()) {
                    AniTVApp(
                        lifecycle,
                        repository
                    ) { UpdateInstaller.install(this@UpdateFixtureActivity, repository) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        repository.resume(packageManager.canRequestPackageInstalls())
    }

    override fun onStop() {
        if (!isChangingConfigurations) repository.background()
        super.onStop()
    }
}
