package org.jellyfin.mobile.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the item detail pages.
 *
 * Routed through [DetailScreen] rather than the three layouts directly, because choosing between
 * them is [DetailScreen]'s job — an item's kind is what picks the layout, and previewing the
 * layouts on their own would not show that the right one is chosen.
 */

private const val PreviewWidth = 390
private const val PreviewHeight = 844

/** Long enough to reach the credits, which are the part that runs off the bottom. */
private const val TallPreviewHeight = 1400

@Preview(name = "Detail · movie", widthDp = PreviewWidth, heightDp = TallPreviewHeight)
@Composable
private fun MovieDetailPreview() {
    PreviewSurface {
        DetailPreviewScreen(DetailUiState.Content(PreviewData.movieDetail))
    }
}

/**
 * A movie the server knows nothing about beyond its title: no artwork, overview, ratings or cast.
 * Every optional block is dropped, which is the layout most likely to collapse.
 */
@Preview(name = "Detail · bare movie", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun BareMovieDetailPreview() {
    PreviewSurface {
        DetailPreviewScreen(DetailUiState.Content(PreviewData.sparseDetail))
    }
}

@Preview(name = "Detail · series", widthDp = PreviewWidth, heightDp = TallPreviewHeight)
@Composable
private fun SeriesDetailPreview() {
    PreviewSurface {
        DetailPreviewScreen(
            DetailUiState.Content(
                detail = PreviewData.seriesDetail,
                seasons = PreviewData.seasons,
                selectedSeasonId = "season-2",
                episodes = PreviewData.episodes,
            ),
        )
    }
}

/** A season page: scoped to one season, so it links up to the show instead of selecting one. */
@Preview(name = "Detail · season", widthDp = PreviewWidth, heightDp = TallPreviewHeight)
@Composable
private fun SeasonDetailPreview() {
    PreviewSurface {
        DetailPreviewScreen(
            DetailUiState.Content(
                detail = PreviewData.seasonDetail,
                episodes = PreviewData.episodes,
            ),
        )
    }
}

/** The page as it looks between the item arriving and its episodes doing so. */
@Preview(name = "Detail · series, episodes loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SeriesEpisodesLoadingPreview() {
    PreviewSurface {
        DetailPreviewScreen(
            DetailUiState.Content(
                detail = PreviewData.seriesDetail,
                seasons = PreviewData.seasons,
                selectedSeasonId = "season-1",
                episodesLoading = true,
            ),
        )
    }
}

@Preview(name = "Detail · episode", widthDp = PreviewWidth, heightDp = TallPreviewHeight)
@Composable
private fun EpisodeDetailPreview() {
    PreviewSurface {
        DetailPreviewScreen(DetailUiState.Content(PreviewData.episodeDetail))
    }
}

@Preview(name = "Detail · loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun DetailLoadingPreview() {
    PreviewSurface {
        DetailPreviewScreen(DetailUiState.Loading)
    }
}

@Preview(name = "Detail · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun DetailErrorPreview() {
    PreviewSurface {
        DetailPreviewScreen(DetailUiState.Error("Could not load this item"))
    }
}

/** [DetailScreen] takes eleven callbacks and none of them do anything in a preview. */
@Composable
private fun DetailPreviewScreen(state: DetailUiState) {
    DetailScreen(
        state = state,
        onBack = {},
        onPlay = {},
        onRetry = {},
        onToggleFavorite = {},
        onTogglePlayed = {},
        onDismissActionError = {},
        onSelectSeason = {},
        onEpisodeClick = {},
        onSeriesClick = {},
        onCastClick = {},
    )
}
