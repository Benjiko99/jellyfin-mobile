package org.jellyfin.mobile.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.action_close
import org.jellyfin.mobile.resources.action_enlarge
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jetbrains.compose.resources.stringResource

/** The picture never shrinks below the screen; there is nothing to see in the space around it. */
private const val MinScale = 1f

/** Far enough in to read the small print on a poster, and to see where a still was cropped. */
private const val MaxScale = 6f

/** Where a double tap lands, chosen to be an obvious jump rather than a nudge. */
private const val DoubleTapScale = 2.5f

/**
 * Black in either colour scheme, and one of the few places a colour is written down rather than
 * taken from the theme — the same exception the player makes, for the same reason. This sits
 * *under artwork*, and a themed surface behind a poster tints the picture it is meant to show.
 */
private val ViewerBackground = Color.Black

/**
 * A picture on its own, over the page it was opened from: pinch or double tap to zoom, drag to pan.
 *
 * Drawn inside the page rather than in a dialog window, so it needs no platform window of its own
 * and inherits the edge-to-edge layout the detail pages already set up — it covers the status bar
 * the way the hero image does, with the close control inset inside it.
 *
 * [url] should point at the largest copy of the image the server will give us. Zooming into a
 * thumbnail sized for a 116dp poster is the one way this screen can look broken.
 */
// `BackHandler` is deprecated in Compose 1.11 in favour of `NavigationEventHandler`, which needs a
// `NavigationEventState` this screen has no other use for. Kept until the replacement is the plain
// one-liner this is; the dispatcher it needs is provided by the platform on both targets.
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FullscreenImageViewer(
    url: String,
    contentDescription: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(MinScale) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val scope = rememberCoroutineScope()
    // Held so a second double tap interrupts the first one's animation rather than fighting it.
    var settling by remember { mutableStateOf<Job?>(null) }

    fun settleTo(targetScale: Float, targetOffset: Offset) {
        settling?.cancel()
        settling = scope.launch {
            val fromScale = scale
            val fromOffset = offset
            animate(initialValue = 0f, targetValue = 1f) { fraction, _ ->
                scale = fromScale + (targetScale - fromScale) * fraction
                offset = fromOffset + (targetOffset - fromOffset) * fraction
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ViewerBackground)
            .pointerInput(Unit) {
                // No `onTap`: the picture is the whole point of this screen, and a stray tap on it
                // must not throw it away. Dismissing is the close control and the system back,
                // both of which say what they do.
                detectTapGestures(
                    onDoubleTap = { position ->
                        if (scale > MinScale) {
                            settleTo(MinScale, Offset.Zero)
                        } else {
                            settleTo(
                                targetScale = DoubleTapScale,
                                targetOffset = clampedOffset(
                                    offset = anchoredOffset(
                                        centroid = position,
                                        pan = Offset.Zero,
                                        offset = offset,
                                        scale = scale,
                                        newScale = DoubleTapScale,
                                        viewport = size,
                                    ),
                                    scale = DoubleTapScale,
                                    viewport = size,
                                ),
                            )
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // A finger on the picture wins over an animation still running under it.
                    settling?.cancel()
                    val newScale = (scale * zoom).coerceIn(MinScale, MaxScale)
                    offset = clampedOffset(
                        offset = anchoredOffset(centroid, pan, offset, scale, newScale, size),
                        scale = newScale,
                        viewport = size,
                    )
                    scale = newScale
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(url)
                // Coil otherwise decodes to the size of the composable, which is the *unzoomed*
                // viewport — the picture would go soft exactly when the user zooms in to look.
                .size(Size.ORIGINAL)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) {
            Icon(
                imageVector = CloseIcon,
                contentDescription = stringResource(Res.string.action_close),
                // White for the same reason the background is black: the control sits over artwork.
                tint = Color.White,
            )
        }
    }
}

/**
 * Makes a picture open full screen, when there is a picture and somewhere to open it.
 *
 * Both conditions matter: a placeholder rectangle should carry neither a ripple nor a screen-reader
 * action, because tapping it would do nothing. The label is what a screen reader reads after the
 * picture's own description — "The Cartographer, double tap to enlarge".
 *
 * Applied to the container rather than to the image, so the two cases where a picture is missing
 * and something else is drawn instead — a person's initial, an empty poster — go through the same
 * check as the ordinary one.
 */
@Composable
internal fun Modifier.enlargeOnClick(enabled: Boolean, onClick: (() -> Unit)?): Modifier =
    if (enabled && onClick != null) {
        clickable(onClickLabel = stringResource(Res.string.action_enlarge), onClick = onClick)
    } else {
        this
    }

/**
 * Where the picture has to sit for the point under the fingers to stay under them.
 *
 * The layer scales about the viewport's centre, so a point `p` of the picture — measured from that
 * centre — is drawn at `scale * p + offset`. Solving that for the offset again, once the fingers
 * have moved by [pan] and the scale has become [newScale], gives the line below. Without it a pinch
 * drags the picture towards the middle of the screen instead of growing where the fingers are.
 */
private fun anchoredOffset(
    centroid: Offset,
    pan: Offset,
    offset: Offset,
    scale: Float,
    newScale: Float,
    viewport: IntSize,
): Offset {
    val fromCentre = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
    return fromCentre + pan - (fromCentre - offset) * (newScale / scale)
}

/**
 * Keeps the picture over the viewport rather than letting it be flung into a corner. At rest the
 * bound is zero in both directions, which is also what returns it to the middle after a zoom out.
 *
 * Measured against the viewport rather than against the picture, whose own dimensions are not known
 * until it has loaded: a portrait poster on a portrait screen can therefore be dragged a little
 * further sideways than its edges strictly need. That is slack, not a wrong picture.
 */
private fun clampedOffset(offset: Offset, scale: Float, viewport: IntSize): Offset {
    val maxX = (viewport.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (viewport.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/**
 * Only the resting state is worth a preview: the zoom lives in gestures the tooling cannot make,
 * and what a still frame can show is that the close control stays legible over a dark picture.
 */
@Preview(name = "Fullscreen image")
@Composable
private fun FullscreenImageViewerPreview() {
    PreviewSurface {
        FullscreenImageViewer(
            url = PreviewData.movieDetail.coverImageUrl.orEmpty(),
            contentDescription = PreviewData.movieDetail.title,
            onDismiss = {},
        )
    }
}
