package org.jellyfin.mobile.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.Credit
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.action_retry
import org.jellyfin.mobile.resources.empty_nothing_here
import org.jellyfin.mobile.resources.error_generic
import org.jellyfin.mobile.resources.person_credits_subtitle
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.components.LoadMoreWhenNearEnd
import org.jellyfin.mobile.ui.components.PageFooter
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
                    Text(
                        text = stringResource(kind.title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // The person is always named; the count joins them once it has been asked for.
                subtitle = {
                    Text(
                        text = state.totalCount?.let {
                            stringResource(
                                Res.string.person_credits_subtitle,
                                personName,
                                it.toString(),
                            )
                        } ?: personName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                    Text(state.error.resolve(), textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                }

                state.credits.isEmpty() -> Text(
                    text = stringResource(Res.string.empty_nothing_here),
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
    LoadMoreWhenNearEnd(
        itemCount = state.credits.size,
        endReached = state.endReached,
        loadFailed = state.loadMoreFailed,
        onLoadMore = onLoadMore,
        lastVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
    )

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
            PageFooter(state.loadingMore, state.loadMoreFailed, onRetry)
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
    LoadMoreWhenNearEnd(
        itemCount = state.credits.size,
        endReached = state.endReached,
        loadFailed = state.loadMoreFailed,
        onLoadMore = onLoadMore,
        lastVisibleIndex = { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index },
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.credits, key = { it.id }) { credit ->
            EpisodeCreditRow(credit, onClick = { onCreditClick(credit) })
        }
        item { PageFooter(state.loadingMore, state.loadMoreFailed, onRetry) }
    }
}

@Preview(name = "Credits · grid")
@Composable
private fun CreditGridPreview() {
    PreviewSurface {
        PersonCreditsScreenPreview(
            kind = CreditKind.Movies,
            state = PersonCreditsUiState(
                credits = PreviewData.movieCredits,
                totalCount = 31,
                loadingFirstPage = false,
            ),
        )
    }
}

/** Episode credits stay one per row, so this is the same screen in its other shape. */
@Preview(name = "Credits · episode list")
@Composable
private fun CreditEpisodeListPreview() {
    PreviewSurface {
        PersonCreditsScreenPreview(
            kind = CreditKind.Episodes,
            state = PersonCreditsUiState(
                credits = PreviewData.episodeCredits,
                totalCount = 62,
                loadingFirstPage = false,
                loadingMore = true,
            ),
        )
    }
}

@Preview(name = "Credits · loading")
@Composable
private fun CreditsLoadingPreview() {
    PreviewSurface {
        PersonCreditsScreenPreview(kind = CreditKind.Shows, state = PersonCreditsUiState())
    }
}

@Preview(name = "Credits · error")
@Composable
private fun CreditsErrorPreview() {
    PreviewSurface {
        PersonCreditsScreenPreview(
            kind = CreditKind.Movies,
            state = PersonCreditsUiState(
                loadingFirstPage = false,
                error = Res.string.error_generic.asUiText(),
            ),
        )
    }
}

@Composable
private fun PersonCreditsScreenPreview(kind: CreditKind, state: PersonCreditsUiState) {
    PersonCreditsScreen(
        personName = "Elena Marsh",
        kind = kind,
        state = state,
        onBack = {},
        onLoadMore = {},
        onRetry = {},
        onCreditClick = {},
    )
}
