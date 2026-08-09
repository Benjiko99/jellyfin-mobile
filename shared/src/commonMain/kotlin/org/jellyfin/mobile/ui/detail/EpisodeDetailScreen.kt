package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_credit_directors
import org.jellyfin.mobile.resources.detail_credit_writers
import org.jellyfin.mobile.ui.components.ExternalLinkRow
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.ScreenPadding

/**
 * One episode.
 *
 * Structurally different from a title page rather than a variation on it:
 *
 * - the hero is the episode's own still, so there is no poster beside the title — an episode's
 *   Primary image is a 16:9 frame and cropping it into a 2:3 poster looks broken;
 * - the show is the headline and the episode name sits under it, because an episode title alone
 *   ("Ozymandias") is meaningless without the show;
 * - genres and studios are inherited from the series, so repeating them on every episode is noise.
 */
@Composable
fun EpisodeDetailScreen(
    detail: ItemDetail,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onSeriesClick: (String) -> Unit,
    onCastClick: (CastMember) -> Unit,
    onCoverClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier,
        contentPadding = detailListPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Hero(
                // The episode's Primary image is its still frame. Falls back to the series
                // backdrop for episodes the scraper never found artwork for.
                imageUrl = detail.posterUrl ?: detail.backdropUrl,
                progress = detail.progress,
                onBack = onBack,
                // The still is this page's cover — there is no poster beside the title to tap.
                onImageClick = onCoverClick,
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                detail.seriesLink?.let { SeriesLink(it, onSeriesClick) }
                Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                MetadataLine(
                    buildList {
                        detail.episodeNumbering?.let { add(it.resolve()) }
                        detail.runtime?.let { add(it.resolve()) }
                        detail.year?.let { add(it.toString()) }
                    },
                )
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

        if (detail.directors.isNotEmpty()) {
            item { CreditsRow(Res.plurals.detail_credit_directors, detail.directors) }
        }
        if (detail.writers.isNotEmpty()) {
            item { CreditsRow(Res.plurals.detail_credit_writers, detail.writers) }
        }

        if (detail.cast.isNotEmpty()) {
            // Episode-level people are the guest cast for this episode specifically.
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

/** The show leads and the episode name sits under it, which is the point of this layout. */
@Preview(name = "Episode detail")
@Composable
private fun EpisodeDetailScreenPreview() {
    PreviewSurface {
        EpisodeDetailScreen(
            detail = PreviewData.episodeDetail,
            onBack = {},
            onPlay = {},
            onToggleFavorite = {},
            onTogglePlayed = {},
            onSeriesClick = {},
            onCastClick = {},
            onCoverClick = {},
        )
    }
}
