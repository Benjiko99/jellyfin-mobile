package org.jellyfin.mobile.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Dimensions shared across screens.
 *
 * Here rather than in `ui.components` because they are design decisions rather than components: a
 * screen that draws none of our composables still has to agree with the rest about where its content
 * starts and what shape artwork is.
 */

/** The gutter between screen content and the edge of the display. */
internal val ScreenPadding = 16.dp

/**
 * Jellyfin artwork comes in two shapes: [PosterAspectRatio] is cover art — movies, series,
 * collections, people — and [WideAspectRatio] is stills and backdrops, so episodes, thumbnails and
 * the hero image on a detail screen.
 */
internal const val PosterAspectRatio = 2f / 3f
internal const val WideAspectRatio = 16f / 9f
