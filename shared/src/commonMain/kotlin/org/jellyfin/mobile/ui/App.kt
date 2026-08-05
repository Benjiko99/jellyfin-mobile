package org.jellyfin.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import org.jellyfin.mobile.ui.detail.DetailScreen
import org.jellyfin.mobile.ui.detail.DetailViewModel
import org.jellyfin.mobile.ui.home.HomeScreen
import org.jellyfin.mobile.ui.home.HomeViewModel
import org.jellyfin.mobile.ui.login.LoginScreen
import org.jellyfin.mobile.ui.login.LoginViewModel
import org.jellyfin.mobile.ui.person.PersonScreen
import org.jellyfin.mobile.ui.person.PersonViewModel
import org.jellyfin.mobile.ui.theme.AppTheme

@Composable
fun App(sessionFilePath: String) {
    val container = remember { AppContainer(sessionFilePath) }

    // Images go through the same authenticated client as API calls, so the access token stays in
    // the Authorization header instead of being appended to every image URL.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { container.httpClient })) }
            .build()
    }

    LaunchedEffect(container) {
        container.session.restore()
    }

    AppTheme {
        val restored by container.session.restored.collectAsStateWithLifecycle()
        val session by container.session.state.collectAsStateWithLifecycle()

        when {
            // Reading the stored session is a disk hit; showing the login screen first would make
            // every launch flash a sign-in form the user does not need.
            !restored -> Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            session == null -> {
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

            else -> SignedInNavHost(container)
        }
    }
}

@Composable
private fun SignedInNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            val viewModel = viewModel {
                HomeViewModel(
                    repository = container.homeRepository,
                    onSessionExpired = container.session::signOut,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRetry = viewModel::load,
                onItemClick = { navController.navigate(DetailRoute(it.id)) },
                onSignOut = container.session::signOut,
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
            )
        }
    }
}
