package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.WatchBadge
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the components every screen shares.
 *
 * Each one covers the states that differ visually rather than one specimen of each component: a
 * card is only interesting next to the same card with a badge, a progress bar, or no artwork at
 * all, because those are the variants that collide with each other.
 */

@Preview(name = "Media card · poster", widthDp = 460)
@Composable
private fun MediaCardPosterPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaCard(PreviewData.movie, CardShape.Poster, onClick = {})
            MediaCard(PreviewData.series, CardShape.Poster, onClick = {})
            MediaCard(PreviewData.finishedSeries, CardShape.Poster, onClick = {})
        }
    }
}

/** The cases with something in the way of the artwork: a long title, a pill badge, no image. */
@Preview(name = "Media card · awkward data", widthDp = 460)
@Composable
private fun MediaCardEdgeCasesPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaCard(PreviewData.longTitleMovie, CardShape.Poster, onClick = {})
            MediaCard(PreviewData.hugeCollection, CardShape.Poster, onClick = {})
            MediaCard(PreviewData.artlessMovie, CardShape.Poster, onClick = {})
        }
    }
}

@Preview(name = "Media card · thumb", widthDp = 460)
@Composable
private fun MediaCardThumbPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaCard(PreviewData.episodeInProgress, CardShape.Thumb, onClick = {})
            MediaCard(PreviewData.episodeJustStarted, CardShape.Thumb, onClick = {})
        }
    }
}

/**
 * The badge at every width it has to survive: one digit stays a circle, two and three stretch it
 * into a pill, and the tick is a fixed-size icon rather than text.
 */
@Preview(name = "Watch indicator")
@Composable
private fun WatchIndicatorPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchIndicator(WatchBadge.Unwatched(1))
            WatchIndicator(WatchBadge.Unwatched(24))
            WatchIndicator(WatchBadge.Unwatched(312))
            WatchIndicator(WatchBadge.Watched)
        }
    }
}

@Preview(name = "Error state", widthDp = 360)
@Composable
private fun ErrorStatePreview() {
    PreviewSurface {
        ErrorState(
            message = "Could not reach the server",
            onRetry = {},
        )
    }
}

@Preview(name = "External links", widthDp = 360)
@Composable
private fun ExternalLinkRowPreview() {
    PreviewSurface {
        ExternalLinkRow(
            links = PreviewData.links,
            onOpen = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** All three footer states stacked, since the point of the component is that they swap in place. */
@Preview(name = "Page footer", widthDp = 360)
@Composable
private fun PageFooterPreview() {
    PreviewSurface {
        Column(Modifier.padding(vertical = 12.dp)) {
            PageFooter(loadingMore = true, loadFailed = false, onRetry = {})
            PageFooter(loadingMore = false, loadFailed = true, onRetry = {})
            PageFooter(loadingMore = false, loadFailed = false, onRetry = {})
        }
    }
}

/** The hand-drawn icons, at the size they are actually used. */
@Preview(name = "Icons")
@Composable
private fun IconsPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = {})
            Icon(imageVector = SearchIcon, contentDescription = "Search")
            Icon(imageVector = ClearIcon, contentDescription = "Clear")
            Icon(imageVector = CheckIcon, contentDescription = "Watched")
        }
    }
}
