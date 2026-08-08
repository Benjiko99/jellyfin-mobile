package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.StreamAuthorizer

enum class PlayerStatus {
    Idle,
    Buffering,
    Ready,
    Ended,
    Failed,
}

/**
 * Playback state that changes by event.
 *
 * Position is deliberately absent: it changes continuously and no engine pushes it, so polling it
 * is the caller's job (see [PlayerEngine.positionMs]). Putting it here would mean a state emission
 * several times a second and a recomposition of everything observing playback.
 */
data class PlayerState(
    val status: PlayerStatus = PlayerStatus.Idle,
    /**
     * Whether playback is *meant* to be running: true from [PlayerEngine.play] until
     * [PlayerEngine.pause], whether or not frames are currently arriving.
     *
     * Deliberately not "are frames arriving", which is what ExoPlayer's own `isPlaying` means and
     * what this field used to carry. That reads false throughout a rebuffer, so a stalling video
     * announced itself as paused: the transport offered Play for something nobody had paused, the
     * position poll stopped and the clock froze. Whether the picture is moving is [status]'s job —
     * [PlayerStatus.Buffering] against this being true is precisely a stall.
     */
    val playWhenReady: Boolean = false,
    val durationMs: Long = 0,
    /** Why playback stopped, ready to be shown — usually the engine's own words. */
    val error: UiText? = null,
)

/**
 * A video engine, deliberately a runtime interface rather than `expect`/`actual`.
 *
 * Compile-time binding would fix one engine per platform, and iOS is expected to carry two —
 * VLCKit for decode coverage, AVPlayer later for AirPlay and battery-sensitive direct play. See
 * PLAN.md §6.1.
 */
interface PlayerEngine {
    val state: StateFlow<PlayerState>

    /** Loads [source] and begins buffering. Does not start playback; call [play]. */
    fun load(source: PlaybackSource)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Current position. Polled rather than observed — see [PlayerState]. */
    fun positionMs(): Long

    /** Frees decoders and the surface. The engine is unusable afterwards. */
    fun release()
}

/**
 * Creates the engine for this platform, tied to the composition that owns the player screen.
 *
 * Built inside the composition rather than in `AppContainer` because Android needs a `Context`,
 * which the container has no clean way to hold — and because an engine holds decoders that must be
 * released when the screen goes away, which is exactly a composition lifetime.
 */
@Composable
expect fun rememberPlayerEngine(authorizer: StreamAuthorizer): PlayerEngine

/** The platform view the engine renders into. */
@Composable
expect fun VideoSurface(engine: PlayerEngine, modifier: Modifier = Modifier)
