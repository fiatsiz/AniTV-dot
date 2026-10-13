package indie.fiatsiz.anitv.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player

internal class ForegroundPlayer(
    private val lifecycle: Lifecycle,
    initialPositionMs: Long,
    private val createPlayer: (Long) -> Player,
    private val onProgress: (Long, Long) -> Unit,
    private val onPlayerChanged: (Player?) -> Unit
) : DefaultLifecycleObserver {
    private var player: Player? = null
    private var positionMs = initialPositionMs
    private var playWhenReady = true

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        if (player != null) return
        player = createPlayer(positionMs).also {
            it.playWhenReady = playWhenReady
            onPlayerChanged(it)
        }
    }

    override fun onStop(owner: LifecycleOwner) = releasePlayer()

    fun dispose() {
        lifecycle.removeObserver(this)
        releasePlayer()
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        playWhenReady = current.playWhenReady
        current.pause()
        positionMs = current.currentPosition.coerceAtLeast(0)
        try {
            if (positionMs > 0) onProgress(positionMs, current.duration.coerceAtLeast(0))
        } finally {
            current.release()
            onPlayerChanged(null)
        }
    }
}
