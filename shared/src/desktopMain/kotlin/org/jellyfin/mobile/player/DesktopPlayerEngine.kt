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
import org.jellyfin.mobile.resources.player_surface_not_implemented
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder, for the same reason and in the same shape as `VlcPlayerEngine` on iOS: everything
 * above the engine — negotiation, the device profile, the controls, the gestures, reporting — is
 * shared and already exercised by the Android engine, so replacing this class is the whole of the
 * remaining desktop player work.
 *
 * There is nothing on the JVM to fall back to. Compose Desktop draws with Skia and has no video
 * element, JavaFX's `MediaPlayer` reads a handful of formats no Jellyfin library is stored in, and
 * `javax.sound` is audio only. A real engine means a native decoder, and the two candidates are
 * VLCJ (libVLC, LGPL-2.1, the same library iOS is heading for) and an FFmpeg binding. That decision
 * belongs with the iOS one — see PLAN.md §6.1 — because whichever is chosen decides what
 * [DesktopDecoderCapabilities] may declare.
 *
 * Note that [StreamAuthorizer] exists because engines fetch stream URLs themselves: a desktop engine
 * embedding libVLC has the same problem VLCKit does, and passes the header through `VLCMedia`-style
 * options rather than through our `HttpClient`.
 */
class DesktopPlayerEngine : PlayerEngine {
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
    val engine = remember { DesktopPlayerEngine() }
    DisposableEffect(engine) { onDispose { engine.release() } }
    return engine
}

@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.player_surface_not_implemented),
            style = MaterialTheme.typography.bodyMedium,
            // White on black like the rest of the player, which sits over video in either scheme.
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}
