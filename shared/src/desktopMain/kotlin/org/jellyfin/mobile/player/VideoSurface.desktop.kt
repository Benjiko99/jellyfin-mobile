package org.jellyfin.mobile.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jellyfin.mobile.network.StreamAuthorizer

@Composable
actual fun rememberPlayerEngine(authorizer: StreamAuthorizer): PlayerEngine {
    val engine = remember(authorizer) { VlcjPlayerEngine(authorizer) }

    // libVLC holds decoders and a thread of its own; leaving one running behind a closed screen
    // keeps decoding a stream nobody is watching.
    DisposableEffect(engine) {
        onDispose { engine.release() }
    }
    return engine
}

/**
 * Draws the frames [VlcjPlayerEngine] hands over.
 *
 * A plain [Image], because that is all this needs to be: libVLC has already scaled, deinterlaced and
 * drawn subtitles into the picture, so the only decision left is how it sits in the window.
 * [ContentScale.Fit] against black is what every video player does — the whole frame, letterboxed
 * rather than cropped.
 *
 * No content description. The picture is not an illustration of something else; a screen reader
 * describing a film frame by frame would be noise, and the item's own title is already announced by
 * the player's heading.
 */
@Composable
actual fun VideoSurface(engine: PlayerEngine, modifier: Modifier) {
    val vlcj = engine as? VlcjPlayerEngine ?: return
    val frame by vlcj.frame

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
