package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.ui.components.ExternalLinkRow
import org.jellyfin.mobile.ui.theme.ScreenPadding

/**
 * A single, self-contained title: artwork, credits and the controls to watch it.
 *
 * Also used for anything that is not a series, season or episode — a box set or a stray video has
 * the same shape.
 */
@Composable
fun MovieDetailScreen(
    detail: ItemDetail,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onCastClick: (CastMember) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Hero(imageUrl = detail.backdropUrl, progress = detail.progress, onBack = onBack) }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Poster(url = detail.posterUrl, contentDescription = detail.title)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            detail.runtime?.let { add(it) }
                            detail.ratings.official?.let { add(it) }
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

        if (detail.directors.isNotEmpty()) item { CreditsRow("Director", detail.directors) }
        if (detail.writers.isNotEmpty()) item { CreditsRow("Writer", detail.writers) }
        if (detail.studios.isNotEmpty()) item { CreditsRow("Studio", detail.studios) }

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
