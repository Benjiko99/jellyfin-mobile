package org.jellyfin.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.MediaItem

private val PosterWidth = 132.dp
private val ThumbWidth = 208.dp
private const val PosterAspectRatio = 2f / 3f
private const val ThumbAspectRatio = 16f / 9f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Home") }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                HomeUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is HomeUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

                is HomeUiState.Content -> if (state.sections.isEmpty()) {
                    Text(
                        text = "Nothing to show yet.\nStart watching something and it will appear here.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                } else {
                    HomeSections(state.sections, onItemClick)
                }
            }
        }
    }
}

@Composable
private fun HomeSections(
    sections: List<HomeSection>,
    onItemClick: (MediaItem) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(sections, key = { it.id }) { section ->
            SectionRow(section, onItemClick)
        }
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    onItemClick: (MediaItem) -> Unit,
) {
    Column {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(section.items, key = { it.id }) { item ->
                MediaCard(
                    item = item,
                    shape = section.cardShape,
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    item: MediaItem,
    shape: CardShape,
    onClick: () -> Unit,
) {
    val width = if (shape == CardShape.Poster) PosterWidth else ThumbWidth
    val aspectRatio = if (shape == CardShape.Poster) PosterAspectRatio else ThumbAspectRatio

    Column(
        modifier = Modifier.width(width).clickable(onClick = onClick),
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

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
