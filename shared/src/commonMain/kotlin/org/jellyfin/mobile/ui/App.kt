package org.jellyfin.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import org.jellyfin.mobile.AppContainer
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.domain.LibraryRow
import org.jellyfin.mobile.domain.LibraryRowTarget
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.Session
import org.jellyfin.mobile.network.buildUserImageUrl
import org.jellyfin.mobile.player.rememberPlayerEngine
import org.jellyfin.mobile.ui.detail.DetailScreen
import org.jellyfin.mobile.ui.detail.DetailUiState
import org.jellyfin.mobile.ui.detail.DetailViewModel
import org.jellyfin.mobile.ui.home.HomeScreen
import org.jellyfin.mobile.ui.home.HomeTab
import org.jellyfin.mobile.ui.home.LibrariesViewModel
import org.jellyfin.mobile.ui.home.MenuLinksViewModel
import org.jellyfin.mobile.ui.home.SectionsViewModel
import org.jellyfin.mobile.ui.library.LibraryScreen
import org.jellyfin.mobile.ui.library.LibraryViewModel
import org.jellyfin.mobile.ui.login.LoginScreen
import org.jellyfin.mobile.ui.login.LoginViewModel
import org.jellyfin.mobile.ui.person.PersonCreditsScreen
import org.jellyfin.mobile.ui.person.PersonCreditsViewModel
import org.jellyfin.mobile.ui.person.PersonScreen
import org.jellyfin.mobile.ui.person.PersonUiState
import org.jellyfin.mobile.ui.person.PersonViewModel
import org.jellyfin.mobile.ui.player.PlayerScreen
import org.jellyfin.mobile.ui.player.PlayerViewModel
import org.jellyfin.mobile.ui.search.SearchScreen
import org.jellyfin.mobile.ui.search.SearchViewModel
import org.jellyfin.mobile.ui.section.SectionListScreen
import org.jellyfin.mobile.ui.section.SectionListViewModel
import org.jellyfin.mobile.ui.theme.AppTheme

// Both rules are aimed at reusable composables, and App is neither. It is the composition root:
// MainActivity and MainViewController call it with nothing to pass down, so a `modifier` parameter
// would be permanently unused, and owning the container — including the login ViewModel that gates
// the NavHost below — is the whole reason it exists.
@Suppress("ktlint:compose:modifier-missing-check", "ktlint:compose:vm-injection-check")
@Composable
fun App(sessionFilePath: String) {
    val container = remember { AppContainer(sessionFilePath) }

    // Images go through the same authenticated client as API calls, so the access token stays in
    // the Authorization header instead of being appended to every image URL.
    val httpClient = container.httpClient
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { httpClient })) }
            .build()
    }

    LaunchedEffect(container) {
        container.session.restore()
    }

    AppTheme {
        val restored by container.session.restored.collectAsStateWithLifecycle()
        val session by container.session.state.collectAsStateWithLifecycle()
        // Read into a local so the signed-in branch below gets a non-null Session: a delegated
        // property is not smart-cast.
        val currentSession = session

        when {
            // Reading the stored session is a disk hit; showing the login screen first would make
            // every launch flash a sign-in form the user does not need.
            !restored -> Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            currentSession == null -> {
                val viewModel = viewModel { LoginViewModel(container.api, container.session) }
                val state by viewModel.state.collectAsStateWithLifecycle()
                LoginScreen(
                    state = state,
                    onServerUrlChange = viewModel::onServerUrlChange,
                    onUsernameChange = viewModel::onUsernameChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onSubmit = viewModel::login,
                )
            }

            else -> SignedInNavHost(container, currentSession)
        }
    }
}

/**
 * Where tapping a card goes. People have their own screen; everything else is an item.
 *
 * Only reachable from Favorites today, since no other row mixes people with library content.
 */
private fun MediaItem.route(): Any = when (kind) {
    ItemKind.Person -> PersonRoute(id)
    else -> DetailRoute(id)
}

/** What this route narrows its library to, if the row it was opened from narrowed anything. */
private fun LibraryRoute.narrowedTo(): LibraryRowTarget? = when {
    genre != null -> LibraryRowTarget.Genre(genre)
    studioId != null -> LibraryRowTarget.Studio(studioId, title)
    else -> null
}

/**
 * The same library again, narrowed to one genre or network and titled after it.
 *
 * [LibraryRoute.narrowedTab] is the grid tab holding what the row previewed — the genre rows on a
 * TV library preview series, so the screen behind them is the Shows tab. Note this is a *new*
 * destination rather than a filter applied in place: Back should return to the list of genres.
 */
