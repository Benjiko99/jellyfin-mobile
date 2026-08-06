package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.ui.theme.PosterAspectRatio
import org.jellyfin.mobile.ui.theme.WideAspectRatio

/**
 * The dimensions of a card, which callers laying out a grid need in order to choose a column count.
 */
internal val PosterWidth = 132.dp
internal val ThumbWidth = 208.dp

/**
 * One item, as artwork with a title under it.
 *
 * Used by the home rows, the full-list grid behind "More", and the search screen's suggestions and
 * results, so all four render identically.
 *
 * [Modifier.width] is applied by the caller in a grid, where the column count sets the width.
 */
@Composable
internal fun MediaCard(
    item: MediaItem,
    shape: CardShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(if (shape == CardShape.Poster) PosterWidth else ThumbWidth),
) {
    val aspectRatio = if (shape == CardShape.Poster) PosterAspectRatio else WideAspectRatio

    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Items without artwork are common on sparsely-scraped libraries; show the title
                // rather than an empty rectangle.
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }

            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }

            item.watchBadge?.let { badge ->
                WatchIndicator(
                    badge = badge,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        item.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
