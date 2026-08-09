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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import org.jellyfin.mobile.player.rememberPlaybackHardware
import org.jellyfin.mobile.player.rememberPlayerEngine
import org.jellyfin.mobile.ui.detail.DetailScreen
import org.jellyfin.mobile.ui.detail.DetailUiState
import org.jellyfin.mobile.ui.detail.DetailViewModel
import org.jellyfin.mobile.ui.home.HomeScreen
import org.jellyfin.mobile.ui.home.HomeTab
import org.jellyfin.mobile.ui.home.LibrariesViewModel
import org.jellyfin.mobile.ui.home.MenuLinksViewModel
import org.jellyfin.mobile.ui.home.SectionsViewModel
import org.jellyfin.mobile.ui.home.SettingsEntry
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
import org.jellyfin.mobile.ui.settings.ClientSettingsScreen
import org.jellyfin.mobile.ui.theme.AppTheme
import org.jellyfin.mobile.ui.theme.isDark

// Both rules are aimed at reusable composables, and App is neither. It is the composition root:
// MainActivity and MainViewController call it with nothing to pass down, so a `modifier` parameter
// would be permanently unused, and owning the container — including the login ViewModel that gates
// the NavHost below — is the whole reason it exists.
@Suppress("ktlint:compose:modifier-missing-check", "ktlint:compose:vm-injection-check")
@Composable
fun App(dataStoreDirectory: String) {
    val container = remember { AppContainer(dataStoreDirectory) }

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

    // Outside AppTheme, because it is what decides which theme. The store reads eagerly, so this is
    // the stored preference on the first frame rather than the default flashing past.
    val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

    AppTheme(darkTheme = settings.theme.isDark()) {
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
 * Leave this screen, and only this screen.
 *
 * A back that arrives while the screen is already on its way out pops a second entry — the two taps
 * of a double tap on a back arrow, or an arrow racing the system gesture. Popping the last entry
 * empties the back stack, and a `NavHost` with an empty stack composes *nothing*: the window goes
 * black, every screen is gone at once, and Back leaves the app rather than getting the user out of
 * it. The whole app looks dead while the process is perfectly healthy, which is why this is worth a
 * guard rather than a comment. Pop only while there is still something underneath.
 */
private fun NavController.popOnce() {
    if (previousBackStackEntry != null) popBackStack()
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
        // Taken from the target rather than the row's heading, which is a
        // [org.jellyfin.mobile.domain.UiText] and cannot ride in a route. They are the same words:
        // only a genre or a network has a target, and both are named by the server.
        title = when (target) {
            is LibraryRowTarget.Genre -> target.name
            is LibraryRowTarget.Studio -> target.name
            null -> title
        },
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
    parentId = parentId,
    libraryName = libraryName,
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
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val favoritesViewModel = viewModel(key = "favorites") {
                SectionsViewModel(
                    loader = container.favoritesRepository::loadFavorites,
                    userDataStore = container.userDataStore,
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
                onSettingClick = { entry ->
                    // The other four entries are placeholders for screens that do not exist. They
                    // stay inert rather than navigating somewhere blank.
                    if (entry == SettingsEntry.Client) navController.navigate(ClientSettingsRoute)
                },
            )
        }

        composable<ClientSettingsRoute> {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            ClientSettingsScreen(
                settings = settings,
                onBack = { navController.popOnce() },
                onSelectTheme = container.settingsStore::setTheme,
                onToggleGestures = container.settingsStore::setBrightnessAndVolumeGestures,
                onToggleRememberBrightness = container.settingsStore::setRememberBrightness,
                // Asked of the platform rather than assumed: iOS can set brightness but not volume,
                // and the row says which of the two it is offering.
                volumeGesturesSupported = rememberPlaybackHardware().canSetVolume,
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
                    userDataStore = container.userDataStore,
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
                onBack = { navController.popOnce() },
            )
        }

        composable<SearchRoute> {
            val viewModel = viewModel {
                SearchViewModel(
                    repository = container.searchRepository,
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            SearchScreen(
                state = state,
                onQueryChange = viewModel::onQueryChange,
                onBack = { navController.popOnce() },
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
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            SectionListScreen(
                // Built from the same two values the row's heading was, rather than carried along
                // as text — see [SectionRoute].
                title = kind.title(route.libraryName),
                cardShape = kind.cardShape,
                state = state,
                onBack = { navController.popOnce() },
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
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            DetailScreen(
                state = state,
                onBack = { navController.popOnce() },
                onPlay = {
                    (state as? DetailUiState.Content)?.detail?.let { detail ->
                        navController.navigate(
                            PlayerRoute(
                                itemId = detail.id,
                                title = detail.title,
                                startPositionTicks = detail.playbackPositionTicks,
                                // Null on anything that is not an episode, which is what leaves the
                                // player showing a film's title on its own.
                                seriesName = detail.seriesLink?.label,
                                seasonNumber = detail.seasonNumber,
                                episodeNumber = detail.episodeNumber,
                                year = detail.year,
                            ),
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
                    title = route.header(),
                    startPositionTicks = route.startPositionTicks,
                    repository = container.playbackRepository,
                    engine = engine,
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()

            // Stop the moment the screen is popped, rather than when it is finally disposed.
            //
            // A popped entry stays composed for the whole exit transition, so onDispose alone left
            // the film playing — audible, and still drawing — over the top of the animation. The
            // back stack itself updates immediately, which is the earliest signal available, and it
            // catches every way out rather than only the back button.
            val currentEntry by navController.currentBackStackEntryAsState()
            LaunchedEffect(currentEntry) {
                if (currentEntry?.id != backStackEntry.id) viewModel.leave()
            }

            // The backstop, for a teardown that never went through the back stack at all — the
            // whole graph going away. Idempotent, so it costs nothing after the effect above.
            DisposableEffect(viewModel) {
                onDispose { viewModel.stop() }
            }

            PlayerScreen(
                state = state,
                positionMs = positionMs,
                engine = engine,
                onBack = { navController.popOnce() },
                onPlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onSeekBy = viewModel::seekBy,
                onRetry = viewModel::load,
                onControlsVisibleChange = viewModel::setControlsVisible,
                onOpenMenu = viewModel::openMenu,
                onCloseMenu = viewModel::closeMenu,
                onSelectAudio = viewModel::selectAudio,
                onSelectSubtitle = viewModel::selectSubtitle,
                onSelectQuality = viewModel::selectQuality,
                onToggleFullscreen = viewModel::toggleFullscreen,
                onToggleDebugInfo = viewModel::toggleDebugInfo,
                gesturesEnabled = settings.brightnessAndVolumeGestures,
                // Only when asked for. The value is recorded either way, so switching the setting
                // on applies the brightness already chosen instead of waiting for the next drag.
                initialBrightness = settings.lastBrightness.takeIf { settings.rememberBrightness },
                onBrightnessSettled = container.settingsStore::setLastBrightness,
            )
        }

        composable<PersonRoute> { backStackEntry ->
            val personId = backStackEntry.toRoute<PersonRoute>().personId
            val viewModel = viewModel(key = personId) {
                PersonViewModel(
                    personId = personId,
                    repository = container.personRepository,
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            PersonScreen(
                state = state,
                onBack = { navController.popOnce() },
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
                    userDataStore = container.userDataStore,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            PersonCreditsScreen(
                personName = route.personName,
                kind = kind,
                state = state,
                onBack = { navController.popOnce() },
                onLoadMore = viewModel::loadNextPage,
                onRetry = viewModel::retry,
                onCreditClick = { navController.navigate(DetailRoute(it.id)) },
            )
        }
    }
}
