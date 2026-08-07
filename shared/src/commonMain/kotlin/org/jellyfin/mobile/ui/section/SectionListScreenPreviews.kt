package org.jellyfin.mobile.ui.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the full list behind a row's "More" action.
 *
 * Both card shapes are here because the grid's column count is derived from them — a poster grid
 * and a thumb grid are the same screen laid out to different widths, and that is the thing worth
 * looking at.
 */

private const val PreviewWidth = 390
private const val PreviewHeight = 844

@Preview(name = "Section list · posters", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SectionListPosterPreview() {
    PreviewSurface {
        SectionListScreen(
            title = "Recently Added in Movies",
            cardShape = CardShape.Poster,
            state = SectionListUiState(
                items = PreviewData.posterGrid,
                loadingFirstPage = false,
                totalCount = 128,
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onItemClick = {},
        )
    }
}

@Preview(name = "Section list · thumbs", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SectionListThumbPreview() {
    PreviewSurface {
        SectionListScreen(
            title = "Continue Watching",
            cardShape = CardShape.Thumb,
            state = SectionListUiState(
                items = PreviewData.thumbGrid,
                loadingFirstPage = false,
                totalCount = 14,
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onItemClick = {},
        )
    }
}

/** A later page failed: what already loaded stays, and the footer offers the way to try again. */
@Preview(name = "Section list · page failed", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SectionListPageFailedPreview() {
    PreviewSurface {
        SectionListScreen(
            title = "Recently Added in Movies",
            cardShape = CardShape.Poster,
            state = SectionListUiState(
                items = PreviewData.posterGrid.take(6),
                loadingFirstPage = false,
                totalCount = 128,
                loadMoreFailed = true,
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onItemClick = {},
        )
    }
}

@Preview(name = "Section list · loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SectionListLoadingPreview() {
    PreviewSurface {
        SectionListScreen(
            title = "Recently Added in Movies",
            cardShape = CardShape.Poster,
            state = SectionListUiState(),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onItemClick = {},
        )
    }
}

@Preview(name = "Section list · empty", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SectionListEmptyPreview() {
    PreviewSurface {
        SectionListScreen(
            title = "Favorites",
            cardShape = CardShape.Poster,
            state = SectionListUiState(loadingFirstPage = false),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onItemClick = {},
        )
    }
}

@Preview(name = "Section list · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SectionListErrorPreview() {
    PreviewSurface {
        SectionListScreen(
            title = "Recently Added in Movies",
            cardShape = CardShape.Poster,
            state = SectionListUiState(
                loadingFirstPage = false,
                error = "Could not reach the server",
            ),
            onBack = {},
            onLoadMore = {},
            onRetry = {},
            onItemClick = {},
        )
    }
}
