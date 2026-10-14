package indie.fiatsiz.anitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import indie.fiatsiz.anitv.ui.AniTVApp

class MainActivity : ComponentActivity() {
    private val updates by lazy { indie.fiatsiz.anitv.update.ProcessUpdates.get(applicationContext) }

    override fun onResume() {
        super.onResume()
        updates.resume(packageManager.canRequestPackageInstalls())
    }

    override fun onStop() {
        if (!isChangingConfigurations) updates.background()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        indie.fiatsiz.anitv.source.ProcessRules.get(applicationContext)
        setContent {
            AniTVTheme {
                Surface(Modifier.fillMaxSize()) {
                    AniTVApp(lifecycle, updates) {
                        indie.fiatsiz.anitv.update.UpdateInstaller.install(
                            this@MainActivity,
                            updates
                        )
                    }
                }
            }
        }
    }
}
