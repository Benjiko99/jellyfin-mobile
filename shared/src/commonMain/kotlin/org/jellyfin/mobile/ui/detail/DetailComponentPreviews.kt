package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.Ratings
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the pieces the three detail layouts are assembled from.
 *
 * Worth having separately from the whole-page previews: a page preview shows one combination of
 * these, while the components each have states the page it appears on cannot reach — a critic
 * score is fresh or rotten, an action bar says Play or Resume, an episode is watched, part-watched
 * or untouched.
 */

private const val PreviewWidth = 390

@Preview(name = "Hero", widthDp = PreviewWidth)
@Composable
private fun HeroPreview() {
    PreviewSurface {
        Hero(
            imageUrl = PreviewData.movieDetail.backdropUrl,
            progress = PreviewData.movieDetail.progress,
            onBack = {},
        )
    }
}

/** With no backdrop the scrim is all there is, so the back control still has to be legible. */
@Preview(name = "Hero · no artwork", widthDp = PreviewWidth)
@Composable
private fun HeroWithoutArtworkPreview() {
    PreviewSurface {
        Hero(imageUrl = null, progress = null, onBack = {})
    }
}

@Preview(name = "Poster")
@Composable
private fun PosterPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(url = PreviewData.movieDetail.posterUrl, contentDescription = null)
            Poster(url = null, contentDescription = null)
        }
    }
}

/**
 * Both scores, at both sides of the 60% threshold that decides whether the critic figure reads as
 * fresh or rotten — the one piece of colour logic on the page.
 */
@Preview(name = "Ratings", widthDp = PreviewWidth)
@Composable
private fun RatingsRowPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RatingsRow(Ratings(community = 8.4f, critic = 92, official = "PG-13"))
            RatingsRow(Ratings(community = 4.1f, critic = 23, official = "R"))
            // Only a community score, which is the usual shape on a TMDb-only server.
            RatingsRow(Ratings(community = 7.0f, critic = null, official = null))
        }
    }
}

/**
 * Every combination of the action bar's labels: Play against Resume, the trailer button's
 * presence, and the three things the played toggle can say.
 */
@Preview(name = "Action bar", widthDp = PreviewWidth)
@Composable
private fun ActionBarPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Started, favourited, has a trailer.
            ActionBar(PreviewData.movieDetail, onPlay = {}, onTrailer = {}, onToggleFavorite = {}, onTogglePlayed = {})
            // Untouched, no trailer.
            ActionBar(PreviewData.sparseDetail, onPlay = {}, onTrailer = {}, onToggleFavorite = {}, onTogglePlayed = {})
            // A container, where marking watched means marking everything inside watched.
            ActionBar(PreviewData.seriesDetail, onPlay = {}, onTrailer = {}, onToggleFavorite = {}, onTogglePlayed = {})
            // Already played, so the toggle reads back the state rather than the action.
            ActionBar(PreviewData.episodeDetail, onPlay = {}, onTrailer = {}, onToggleFavorite = {}, onTogglePlayed = {})
        }
    }
}

@Preview(name = "Genres and credits", widthDp = PreviewWidth)
@Composable
private fun ChipsAndCreditsPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChipRow(PreviewData.seriesDetail.genres, Modifier.padding(horizontal = 16.dp))
            CreditsRow("Director", PreviewData.movieDetail.directors)
            // Pluralised by the component itself, which is the only reason it takes a singular.
            CreditsRow("Writer", PreviewData.movieDetail.writers)
        }
    }
}

@Preview(name = "Cast", widthDp = PreviewWidth)
@Composable
private fun CastSectionPreview() {
    PreviewSurface {
        CastSection(PreviewData.cast, onMemberClick = {})
    }
}

@Preview(name = "Season selector", widthDp = PreviewWidth)
@Composable
private fun SeasonSelectorPreview() {
    PreviewSurface {
        SeasonSelector(
            seasons = PreviewData.seasons,
            selectedSeasonId = "season-2",
            onSelectSeason = {},
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

/** Watched, part-watched, and an episode with neither still nor synopsis. */
@Preview(name = "Episode rows", widthDp = PreviewWidth)
@Composable
private fun EpisodeRowPreview() {
    PreviewSurface {
        Column(Modifier.padding(vertical = 8.dp)) {
            PreviewData.episodes.forEach { episode ->
                EpisodeRow(episode = episode, onClick = {})
            }
        }
    }
}

@Preview(name = "Episode list placeholders", widthDp = PreviewWidth)
@Composable
private fun EpisodesPlaceholderPreview() {
    PreviewSurface {
        Column {
            EpisodesPlaceholder(loading = true, error = null, isEmpty = true)
            EpisodesPlaceholder(loading = false, error = "Could not load episodes", isEmpty = true)
            EpisodesPlaceholder(loading = false, error = null, isEmpty = true)
        }
    }
}
