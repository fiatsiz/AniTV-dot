package indie.fiatsiz.anitv.playback

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ForegroundPlayerTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
        fun event(event: Lifecycle.Event) = lifecycle.handleLifecycleEvent(event)
    }

    private class FakePlayer {
        var position = 0L
        var ready = false
        var releases = 0
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> 100_000L
                "getPlayWhenReady" -> ready
                "setPlayWhenReady" -> { ready = args!![0] as Boolean; null }
                "pause" -> { ready = false; null }
                "release" -> { releases++; null }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player
    }

    @Test fun homeReleasesImmediatelyAndReturnRestoresPosition() {
        val owner = Owner()
        val players = mutableListOf<FakePlayer>()
        val positions = mutableListOf<Long>()
        val progress = mutableListOf<Pair<Long, Long>>()
        var visible: Player? = null
        val session = ForegroundPlayer(owner.lifecycle, 2_000, { position ->
            positions += position
            FakePlayer().also { it.position = position; players += it }.player
        }, { position, duration -> progress += position to duration }, { visible = it })
        assertEquals(0, players.size)
        owner.event(Lifecycle.Event.ON_CREATE)
        owner.event(Lifecycle.Event.ON_START)
        assertSame(players[0].player, visible)
        players[0].position = 8_000
        owner.event(Lifecycle.Event.ON_STOP)
        assertNull(visible)
        assertEquals(false, players[0].ready)
        assertEquals(1, players[0].releases)
        assertEquals(listOf(8_000L to 100_000L), progress)
        owner.event(Lifecycle.Event.ON_START)
        assertEquals(listOf(2_000L, 8_000L), positions)
        assertEquals(true, players[1].ready)
        session.dispose()
        owner.event(Lifecycle.Event.ON_STOP)
        owner.event(Lifecycle.Event.ON_START)
        assertEquals(2, players.size)
        assertEquals(listOf(1, 1), players.map { it.releases })
    }

    @Test fun lateStreamWaitsForForegroundAndPausedPlaybackStaysPaused() {
        val owner = Owner()
        owner.event(Lifecycle.Event.ON_CREATE)
        val players = mutableListOf<FakePlayer>()
        val session = ForegroundPlayer(owner.lifecycle, 0, {
            FakePlayer().also { players += it }.player
        }, { _, _ -> }, {})
        assertEquals(0, players.size)
        owner.event(Lifecycle.Event.ON_START)
        players[0].ready = false
        owner.event(Lifecycle.Event.ON_STOP)
        session.dispose()
        assertEquals(1, players[0].releases)

        owner.event(Lifecycle.Event.ON_START)
        val pausedSession = ForegroundPlayer(owner.lifecycle, 0, {
            FakePlayer().also { players += it }.player
        }, { _, _ -> }, {})
        players[1].ready = false
        owner.event(Lifecycle.Event.ON_STOP)
        owner.event(Lifecycle.Event.ON_START)
        assertEquals(false, players[2].ready)
        pausedSession.dispose()
    }
}
