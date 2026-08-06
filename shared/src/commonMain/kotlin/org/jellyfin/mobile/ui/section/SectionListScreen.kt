package org.jellyfin.mobile.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.home.MediaCard
import org.jellyfin.mobile.ui.home.PosterWidth
import org.jellyfin.mobile.ui.home.ThumbWidth

/** How close to the end the user gets before the next page is requested. */
private const val PREFETCH_DISTANCE = 8

/**
 * The full list behind a row's "More" action.
 *
 * A grid rather than a longer row: once a list runs past a screenful, scrolling sideways through
 * hundreds of items is worse than reading down a page of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionListScreen(
    title: String,
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
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loadingFirstPage -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.error, textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text("Retry") }
                }

                state.items.isEmpty() -> Text(
                    text = "Nothing here.",
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
    LoadMoreWhenNearEnd(gridState, state, onLoadMore)

    LazyVerticalGrid(
        state = gridState,
        // Landscape cards need roughly twice the width, so the column count follows the shape
        // rather than being fixed.
        columns = GridCells.Adaptive(minSize = if (cardShape == CardShape.Poster) PosterWidth else ThumbWidth),
        contentPadding = PaddingValues(16.dp),
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
            PageFooter(state, onRetry)
        }
    }
}

@Composable
private fun PageFooter(state: SectionListUiState, onRetry: () -> Unit) {
    when {
        state.loadingMore -> Box(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.loadMoreFailed -> Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            OutlinedButton(onClick = onRetry) { Text("Load more") }
        }

        else -> Box(Modifier.fillMaxWidth().height(8.dp))
    }
}

@Composable
private fun LoadMoreWhenNearEnd(
    gridState: LazyGridState,
    state: SectionListUiState,
    onLoadMore: () -> Unit,
) {
    // Keyed on what the predicate actually reads. Keying on the whole state would rebuild the
    // derived state on every emission, including the several a single page load produces.
    val shouldLoad by remember(state.items.size, state.endReached, state.loadMoreFailed) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            !state.endReached && !state.loadMoreFailed && lastVisible >= state.items.size - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) onLoadMore() }
}
