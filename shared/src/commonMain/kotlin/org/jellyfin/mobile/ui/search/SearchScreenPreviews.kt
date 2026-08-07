package org.jellyfin.mobile.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the search screen.
 *
 * The four states below are what the screen is: an empty field over recommendations, a term over
 * category rows, a term that matched nothing, and a failure. The field itself is the same control
 * throughout, which is why only the content changes between them.
 */

private const val PreviewWidth = 390
private const val PreviewHeight = 844

/** The resting state: nothing typed, recommendations from the user's viewing history. */
@Preview(name = "Search · suggestions", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SearchSuggestionsPreview() {
    PreviewSurface {
        SearchScreen(
            state = SearchUiState(content = SearchContent.Suggestions(PreviewData.suggestions)),
            onQueryChange = {},
            onBack = {},
            onRetry = {},
            onItemClick = {},
            onShowAll = {},
        )
    }
}

/** A fresh account has no viewing history, so `/Items/Suggestions` legitimately returns nothing. */
@Preview(name = "Search · no suggestions", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SearchNoSuggestionsPreview() {
    PreviewSurface {
        SearchScreen(
            state = SearchUiState(content = SearchContent.Suggestions(emptyList())),
            onQueryChange = {},
            onBack = {},
            onRetry = {},
            onItemClick = {},
            onShowAll = {},
        )
    }
}

@Preview(name = "Search · results", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SearchResultsPreview() {
    PreviewSurface {
        SearchScreen(
            state = SearchUiState(
                query = "north",
                content = SearchContent.Results("north", PreviewData.searchSections),
            ),
            onQueryChange = {},
            onBack = {},
            onRetry = {},
            onItemClick = {},
            onShowAll = {},
        )
    }
}

@Preview(name = "Search · no results", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SearchNoResultsPreview() {
    PreviewSurface {
        SearchScreen(
            state = SearchUiState(
                query = "qwertyuiop",
                content = SearchContent.Results("qwertyuiop", emptyList()),
            ),
            onQueryChange = {},
            onBack = {},
            onRetry = {},
            onItemClick = {},
            onShowAll = {},
        )
    }
}

@Preview(name = "Search · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun SearchErrorPreview() {
    PreviewSurface {
        SearchScreen(
            state = SearchUiState(
                query = "north",
                content = SearchContent.Error("Could not reach the server"),
            ),
            onQueryChange = {},
            onBack = {},
            onRetry = {},
            onItemClick = {},
            onShowAll = {},
        )
    }
}
