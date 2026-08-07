package org.jellyfin.mobile.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.StreamAuthorizer
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_error_not_implemented

/**
 * Placeholder for the VLCKit engine.
 *
 * VLCKit is a CocoaPods/XCFramework dependency that cannot be added or linked from the Windows host
 * this project is currently developed on (see AGENTS.md), so this reports a clear failure instead of
 * pretending to play. Everything above it — negotiation, the device profile, the player UI, progress
 * reporting — is shared and already exercised by the Android engine, so replacing this class is the
 * whole of the remaining iOS player work.
 *
 * When implementing: `VLCMediaPlayer` renders into a `UIView` supplied via `UIKitView`, exposes
 * `time`/`media.length` for position and duration, and takes per-request headers through
 * `VLCMedia` options rather than anything resembling [StreamAuthorizer] — so the host check must be
 * applied before handing it a URL.
 */
class VlcPlayerEngine : PlayerEngine {
    private val _state = MutableStateFlow(
        PlayerState(
            status = PlayerStatus.Failed,
            error = UiText.Resource(Res.string.player_error_not_implemented),
        ),
    )
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    override fun load(source: PlaybackSource) = Unit

    override fun play() = Unit

    override fun pause() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun positionMs(): Long = 0

    override fun release() = Unit
}

@Composable
actual fun rememberPlayerEngine(authorizer: StreamAuthorizer): PlayerEngine {
    val engine = remember { VlcPlayerEngine() }
    DisposableEffect(engine) { onDispose { engine.release() } }
    return engine
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            text = "Video playback is not available on iOS yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}
