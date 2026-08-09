package org.jellyfin.mobile.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.empty_nothing_here
import org.jellyfin.mobile.resources.error_generic
import org.jellyfin.mobile.resources.item_count
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.components.ErrorState
import org.jellyfin.mobile.ui.components.LoadMoreWhenNearEnd
import org.jellyfin.mobile.ui.components.MediaCard
import org.jellyfin.mobile.ui.components.PageFooter
import org.jellyfin.mobile.ui.components.PosterWidth
import org.jellyfin.mobile.ui.components.ThumbWidth
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The full list behind a row's "More" action.
 *
 * A grid rather than a longer row: once a list runs past a screenful, scrolling sideways through
 * hundreds of items is worse than reading down a page of them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionListScreen(
    title: UiText,
    cardShape: CardShape,
    state: SectionListUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = title.resolve(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // Emits nothing until the count arrives. The bar is a fixed height either way, so
                // nothing below it moves when it does.
                subtitle = {
                    state.totalCount?.let { total ->
                        Text(pluralStringResource(Res.plurals.item_count, total, total.toString()))
                    }
                },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loadingFirstPage -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.items.isEmpty() -> Text(
                    text = stringResource(Res.string.empty_nothing_here),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> ItemGrid(cardShape, state, onLoadMore, onRetry, onItemClick)
            }
        }
    }
}

@Composable
private fun ItemGrid(
    cardShape: CardShape,
    state: SectionListUiState,
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

    LazyVerticalGrid(
        state = gridState,
        // Landscape cards need roughly twice the width, so the column count follows the shape
        // rather than being fixed.
        columns = GridCells.Adaptive(minSize = if (cardShape == CardShape.Poster) PosterWidth else ThumbWidth),
        contentPadding = PaddingValues(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.items, key = { it.id }) { item ->
            MediaCard(
                item = item,
                shape = cardShape,
                onClick = { onItemClick(item) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Spans the whole row whatever the adaptive column count works out to be.
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageFooter(state.loadingMore, state.loadMoreFailed, onRetry)
        }
    }
}

/**
 * Both card shapes, because the column count is derived from them — a poster grid and a thumb grid
 * are the same screen laid out to different widths.
 */
@Preview(name = "Section list · posters")
@Composable
private fun SectionListPosterPreview() {
    PreviewSurface {
        SectionListScreenPreview(
            cardShape = CardShape.Poster,
            state = SectionListUiState(
                items = PreviewData.posterGrid,
                loadingFirstPage = false,
                totalCount = 128,
            ),
        )
    }
}

@Preview(name = "Section list · thumbs")
@Composable
private fun SectionListThumbPreview() {
    PreviewSurface {
        SectionListScreenPreview(
            title = SectionKind.Resume.title(),
            cardShape = CardShape.Thumb,
            state = SectionListUiState(
                items = PreviewData.thumbGrid,
                loadingFirstPage = false,
                totalCount = 14,
            ),
        )
    }
}

/** A later page failed: what already loaded stays, and the footer offers the way to try again. */
@Preview(name = "Section list · page failed")
@Composable
private fun SectionListPageFailedPreview() {
    PreviewSurface {
        SectionListScreenPreview(
            state = SectionListUiState(
                items = PreviewData.posterGrid.take(6),
                loadingFirstPage = false,
                totalCount = 128,
                loadMoreFailed = true,
            ),
        )
    }
}

@Preview(name = "Section list · loading")
@Composable
private fun SectionListLoadingPreview() {
    PreviewSurface {
        SectionListScreenPreview(state = SectionListUiState())
    }
}

@Preview(name = "Section list · empty")
@Composable
private fun SectionListEmptyPreview() {
    PreviewSurface {
        SectionListScreenPreview(
            title = SectionKind.FavoriteMovies.title(),
            state = SectionListUiState(loadingFirstPage = false),
        )
    }
}

@Preview(name = "Section list · error")
@Composable
private fun SectionListErrorPreview() {
    PreviewSurface {
        SectionListScreenPreview(
            state = SectionListUiState(
                loadingFirstPage = false,
                error = Res.string.error_generic.asUiText(),
            ),
        )
    }
}

@Composable
private fun SectionListScreenPreview(
    state: SectionListUiState,
    title: UiText = SectionKind.LatestInLibrary.title("Movies"),
    cardShape: CardShape = CardShape.Poster,
) {
    SectionListScreen(
        title = title,
        cardShape = cardShape,
        state = state,
        onBack = {},
        onLoadMore = {},
        onRetry = {},
        onItemClick = {},
    )
}
