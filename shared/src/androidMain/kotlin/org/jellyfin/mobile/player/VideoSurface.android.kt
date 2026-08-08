package org.jellyfin.mobile.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.jellyfin.mobile.network.StreamAuthorizer
import org.jellyfin.mobile.shared.R

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
 *
 * Inflated from `R.layout.video_surface` rather than constructed, because the one thing that has to
 * be set here — a TextureView instead of the default SurfaceView — can only be set from an
 * attribute set. See that file for why it matters.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    val media3 = engine as? Media3PlayerEngine ?: return
    val hasVideoOutput by media3.hasVideoOutput.collectAsState()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(R.layout.video_surface, null) as PlayerView
            view.apply {
                // Inflating without a parent discards the root's own layout_params, which would
                // leave this wrapping its content inside a container sized to the whole screen.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Hidden from the start: cues can arrive before the first frame does, and the view
                // would lay them out against bounds the video has not established yet.
                subtitleView?.visibility = View.INVISIBLE
                setPlayer(media3.exoPlayer)
            }
        },
        update = { view ->
            view.setPlayer(media3.exoPlayer)
            // INVISIBLE rather than GONE, so the view keeps its place in the layout and has the
            // right bounds to measure against the moment it is shown.
            view.subtitleView?.visibility = if (hasVideoOutput) View.VISIBLE else View.INVISIBLE
        },
        onRelease = { view -> view.setPlayer(null) },
    )
}
