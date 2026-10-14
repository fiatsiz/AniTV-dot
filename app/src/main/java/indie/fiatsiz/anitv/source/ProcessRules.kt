package indie.fiatsiz.anitv.source

import android.content.Context
import indie.fiatsiz.anitv.BuildConfig
import java.io.File

/** Activity recreation and independent playback consumers reuse the same process owner. */
object ProcessRules {
    @Volatile
    private var repository: RulesRepository? = null
    fun get(context: Context): RulesRepository = repository ?: synchronized(this) {
        repository ?: RulesRepository(
            RulesCache(File(context.applicationContext.filesDir, "source-rules")),
            appVersionCode = BuildConfig.VERSION_CODE
        ).also { repository = it }
    }
}
