package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Owns everything the three detail layouts share — the scaffold, the loading and error states, and
 * the snackbar — then dispatches to the right one.
 *
 * The dispatch happens here rather than at the navigation layer because an item's kind is not
 * known from its id: the caller has only an id (a deep link has nothing else), so the item has to
 * be fetched before we can tell whether it is a movie, a show or an episode.
 */
@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onDismissActionError: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onSeriesClick: (String) -> Unit,
    onCastClick: (CastMember) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Called once the snackbar has been shown, so the effect outlives the composition that started
    // it. Keying the effect on the lambda instead would re-show the snackbar on every recomposition.
    val currentOnDismissActionError by rememberUpdatedState(onDismissActionError)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is DetailUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.message, textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text("Retry") }
                    TextButton(onClick = onBack) { Text("Back") }
                }

                is DetailUiState.Content -> {
                    LaunchedEffect(state.actionError) {
                        state.actionError?.let {
                            snackbarHostState.showSnackbar(it)
                            currentOnDismissActionError()
                        }
                    }

                    when (state.detail.kind) {
                        ItemKind.Series, ItemKind.Season -> SeriesDetailScreen(
                            content = state,
                            onBack = onBack,
                            onPlay = onPlay,
                            onToggleFavorite = onToggleFavorite,
                            onTogglePlayed = onTogglePlayed,
                            onSelectSeason = onSelectSeason,
                            onEpisodeClick = onEpisodeClick,
                            onSeriesClick = onSeriesClick,
                            onCastClick = onCastClick,
                        )

                        ItemKind.Episode -> EpisodeDetailScreen(
                            detail = state.detail,
                            onBack = onBack,
                            onPlay = onPlay,
                            onToggleFavorite = onToggleFavorite,
                            onTogglePlayed = onTogglePlayed,
                            onSeriesClick = onSeriesClick,
                            onCastClick = onCastClick,
                        )

                        // Box sets and anything unrecognised have the same shape as a movie:
                        // one title, no children to list. A Person should never arrive here —
                        // navigation sends them to PersonScreen — but a deep link could, and a
                        // generic page beats a crash.
                        ItemKind.Movie,
                        ItemKind.BoxSet,
                        ItemKind.Person,
                        ItemKind.Other,
                        -> MovieDetailScreen(
                            detail = state.detail,
                            onBack = onBack,
                            onPlay = onPlay,
                            onToggleFavorite = onToggleFavorite,
                            onTogglePlayed = onTogglePlayed,
                            onCastClick = onCastClick,
                        )
                    }
                }
            }
        }
    }
}

/*
 * Only the states this file owns. The three layouts it dispatches to are previewed alongside
 * themselves, in MovieDetailScreen.kt, SeriesDetailScreen.kt and EpisodeDetailScreen.kt.
 */

@Preview(name = "Detail · loading")
@Composable
private fun DetailLoadingPreview() {
    PreviewSurface {
        DetailScreenPreview(DetailUiState.Loading)
    }
}

@Preview(name = "Detail · error")
@Composable
private fun DetailErrorPreview() {
    PreviewSurface {
        DetailScreenPreview(DetailUiState.Error("Could not load this item"))
    }
}

/** [DetailScreen] takes eleven callbacks and none of them do anything in a preview. */
@Composable
private fun DetailScreenPreview(state: DetailUiState) {
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
