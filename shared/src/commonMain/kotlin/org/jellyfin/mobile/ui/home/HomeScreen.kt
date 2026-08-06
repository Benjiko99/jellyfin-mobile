package org.jellyfin.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.ui.components.ErrorState
import org.jellyfin.mobile.ui.components.MediaCard
import org.jellyfin.mobile.ui.components.SearchIcon
import org.jellyfin.mobile.ui.theme.ScreenPadding

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
     * Loads a tab. Called when Favorites is shown — favourites change from the detail screens — and
     * on retry. Silent: no refresh indicator.
     */
    onLoad: (HomeTab) -> Unit,
    /** Pull-to-refresh. Separate from [onLoad] only so the indicator tracks the user's gesture. */
    onRefresh: (HomeTab) -> Unit,
    onItemClick: (MediaItem) -> Unit,
    onShowAll: (HomeSection) -> Unit,
    onSearch: () -> Unit,
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
                        IconButton(onClick = onSearch) {
                            Icon(imageVector = SearchIcon, contentDescription = "Search")
                        }
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
        val state = when (selectedTab) {
            HomeTab.Home -> homeState
            HomeTab.Favorites -> favoritesState
        }

        PullToRefreshBox(
            isRefreshing = (state as? SectionsUiState.Content)?.refreshing == true,
            onRefresh = { onRefresh(selectedTab) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (state) {
                SectionsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is SectionsUiState.Error -> ErrorState(
                    message = state.message,
                    onRetry = { onLoad(selectedTab) },
                    modifier = Modifier.align(Alignment.Center),
                )

                is SectionsUiState.Content -> if (state.sections.isEmpty()) {
                    // Scrollable so the empty state can still be pulled: a user who has just
                    // favourited something on another device has nothing else to tap here.
                    EmptyTab(selectedTab)
                } else {
                    SectionRows(state.sections, onItemClick, onShowAll)
                }
            }
        }
    }
}

@Composable
private fun EmptyTab(tab: HomeTab) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = when (tab) {
                    HomeTab.Home ->
                        "Nothing to show yet.\nStart watching something and it will appear here."
                    HomeTab.Favorites ->
                        "Nothing favourited yet.\nTap Favorite on a movie, show or person " +
                            "and it will appear here."
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

/** A screenful of rows. Shared with the search screen, whose results are the same shape. */
@Composable
internal fun SectionRows(
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
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
            contentPadding = PaddingValues(horizontal = ScreenPadding),
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
