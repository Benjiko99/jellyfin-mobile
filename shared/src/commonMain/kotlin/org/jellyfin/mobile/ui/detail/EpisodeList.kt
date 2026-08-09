package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.Season
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.WatchBadge
import org.jellyfin.mobile.domain.asUiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_error_load_episodes
import org.jellyfin.mobile.resources.detail_no_episodes
import org.jellyfin.mobile.resources.detail_no_playlist_items
import org.jellyfin.mobile.ui.components.WatchIndicator
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jellyfin.mobile.ui.theme.WideAspectRatio
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val EpisodeImageWidth = 132.dp

@Composable
fun SeasonSelector(
    seasons: List<Season>,
    selectedSeasonId: String?,
    onSelectSeason: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it.id }) { season ->
            val selected = season.id == selectedSeasonId
            Text(
                text = season.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                    )
                    .clickable { onSelectSeason(season.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * What stands in for a child list that is loading, failed, or has nothing in it.
 *
 * [emptyMessage] is passed in rather than fixed here because the two lists that use this are empty
 * for different reasons — a season nobody has scanned yet, and a playlist nobody has added to.
 */
@Composable
fun ChildListPlaceholder(
    loading: Boolean,
    error: UiText?,
    isEmpty: Boolean,
    emptyMessage: StringResource,
    modifier: Modifier = Modifier,
) {
    val message = when {
        loading -> null
        error != null -> error.resolve()
        isEmpty -> stringResource(emptyMessage)
        else -> return
    }

    Box(
        modifier = modifier.fillMaxWidth().height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (message == null) {
            CircularProgressIndicator()
        } else {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(EpisodeImageWidth)
                .aspectRatio(WideAspectRatio)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (episode.imageUrl != null) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            episode.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }

            if (episode.isPlayed) {
                WatchIndicator(
                    badge = WatchBadge.Watched,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.title)
                    .joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            episode.runtime?.let {
                Text(
                    text = it.resolve(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            episode.overview?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(name = "Season selector")
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
@Preview(name = "Episode rows")
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

@Preview(name = "Child list placeholders")
@Composable
private fun ChildListPlaceholderPreview() {
    PreviewSurface {
        Column {
            ChildListPlaceholder(
                loading = true,
                error = null,
                isEmpty = true,
                emptyMessage = Res.string.detail_no_episodes,
            )
            ChildListPlaceholder(
                loading = false,
                error = Res.string.detail_error_load_episodes.asUiText(),
                isEmpty = true,
                emptyMessage = Res.string.detail_no_episodes,
            )
            ChildListPlaceholder(
                loading = false,
                error = null,
                isEmpty = true,
                emptyMessage = Res.string.detail_no_episodes,
            )
            ChildListPlaceholder(
                loading = false,
                error = null,
                isEmpty = true,
                emptyMessage = Res.string.detail_no_playlist_items,
            )
        }
    }
}
