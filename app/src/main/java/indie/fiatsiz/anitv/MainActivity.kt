package indie.fiatsiz.anitv

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import indie.fiatsiz.anitv.ui.theme.AniTVTheme
import indie.fiatsiz.anitv.ui.AniTVApp

class MainActivity : AppCompatActivity() {
    private val updates by lazy { indie.fiatsiz.anitv.update.ProcessUpdates.get(applicationContext) }

    override fun onResume() {
        super.onResume()
        updates.resume(indie.fiatsiz.anitv.update.UpdateInstaller.canInstall(this))
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
