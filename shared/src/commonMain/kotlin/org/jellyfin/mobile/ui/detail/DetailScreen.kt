package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.action_back
import org.jellyfin.mobile.resources.action_retry
import org.jellyfin.mobile.resources.detail_error_load_item
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jetbrains.compose.resources.stringResource

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
        // The one thing that must not draw under a system bar, so it takes the inset itself.
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding()) },
        // The page is edge to edge in both directions, so neither vertical inset is applied to the
        // content here. The hero image is full-bleed and draws *under* the status bar, with the back
        // control inset inside it; the list scrolls under the navigation bar and carries that inset
        // in its own content padding. Both are the elements that know where their own edges are —
        // padding the whole page instead would leave a strip of background above the artwork, and
        // put the back button two status bars from the top. Sides are kept, for a landscape cutout.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                DetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is DetailUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(state.message.resolve(), textAlign = TextAlign.Center)
                    Button(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
                    TextButton(onClick = onBack) { Text(stringResource(Res.string.action_back)) }
                }

                is DetailUiState.Content -> {
                    // Resolved in the composition; `showSnackbar` runs in a coroutine, which
                    // is no longer one.
                    val actionError = state.actionError?.resolve()
                    LaunchedEffect(actionError) {
                        actionError?.let {
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
                        // generic page beats a crash. A Playlist lands here for now too: it has
                        // children, but an ordered, user-editable list of them is its own screen.
                        ItemKind.Movie,
                        ItemKind.BoxSet,
                        ItemKind.Playlist,
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
        DetailScreenPreview(DetailUiState.Error(Res.string.detail_error_load_item.asUiText()))
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
