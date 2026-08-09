package org.jellyfin.mobile.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.PlaylistEntry
import org.jellyfin.mobile.domain.WatchBadge
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.detail_no_playlist_items
import org.jellyfin.mobile.resources.detail_playlist_item_count
import org.jellyfin.mobile.resources.detail_playlist_items
import org.jellyfin.mobile.ui.components.SectionHeader
import org.jellyfin.mobile.ui.components.WatchIndicator
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jellyfin.mobile.ui.theme.ScreenPadding
import org.jellyfin.mobile.ui.theme.WideAspectRatio
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val PlaylistImageWidth = 132.dp

/**
 * A playlist: its entries, in the order somebody arranged them.
 *
 * The order is the whole point, so unlike every other page here the list is numbered and nothing
 * re-sorts it. Tapping an entry plays it rather than opening its page — a playlist is a queue, and
 * the entries are things to play rather than things to read about. Play at the top starts the first.
 *
 * There is no season selector, no cast and no credits: those describe one work, and a playlist is a
 * collection of them that may span films, episodes and both at once.
 */
@Composable
@Suppress("LongParameterList")
fun PlaylistDetailScreen(
    content: DetailUiState.Content,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onItemClick: (PlaylistEntry) -> Unit,
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
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall)
                    // Counted from the list rather than from `childCount`: the entries are already
                    // here, and a playlist that has just been edited elsewhere would otherwise show
                    // the number the server had when the page was opened.
                    MetadataLine(
                        buildList {
                            if (content.playlistItems.isNotEmpty()) {
                                add(
                                    pluralStringResource(
                                        Res.plurals.detail_playlist_item_count,
                                        content.playlistItems.size,
                                        content.playlistItems.size.toString(),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
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

        item { SectionHeader(title = stringResource(Res.string.detail_playlist_items)) }

        if (content.playlistItems.isEmpty()) {
            item {
                ChildListPlaceholder(
                    loading = content.childrenLoading,
                    error = content.childrenError,
                    isEmpty = true,
                    emptyMessage = Res.string.detail_no_playlist_items,
                )
            }
        } else {
            // Keyed on position as well as id, because a playlist may hold the same item twice and
            // two rows with one key is a crash rather than a glitch.
            itemsIndexed(
                items = content.playlistItems,
                key = { index, entry -> "$index-${entry.item.id}" },
            ) { index, entry ->
                PlaylistRow(
                    position = index + 1,
                    item = entry.item,
                    onClick = { onItemClick(entry) },
                )
            }
        }
    }
}

/**
 * One entry, led by its position in the list.
 *
 * The number is what makes this a playlist rather than a shelf, and it is fixed-width so a list
 * running past nine does not stagger where the artwork starts.
 */
@Composable
private fun PlaylistRow(
    position: Int,
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 24.dp),
        )

        Box(
            modifier = Modifier
                .width(PlaylistImageWidth)
                .aspectRatio(WideAspectRatio)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                )
            }
            if (item.watched) {
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
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // An episode's series is its title and this line says which episode; a film puts its
            // year here. Both come from the same mapper the cards use.
            item.subtitle?.let {
                Text(
                    text = it.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Films and episodes in one list, which is what a playlist is for. */
@Preview(name = "Playlist detail")
@Composable
private fun PlaylistDetailScreenPreview() {
    PreviewSurface {
        PlaylistDetailScreenPreview(
            DetailUiState.Content(
                detail = PreviewData.playlistDetail,
                playlistItems = PreviewData.playlistItems,
            ),
        )
    }
}

/** Nothing in it yet — the state a playlist is in for as long as it takes to fill one. */
@Preview(name = "Playlist detail · empty")
@Composable
private fun EmptyPlaylistDetailScreenPreview() {
    PreviewSurface {
        PlaylistDetailScreenPreview(DetailUiState.Content(detail = PreviewData.playlistDetail))
    }
}

@Preview(name = "Playlist detail · loading its items")
@Composable
private fun LoadingPlaylistDetailScreenPreview() {
    PreviewSurface {
        PlaylistDetailScreenPreview(
            DetailUiState.Content(detail = PreviewData.playlistDetail, childrenLoading = true),
        )
    }
}

@Composable
private fun PlaylistDetailScreenPreview(content: DetailUiState.Content) {
    PlaylistDetailScreen(
        content = content,
        onBack = {},
        onPlay = {},
        onToggleFavorite = {},
        onTogglePlayed = {},
        onItemClick = {},
        onCoverClick = {},
    )
}
