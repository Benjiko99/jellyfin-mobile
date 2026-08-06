package org.jellyfin.mobile.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.jellyfin.mobile.network.StreamAuthorizer

@Composable
actual fun rememberPlayerEngine(authorizer: StreamAuthorizer): PlayerEngine {
    val context = LocalContext.current
    val engine = remember(authorizer) { Media3PlayerEngine(context, authorizer) }

    // Decoders and the codec surface are a scarce system resource — holding them after the screen
    // is gone starves other apps and, on some devices, this one on the next playback.
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return engine
}

/**
 * Hosts ExoPlayer's own [PlayerView] with its controller disabled.
 *
 * The view is used only as a render surface: it gets video scaling, aspect-ratio handling and
 * subtitle layout right, all of which are fiddly to reimplement. Every control the user sees is
 * shared Compose drawn on top.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    val media3 = engine as? Media3PlayerEngine ?: return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setPlayer(media3.exoPlayer)
            }
        },
        update = { view -> view.setPlayer(media3.exoPlayer) },
        onRelease = { view -> view.setPlayer(null) },
    )
}
