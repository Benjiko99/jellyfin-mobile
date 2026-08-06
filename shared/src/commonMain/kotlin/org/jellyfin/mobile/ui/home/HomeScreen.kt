package org.jellyfin.mobile.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

internal val PosterWidth = 132.dp
internal val ThumbWidth = 208.dp
private const val PosterAspectRatio = 2f / 3f
private const val ThumbAspectRatio = 16f / 9f

enum class HomeTab(val label: String) {
    Home("Home"),
    Favorites("Favorites"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeState: SectionsUiState,
    favoritesState: SectionsUiState,
    /**
     * Loads or refreshes a tab. Called when Favorites is shown — favourites change from the detail
     * screens — and on retry.
     */
    onLoad: (HomeTab) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onShowAll: (HomeSection) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Home) }

    // Favourites change from the detail screens, so the tab reloads each time it is opened rather
    // than only once. Existing rows stay on screen while it does.
    LaunchedEffect(selectedTab) {
        if (selectedTab == HomeTab.Favorites) onLoad(HomeTab.Favorites)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Jellyfin") },
                    actions = {
                        TextButton(onClick = onSignOut) { Text("Sign out") }
                    },
                )
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    HomeTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val state = when (selectedTab) {
                HomeTab.Home -> homeState
                HomeTab.Favorites -> favoritesState
            }

            when (state) {
                SectionsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is SectionsUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = { onLoad(selectedTab) },
                    modifier = Modifier.align(Alignment.Center),
                )

                is SectionsUiState.Content -> if (state.sections.isEmpty()) {
                    Text(
                        text = when (selectedTab) {
                            HomeTab.Home ->
                                "Nothing to show yet.\nStart watching something and it will appear here."
                            HomeTab.Favorites ->
                                "Nothing favourited yet.\nTap Favorite on a movie, show or person " +
                                    "and it will appear here."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                } else {
                    HomeSections(state.sections, onItemClick, onShowAll)
                }
            }
        }
    }
}

@Composable
private fun HomeSections(
    sections: List<HomeSection>,
    onItemClick: (MediaItem) -> Unit,
    onShowAll: (HomeSection) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(sections, key = { it.id }) { section ->
            SectionRow(section, onItemClick, onShowAll)
        }
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    onItemClick: (MediaItem) -> Unit,
    onShowAll: (HomeSection) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only offered when the probe found more than the row is showing, so a row holding
            // exactly the preview count does not promise a screen with nothing extra on it.
            if (section.hasMore) {
                TextButton(
                    onClick = { onShowAll(section) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("More")
                }
            }
        }
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

/**
 * A card in a row. Also used by the full-list screen behind "More", so both render identically.
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
    val aspectRatio = if (shape == CardShape.Poster) PosterAspectRatio else ThumbAspectRatio

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
