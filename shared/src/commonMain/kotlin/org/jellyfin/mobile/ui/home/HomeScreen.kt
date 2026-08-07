package org.jellyfin.mobile.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.ui.components.ErrorState
import org.jellyfin.mobile.ui.components.MediaCard
import org.jellyfin.mobile.ui.components.SearchIcon
import org.jellyfin.mobile.ui.components.SectionHeader
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
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

    // Keyed on the tab alone — adding the lambda as a key would reload Favorites on every
    // recomposition that produced a new one.
    val currentOnLoad by rememberUpdatedState(onLoad)

    // Favourites change from the detail screens, so the tab reloads each time it is opened rather
    // than only once. Existing rows stay on screen while it does.
    LaunchedEffect(selectedTab) {
        if (selectedTab == HomeTab.Favorites) currentOnLoad(HomeTab.Favorites)
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
        // The chevron is offered only when the probe found more than the row is showing, so a row
        // holding exactly the preview count does not promise a screen with nothing extra on it.
        SectionHeader(
            title = section.title,
            onMore = if (section.hasMore) {
                { onShowAll(section) }
            } else {
                null
            },
        )
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

@Preview(name = "Home · content")
@Composable
private fun HomeScreenContentPreview() {
    PreviewSurface {
        HomeScreenPreview(SectionsUiState.Content(PreviewData.homeSections))
    }
}

@Preview(name = "Home · loading")
@Composable
private fun HomeScreenLoadingPreview() {
    PreviewSurface {
        HomeScreenPreview(SectionsUiState.Loading)
    }
}

@Preview(name = "Home · error")
@Composable
private fun HomeScreenErrorPreview() {
    PreviewSurface {
        HomeScreenPreview(SectionsUiState.Error("Could not reach the server"))
    }
}

/** A fresh account: signed in, nothing watched, nothing to show. */
@Preview(name = "Home · empty")
@Composable
private fun HomeScreenEmptyPreview() {
    PreviewSurface {
        HomeScreenPreview(SectionsUiState.Content(emptyList()))
    }
}

/**
 * The rows on their own — the part the Favorites tab and the search screen both reuse. Note that
 * the chevron appears only on the rows with something behind it.
 */
@Preview(name = "Section rows")
@Composable
private fun SectionRowsPreview() {
    PreviewSurface {
        SectionRows(PreviewData.homeSections, onItemClick = {}, onShowAll = {})
    }
}

/**
 * The Favorites tab, which a whole-screen preview cannot reach: which tab is shown is internal
 * state, so [HomeScreen] always previews as Home.
 */
@Preview(name = "Section rows · favorites")
@Composable
private fun SectionRowsFavoritesPreview() {
    PreviewSurface {
        SectionRows(PreviewData.favoriteSections, onItemClick = {}, onShowAll = {})
    }
}

@Composable
private fun HomeScreenPreview(state: SectionsUiState) {
    HomeScreen(
        homeState = state,
        favoritesState = SectionsUiState.Content(PreviewData.favoriteSections),
        onLoad = {},
        onRefresh = {},
        onItemClick = {},
        onShowAll = {},
        onSearch = {},
        onSignOut = {},
    )
}
