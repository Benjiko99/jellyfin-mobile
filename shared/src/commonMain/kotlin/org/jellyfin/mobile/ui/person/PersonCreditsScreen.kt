package org.jellyfin.mobile.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.jellyfin.mobile.domain.Credit
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.ui.components.BackButton

/** How close to the end the user gets before the next page is requested. */
private const val PREFETCH_DISTANCE = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonCreditsScreen(
    personName: String,
    kind: CreditKind,
    state: PersonCreditsUiState,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCreditClick: (Credit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = kind.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.totalCount?.let { "$personName · $it" } ?: personName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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

                state.credits.isEmpty() -> Text(
                    text = "Nothing here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                // Films and shows are artwork worth browsing in a grid; episode credits are a
                // reading list and stay one per row.
                kind == CreditKind.Episodes -> EpisodeCreditList(state, onLoadMore, onRetry, onCreditClick)

                else -> CreditGrid(state, onLoadMore, onRetry, onCreditClick)
            }
        }
    }
}

@Composable
private fun CreditGrid(
    state: PersonCreditsUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCreditClick: (Credit) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LoadMoreWhenNearEnd(gridState, state, onLoadMore)

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = CreditCardWidth),
        contentPadding = PaddingValues(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.credits, key = { it.id }) { credit ->
            CreditCard(credit, onClick = { onCreditClick(credit) })
        }
        // The footer spans the whole row whatever the adaptive column count works out to be.
        item(span = { GridItemSpan(maxLineSpan) }) {
            PageFooter(state, onRetry)
        }
    }
}

@Composable
private fun EpisodeCreditList(
    state: PersonCreditsUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onCreditClick: (Credit) -> Unit,
) {
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, state, onLoadMore)

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.credits, key = { it.id }) { credit ->
            EpisodeCreditRow(credit, onClick = { onCreditClick(credit) })
        }
        item { PageFooter(state, onRetry) }
    }
}

@Composable
private fun PageFooter(state: PersonCreditsUiState, onRetry: () -> Unit) {
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
    state: PersonCreditsUiState,
    onLoadMore: () -> Unit,
) {
    val shouldLoad by remember(state) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            !state.endReached && !state.loadMoreFailed && lastVisible >= state.credits.size - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) onLoadMore() }
}

@Composable
private fun LoadMoreWhenNearEnd(
    listState: LazyListState,
    state: PersonCreditsUiState,
    onLoadMore: () -> Unit,
) {
    val shouldLoad by remember(state) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            !state.endReached && !state.loadMoreFailed && lastVisible >= state.credits.size - PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(shouldLoad) { if (shouldLoad) onLoadMore() }
}