private fun LibraryRoute.narrowedBy(row: LibraryRow): LibraryRoute {
    val target = row.target
    return copy(
        title = row.title,
        narrowedTab = when (LibraryKind.from(collectionType)) {
            LibraryKind.TvShows -> LibraryTab.Shows
            else -> LibraryTab.Movies
        }.name,
        genre = (target as? LibraryRowTarget.Genre)?.name,
        studioId = (target as? LibraryRowTarget.Studio)?.id,
    )
}

/** The full list behind a row's "More". Shared by the home tabs and search, which build the same rows. */
private fun HomeSection.route(): SectionRoute = SectionRoute(
    kind = kind.name,
    title = title,
    parentId = parentId,
    searchTerm = searchTerm,
    libraryItemKind = libraryItemKind?.name,
)

@Composable
private fun SignedInNavHost(container: AppContainer, session: Session) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val homeViewModel = viewModel(key = "home") {
                SectionsViewModel(
                    loader = container.homeRepository::loadHome,
                    onSessionExpired = container.session::signOut,
                )
            }
            val favoritesViewModel = viewModel(key = "favorites") {
                SectionsViewModel(
                    loader = container.favoritesRepository::loadFavorites,
                    onSessionExpired = container.session::signOut,
                    // Loaded when the tab is first opened instead of on launch.
                    loadOnInit = false,
                )
            }
            // Not keyed to a tab: the drawer's links belong to the server, not to what is on screen.
            val menuLinksViewModel = viewModel(key = "menuLinks") {
                MenuLinksViewModel(container.menuLinksRepository)
            }
            val librariesViewModel = viewModel(key = "libraries") {
                LibrariesViewModel(container.librariesRepository)
            }
            val homeState by homeViewModel.state.collectAsStateWithLifecycle()
            val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
            val menuLinks by menuLinksViewModel.links.collectAsStateWithLifecycle()
            val libraries by librariesViewModel.libraries.collectAsStateWithLifecycle()

            HomeScreen(
                homeState = homeState,
                favoritesState = favoritesState,
                menuLinks = menuLinks,
                libraries = libraries,
                userName = session.userName,
                // Only built when the user has a picture: `/UserImage` 404s otherwise, and a
                // request per launch to learn that is one we already know the answer to.
                userImageUrl = session.userImageTag?.let { tag ->
                    buildUserImageUrl(session.serverUrl, session.userId, tag)
                },
                onLoad = { tab ->
                    when (tab) {
                        HomeTab.Home -> homeViewModel.load()
                        HomeTab.Favorites -> favoritesViewModel.load()
                    }
                },
                onRefresh = { tab ->
                    when (tab) {
                        HomeTab.Home -> homeViewModel.refresh()
                        HomeTab.Favorites -> favoritesViewModel.refresh()
                    }
                },
                onItemClick = { item -> navController.navigate(item.route()) },
                onShowAll = { section -> navController.navigate(section.route()) },
                onSearch = { navController.navigate(SearchRoute) },
                onLibraryClick = { library ->
                    navController.navigate(
                        LibraryRoute(
                            libraryId = library.id,
                            collectionType = library.kind.collectionType,
                            title = library.name,
                        ),
                    )
                },
                onSignOut = container.session::signOut,
            )
        }

        composable<LibraryRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<LibraryRoute>()
            val kind = LibraryKind.from(route.collectionType)
            // Keyed by everything that changes what the screen shows, not by the library alone: a
            // server can have two movie libraries, and the same library appears again — narrowed —
            // when a genre or network row is opened from it.
            val key = "${route.libraryId}-${route.narrowedTab}-${route.genre}-${route.studioId}"
            val viewModel = viewModel(key = key) {
                LibraryViewModel(
                    libraryId = route.libraryId,
                    libraryKind = kind,
                    repository = container.libraryRepository,
                    rowsRepository = container.libraryRowsRepository,
                    onSessionExpired = container.session::signOut,
                    narrowedTo = route.narrowedTo(),
                    narrowedTab = route.narrowedTab?.let(LibraryTab::valueOf),
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            LibraryScreen(
                title = route.title,
                tabs = viewModel.tabs,
                state = state,
                onSelectTab = viewModel::selectTab,
                onFiltersChange = viewModel::setFilters,
                onSelectLetter = viewModel::selectLetter,
                onOpenRow = { row -> navController.navigate(route.narrowedBy(row)) },
                onLoadMore = viewModel::loadNextPage,
                onRetry = viewModel::retry,
                onItemClick = { item -> navController.navigate(item.route()) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SearchRoute> {
            val viewModel = viewModel {
                SearchViewModel(
                    repository = container.searchRepository,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            SearchScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onBack = { navController.popBackStack() },
                onRetry = viewModel::retry,
                onItemClick = { item -> navController.navigate(item.route()) },
                onShowAll = { section -> navController.navigate(section.route()) },
            )
        }

        composable<SectionRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<SectionRoute>()
            val kind = SectionKind.valueOf(route.kind)
            val viewModel = viewModel(key = "${route.kind}-${route.parentId}-${route.searchTerm}") {
                SectionListViewModel(
                    kind = kind,
                    parentId = route.parentId,
                    libraryItemKind = route.libraryItemKind?.let(ItemKind::valueOf),
                    searchTerm = route.searchTerm,
                    repository = container.sectionRepository,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            SectionListScreen(
                title = route.title,
                cardShape = kind.cardShape,
                state = state,
                onBack = { navController.popBackStack() },
                onLoadMore = viewModel::loadNextPage,
                onRetry = viewModel::retry,
                onItemClick = { item -> navController.navigate(item.route()) },
            )
        }

        composable<DetailRoute> { backStackEntry ->
            val itemId = backStackEntry.toRoute<DetailRoute>().itemId
            // Keyed by item so navigating to a different item builds a fresh view model rather
            // than reusing the previous item's state.
            val viewModel = viewModel(key = itemId) {
                DetailViewModel(
                    itemId = itemId,
                    repository = container.detailRepository,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            DetailScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPlay = {
                    (state as? DetailUiState.Content)?.detail?.let { detail ->
                        navController.navigate(
                            PlayerRoute(detail.id, detail.title, detail.playbackPositionTicks),
                        )
                    }
                },
                onRetry = viewModel::load,
                onToggleFavorite = viewModel::toggleFavorite,
                onTogglePlayed = viewModel::togglePlayed,
                onDismissActionError = viewModel::dismissActionError,
                onSelectSeason = viewModel::selectSeason,
                // Episodes are items too, so they reuse the same detail route.
                onEpisodeClick = { navController.navigate(DetailRoute(it.id)) },
                onSeriesClick = { seriesId ->
                    // Navigating up from an episode puts a second detail screen on the stack.
                    // Each back stack entry owns its own view model, so the two are independent
                    // and Back returns to the episode.
                    navController.navigate(DetailRoute(seriesId))
                },
                onCastClick = { navController.navigate(PersonRoute(it.id)) },
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PlayerRoute>()
            // Owned by the composition, not the view model: it holds decoders that must be released
            // when this screen leaves the back stack.
            val engine = rememberPlayerEngine(container.streamAuthorizer)
            val viewModel = viewModel(key = route.itemId) {
                PlayerViewModel(
                    itemId = route.itemId,
                    title = route.title,
                    startPositionTicks = route.startPositionTicks,
                    repository = container.playbackRepository,
                    engine = engine,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()

            // Tell the server we stopped before the engine is torn down, so it stops transcoding.
            DisposableEffect(viewModel) {
                onDispose { viewModel.stop() }
            }

            PlayerScreen(
                state = state,
                positionMs = positionMs,
                engine = engine,
                onBack = { navController.popBackStack() },
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onSeekBy = viewModel::seekBy,
                onRetry = viewModel::load,
                onControlsVisibleChange = viewModel::setControlsVisible,
                onOpenMenu = viewModel::openMenu,
                onCloseMenu = viewModel::closeMenu,
                onSelectAudio = viewModel::selectAudio,
                onSelectSubtitle = viewModel::selectSubtitle,
                onCycleOrientation = viewModel::cycleOrientation,
            )
        }

        composable<PersonRoute> { backStackEntry ->
            val personId = backStackEntry.toRoute<PersonRoute>().personId
            val viewModel = viewModel(key = personId) {
                PersonViewModel(
                    personId = personId,
                    repository = container.personRepository,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            PersonScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRetry = viewModel::load,
                onToggleFavorite = viewModel::toggleFavorite,
                onDismissActionError = viewModel::dismissActionError,
                // Credits are items, so they reuse the detail route.
                onCreditClick = { navController.navigate(DetailRoute(it.id)) },
                onShowAll = { kind ->
                    val name = (state as? PersonUiState.Content)?.person?.name.orEmpty()
                    navController.navigate(PersonCreditsRoute(personId, name, kind.name))
                },
            )
        }

        composable<PersonCreditsRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<PersonCreditsRoute>()
            val kind = CreditKind.from(route.kind)
            val viewModel = viewModel(key = "${route.personId}-${route.kind}") {
                PersonCreditsViewModel(
                    personId = route.personId,
                    kind = kind,
                    repository = container.personRepository,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            PersonCreditsScreen(
                personName = route.personName,
                kind = kind,
                state = state,
                onBack = { navController.popBackStack() },
                onLoadMore = viewModel::loadNextPage,
                onRetry = viewModel::retry,
                onCreditClick = { navController.navigate(DetailRoute(it.id)) },
            )
        }
    }
}
