package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_credit_directors
import org.jellyfin.mobile.resources.detail_credit_studios
import org.jellyfin.mobile.resources.detail_episodes
import org.jellyfin.mobile.resources.detail_seasons
import org.jellyfin.mobile.ui.components.ExternalLinkRow
import org.jellyfin.mobile.ui.components.SectionHeader
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A show, or one season of it.
 *
 * The episode list is the reason this page exists, so it sits above the credits. A series gets a
 * season selector; a season page is already scoped to one season and links up to its show instead.
 */
@Composable
fun SeriesDetailScreen(
    content: DetailUiState.Content,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onSeriesClick: (String) -> Unit,
    onCastClick: (CastMember) -> Unit,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val detail = content.detail

    LazyColumn(
        modifier = modifier,
        contentPadding = detailListPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Hero(imageUrl = detail.backdropUrl, progress = detail.progress, onBack = onBack) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Poster(
                    url = detail.posterUrl,
                    contentDescription = detail.title,
                    onClick = onCoverClick,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Set on a season page; a series has no parent to link to.
                    detail.seriesLink?.let { SeriesLink(it, onSeriesClick) }
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    detail.originalTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MetadataLine(
                        buildList {
                            detail.year?.let { add(it.toString()) }
                            detail.ratings.official?.let { add(it) }
                            // Runtime is per-episode and absent on a series, so season count is
                            // the useful size cue here.
                            detail.childCount?.let {
                                add(pluralStringResource(Res.plurals.detail_seasons, it, it.toString()))
                            }
                        },
                    )
                    detail.tagline?.let { Tagline(it) }
                }
            }
        }

        if (detail.ratings.hasAny) {
            item { RatingsRow(detail.ratings, Modifier.padding(horizontal = ScreenPadding)) }
        }

        item {
            ActionBar(
                detail = detail,
                onPlay = onPlay,
                onTrailer = { detail.trailerUrl?.let(uriHandler::openUri) },
                onToggleFavorite = onToggleFavorite,
                onTogglePlayed = onTogglePlayed,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }

        detail.overview?.let { item { Overview(it, Modifier.padding(horizontal = ScreenPadding)) } }

        if (detail.genres.isNotEmpty()) {
            item { ChipRow(detail.genres, Modifier.padding(horizontal = ScreenPadding)) }
        }

        item { SectionHeader(title = stringResource(Res.string.detail_episodes)) }

        if (content.seasons.isNotEmpty()) {
            item {
                SeasonSelector(
                    seasons = content.seasons,
                    selectedSeasonId = content.selectedSeasonId,
                    onSelectSeason = onSelectSeason,
                )
            }
        }

        if (content.episodes.isEmpty()) {
            item {
                EpisodesPlaceholder(
                    loading = content.episodesLoading,
                    error = content.episodesError,
                    isEmpty = true,
                )
            }
        } else {
            items(content.episodes, key = { it.id }) { episode ->
                EpisodeRow(episode = episode, onClick = { onEpisodeClick(episode) })
            }
        }

        if (detail.directors.isNotEmpty()) {
            item { CreditsRow(Res.plurals.detail_credit_directors, detail.directors) }
        }
        if (detail.studios.isNotEmpty()) {
            item { CreditsRow(Res.plurals.detail_credit_studios, detail.studios) }
        }

        if (detail.cast.isNotEmpty()) {
            item { CastSection(detail.cast, onMemberClick = onCastClick) }
        }

        if (detail.links.isNotEmpty()) {
            item {
                ExternalLinkRow(
                    links = detail.links,
                    onOpen = uriHandler::openUri,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }
    }
}

@Preview(name = "Series detail")
@Composable
private fun SeriesDetailScreenPreview() {
    PreviewSurface {
        SeriesDetailScreenPreview(
            DetailUiState.Content(
                detail = PreviewData.seriesDetail,
                seasons = PreviewData.seasons,
                selectedSeasonId = "season-2",
                episodes = PreviewData.episodes,
            ),
        )
    }
}

/** A season page: already scoped to one season, so it links up to the show instead of selecting. */
@Preview(name = "Series detail · season")
@Composable
private fun SeasonDetailScreenPreview() {
    PreviewSurface {
        SeriesDetailScreenPreview(
            DetailUiState.Content(
                detail = PreviewData.seasonDetail,
                episodes = PreviewData.episodes,
            ),
        )
    }
}

/** The page as it looks between the series arriving and its episodes doing so. */
@Preview(name = "Series detail · episodes loading")
@Composable
private fun SeriesEpisodesLoadingPreview() {
    PreviewSurface {
        SeriesDetailScreenPreview(
            DetailUiState.Content(
                detail = PreviewData.seriesDetail,
                seasons = PreviewData.seasons,
                selectedSeasonId = "season-1",
                episodesLoading = true,
            ),
        )
    }
}

@Composable
private fun SeriesDetailScreenPreview(content: DetailUiState.Content) {
    SeriesDetailScreen(
        content = content,
        onBack = {},
        onPlay = {},
        onToggleFavorite = {},
        onTogglePlayed = {},
        onSelectSeason = {},
        onEpisodeClick = {},
        onSeriesClick = {},
        onCastClick = {},
        onCoverClick = {},
    )
}
