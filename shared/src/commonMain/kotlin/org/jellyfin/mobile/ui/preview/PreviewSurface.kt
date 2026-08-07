package org.jellyfin.mobile.ui.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.jellyfin.mobile.ui.theme.AppTheme

/**
 * The environment every preview in this module runs in.
 *
 * Two things previews cannot get for themselves:
 *
 * - the app's theme. [AppTheme] is dark-only, and a preview rendered against the tooling's default
 *   light background says nothing about what the screen actually looks like;
 * - artwork. Coil has no network in a preview, so every poster would otherwise be an empty
 *   rectangle and layouts built around images would be impossible to judge.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides PreviewArtwork) {
        AppTheme {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

/**
 * Muted blocks standing in for cover art, picked from the URL so that a row of cards reads as
 * several distinct images rather than one flat slab. Desaturated on purpose: a preview is for
 * judging layout, and bright placeholders draw the eye to the part that isn't real.
 */
private val ArtworkColors = listOf(
    0xFF3C4A5A.toInt(),
    0xFF4A3C55.toInt(),
    0xFF3F5148.toInt(),
    0xFF55483C.toInt(),
    0xFF44404E.toInt(),
)

@OptIn(ExperimentalCoilApi::class)
private val PreviewArtwork = AsyncImagePreviewHandler { request ->
    ColorImage(ArtworkColors[request.data.hashCode().mod(ArtworkColors.size)])
}
