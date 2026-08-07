package org.jellyfin.mobile.ui.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.LibraryFilters
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.domain.LibraryRow
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.TabShape
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.components.ErrorState
import org.jellyfin.mobile.ui.components.FilterIcon
import org.jellyfin.mobile.ui.components.LoadMoreWhenNearEnd
import org.jellyfin.mobile.ui.components.MediaCard
import org.jellyfin.mobile.ui.components.PageFooter
import org.jellyfin.mobile.ui.components.PosterWidth
import org.jellyfin.mobile.ui.components.ThumbWidth
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.ScreenPadding

/** How far the grid dims while a new query runs, so the stale results read as stale. */
private const val ReloadingAlpha = 0.4f

/**
 * Everything in one library, tab by tab.
 *
 * The same screen for TV and for movies — the two differ only in their title and their tab list,
 * which is exactly how jellyfin-web builds them. See [LibraryTab] for where the tabs come from,
 * which is the client rather than the API.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    title: String,
    tabs: List<LibraryTab>,
    state: LibraryUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onFiltersChange: (LibraryFilters) -> Unit,
    onSelectLetter: (String?) -> Unit,
    /** A genre or network row's header, which reopens this screen narrowed to it. */
    onOpenRow: (LibraryRow) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            state.totalCount?.let { total ->
                                Text(
                                    text = "$total items",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = { BackButton(onClick = onBack) },
                    actions = {
                        // Rows tabs have nothing the sheet could narrow: a genre list is not a
                        // query anyone sorts by runtime.
                        if (state.tab.shape == TabShape.Grid) {
                            FilterButton(
                                activeCount = state.filters.activeCount,
                                onClick = { filtersOpen = true },
                            )
                        }
                    },
                )
                // Scrollable rather than fixed: a TV library has six tabs, and "TV Networks" does
                // not share a screen width with five others.
                if (tabs.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
                        edgePadding = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            Tab(
                                selected = tab == state.tab,
                                onClick = { onSelectTab(tab) },
                                text = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                // Only the very first query gets the whole screen. A filter change or a letter keeps
                // the previous results up, dimmed, so the screen does not blink on every tap.
                state.loadingFirstPage && state.items.isEmpty() && !state.reloading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.isEmpty -> EmptyLibrary(
                    filtering = state.filters.isFiltering || state.startLetter != null,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.tab.shape == TabShape.Rows -> LibraryRows(
                    rows = state.rows,
                    loadingMore = state.loadingMore,
                    loadFailed = state.loadMoreFailed,
                    endReached = state.endReached,
                    onOpenRow = onOpenRow,
                    onItemClick = onItemClick,
                    onLoadMore = onLoadMore,
                    onRetry = onRetry,
                    modifier = Modifier.alpha(if (state.reloading) ReloadingAlpha else 1f),
                )

                else -> LibraryGrid(state, onLoadMore, onRetry, onItemClick)
            }

            // The rail stays put while a query runs, so the letter just tapped is still under the
            // finger that tapped it.
            if (state.tab.alphabetPicker && state.error == null) {
                AlphabetRail(
                    selected = state.startLetter,
                    onSelect = onSelectLetter,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        if (filtersOpen) {
            LibraryFilterSheet(
                filters = state.filters,
                options = state.filterOptions,
                onFiltersChange = onFiltersChange,
                onDismiss = { filtersOpen = false },
            )
        }
    }
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            // Counted rather than a plain dot: with the sheet closed it is the only way to see how
            // much of the library the grid is hiding.
            if (activeCount > 0) Badge { Text(activeCount.toString()) }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = FilterIcon, contentDescription = "Sort and filter")
        }
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LoadMoreWhenNearEnd(
        itemCount = state.items.size,
        endReached = state.endReached,
        loadFailed = state.loadMoreFailed,
        onLoadMore = onLoadMore,
        lastVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
    )

    val dim by animateFloatAsState(
        targetValue = if (state.reloading) ReloadingAlpha else 1f,
        label = "reloading",
    )

    LazyVerticalGrid(
        state = gridState,
        // Landscape cards need roughly twice the width, so the column count follows the shape.
        columns = GridCells.Adaptive(
            minSize = if (state.tab.cardShape == CardShape.Poster) PosterWidth else ThumbWidth,
        ),
        contentPadding = PaddingValues(
            start = ScreenPadding,
            // Clear of the rail, where there is one, so the last column is not underneath it.
            end = if (state.tab.alphabetPicker) ScreenPadding + 28.dp else ScreenPadding,
            top = ScreenPadding,
            bottom = ScreenPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.alpha(dim),
    ) {
        items(state.items, key = { it.id }) { item ->
            MediaCard(
                item = item,
                shape = state.tab.cardShape,
                onClick = { onItemClick(item) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageFooter(state.loadingMore, state.loadMoreFailed, onRetry)
        }
    }
}

/**
 * An empty grid means one of two quite different things, and the difference matters: an empty
 * library is nothing to act on, while an empty *filter* is one tap from being fixed.
 */
@Composable
private fun EmptyLibrary(filtering: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = if (filtering) {
            "Nothing matches these filters."
        } else {
            "This library is empty."
        },
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 32.dp),
    )
}

@Preview(name = "Library · movies")
@Composable
private fun LibraryScreenPreview() {
    PreviewSurface {
        LibraryScreenPreview(
            state = LibraryUiState(
                tab = LibraryTab.Movies,
                items = PreviewData.posterGrid,
                loadingFirstPage = false,
                totalCount = 428,
                filterOptions = PreviewData.filterOptions,
            ),
        )
    }
}

/** The rail active and the filter button badged — what the screen looks like mid-browse. */
@Preview(name = "Library · filtered")
@Composable
private fun LibraryScreenFilteredPreview() {
    PreviewSurface {
        LibraryScreenPreview(
            state = LibraryUiState(
                tab = LibraryTab.Movies,
                items = PreviewData.posterGrid.take(4),
                loadingFirstPage = false,
                startLetter = "M",
                filters = LibraryFilters(genres = setOf("Drama"), favoritesOnly = true),
                filterOptions = PreviewData.filterOptions,
                totalCount = 4,
            ),
        )
    }
}

/** A filter that matches nothing — the case that needs saying so, rather than a blank screen. */
@Preview(name = "Library · no matches")
@Composable
private fun LibraryScreenEmptyPreview() {
    PreviewSurface {
        LibraryScreenPreview(
            state = LibraryUiState(
                tab = LibraryTab.Movies,
                loadingFirstPage = false,
                filters = LibraryFilters(years = setOf(1974)),
                filterOptions = PreviewData.filterOptions,
            ),
        )
    }
}

/**
 * A rows-shaped tab. No alphabet rail and no filter button — there is nothing on a list of genres
 * for either to act on.
 */
@Preview(name = "Library · genres")
@Composable
private fun LibraryScreenRowsPreview() {
    PreviewSurface {
        LibraryScreenPreview(
            state = LibraryUiState(
                tab = LibraryTab.MovieGenres,
                rows = PreviewData.libraryRows,
                loadingFirstPage = false,
                endReached = true,
            ),
        )
    }
}

/** The episodes tab: landscape cards, wider columns, and no alphabet rail. */
@Preview(name = "Library · episodes")
@Composable
private fun LibraryScreenEpisodesPreview() {
    PreviewSurface {
        LibraryScreenPreview(
            title = "TV Shows",
            tabs = LibraryTab.forLibrary(LibraryKind.TvShows),
            state = LibraryUiState(
                tab = LibraryTab.Episodes,
                items = PreviewData.thumbGrid,
                loadingFirstPage = false,
                totalCount = 1204,
            ),
        )
    }
}

@Composable
private fun LibraryScreenPreview(
    state: LibraryUiState,
    title: String = "Movies",
    tabs: List<LibraryTab> = LibraryTab.forLibrary(LibraryKind.Movies),
) {
    LibraryScreen(
        title = title,
        tabs = tabs,
        state = state,
        onSelectTab = {},
        onFiltersChange = {},
        onSelectLetter = {},
        onOpenRow = {},
        onLoadMore = {},
        onRetry = {},
        onItemClick = {},
        onBack = {},
    )
}
