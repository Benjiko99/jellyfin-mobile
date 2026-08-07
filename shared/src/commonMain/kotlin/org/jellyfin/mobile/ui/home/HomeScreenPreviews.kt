package org.jellyfin.mobile.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the home screen.
 *
 * The screen renders whichever tab is selected, and the tab is internal state, so a preview can
 * only ever show Home. Favorites is covered by [SectionRowsFavoritesPreview], which draws the rows
 * that tab is made of.
 */

private const val PreviewWidth = 390
private const val PreviewHeight = 844

@Preview(name = "Home · content", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun HomeScreenContentPreview() {
    PreviewSurface {
        HomeScreen(
            homeState = SectionsUiState.Content(PreviewData.homeSections),
            favoritesState = SectionsUiState.Content(PreviewData.favoriteSections),
            onLoad = {},
            onRefresh = {},
            onItemClick = {},
            onShowAll = {},
            onSearch = {},
            onSignOut = {},
        )
    }
}

@Preview(name = "Home · loading", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun HomeScreenLoadingPreview() {
    PreviewSurface {
        HomeScreen(
            homeState = SectionsUiState.Loading,
            favoritesState = SectionsUiState.Loading,
            onLoad = {},
            onRefresh = {},
            onItemClick = {},
            onShowAll = {},
            onSearch = {},
            onSignOut = {},
        )
    }
}

@Preview(name = "Home · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun HomeScreenErrorPreview() {
    PreviewSurface {
        HomeScreen(
            homeState = SectionsUiState.Error("Could not reach the server"),
            favoritesState = SectionsUiState.Loading,
            onLoad = {},
            onRefresh = {},
            onItemClick = {},
            onShowAll = {},
            onSearch = {},
            onSignOut = {},
        )
    }
}

/** A fresh account: signed in, nothing watched, nothing to show. */
@Preview(name = "Home · empty", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun HomeScreenEmptyPreview() {
    PreviewSurface {
        HomeScreen(
            homeState = SectionsUiState.Content(emptyList()),
            favoritesState = SectionsUiState.Content(emptyList()),
            onLoad = {},
            onRefresh = {},
            onItemClick = {},
            onShowAll = {},
            onSearch = {},
            onSignOut = {},
        )
    }
}

/**
 * The rows on their own — the part the Favorites tab and the search screen both reuse. Note the
 * "More" action appears only on the rows that have something behind it.
 */
@Preview(name = "Section rows", widthDp = PreviewWidth, heightDp = 520)
@Composable
private fun SectionRowsPreview() {
    PreviewSurface {
        SectionRows(PreviewData.homeSections, onItemClick = {}, onShowAll = {})
    }
}

@Preview(name = "Section rows · favorites", widthDp = PreviewWidth, heightDp = 420)
@Composable
private fun SectionRowsFavoritesPreview() {
    PreviewSurface {
        SectionRows(PreviewData.favoriteSections, onItemClick = {}, onShowAll = {})
    }
}
