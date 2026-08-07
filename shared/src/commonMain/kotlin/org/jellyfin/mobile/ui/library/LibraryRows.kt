package org.jellyfin.mobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.LibraryRow
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.ui.components.LoadMoreWhenNearEnd
import org.jellyfin.mobile.ui.components.MediaCard
import org.jellyfin.mobile.ui.components.PageFooter
import org.jellyfin.mobile.ui.components.SectionHeader
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.ScreenPadding

/**
 * The body of a rows-shaped tab: suggestions, air dates, genres, networks.
 *
 * Close cousin of the home screen's `SectionRows`, and deliberately not the same composable — these
 * rows are [LibraryRow]s, whose header leads back into this screen with a filter applied rather
 * than off to a list screen of its own.
 *
 * Paged, because Genres and Networks are: a page is a dozen rows and each one costs the server a
 * query for its preview, so they arrive a screenful at a time.
 */
@Composable
internal fun LibraryRows(
    rows: List<LibraryRow>,
    loadingMore: Boolean,
    loadFailed: Boolean,
    endReached: Boolean,
    onOpenRow: (LibraryRow) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(
        // Two rows from the end rather than the grid's eight: a row is a screenful on its own, so
        // the same prefetch distance would keep the whole list permanently in flight.
        itemCount = rows.size,
        endReached = endReached,
        loadFailed = loadFailed,
        onLoadMore = onLoadMore,
        lastVisibleIndex = { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
    )

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(rows, key = { it.id }) { row ->
            Column {
                SectionHeader(
                    title = row.title.resolve(),
                    // Only a genre or a network has somewhere to go; a day's episodes are all here.
                    onMore = row.target?.let { { onOpenRow(row) } },
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(row.items, key = { it.id }) { item ->
                        MediaCard(
                            item = item,
                            shape = row.cardShape,
                            onClick = { onItemClick(item) },
                        )
                    }
                }
            }
        }

        item { PageFooter(loadingMore, loadFailed, onRetry) }
    }
}

@Preview(name = "Library rows")
@Composable
private fun LibraryRowsPreview() {
    PreviewSurface {
        LibraryRows(
            rows = PreviewData.libraryRows,
            loadingMore = false,
            loadFailed = false,
            endReached = true,
            onOpenRow = {},
            onItemClick = {},
            onLoadMore = {},
            onRetry = {},
        )
    }
}
