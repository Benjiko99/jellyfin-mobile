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
import org.jellyfin.mobile.domain.WatchBadge
import org.jellyfin.mobile.ui.components.WatchIndicator
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jellyfin.mobile.ui.theme.WideAspectRatio

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

@Composable
fun EpisodesPlaceholder(
    loading: Boolean,
    error: String?,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    val message = when {
        loading -> null
        error != null -> error
        isEmpty -> "No episodes in this season"
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
                .clip(RoundedCornerShape(6.dp))
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
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = listOfNotNull(episode.indexNumber?.let { "$it." }, episode.title)
                        .joinToString(" "),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (episode.isPlayed) WatchIndicator(WatchBadge.Watched)
            }

            episode.runtime?.let {
                Text(
                    text = it,
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

@Preview(name = "Episode list placeholders")
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
