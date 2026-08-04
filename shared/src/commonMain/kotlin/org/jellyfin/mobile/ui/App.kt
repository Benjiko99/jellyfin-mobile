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
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import org.jellyfin.mobile.AppContainer
import org.jellyfin.mobile.ui.home.HomeScreen
import org.jellyfin.mobile.ui.home.HomeViewModel
import org.jellyfin.mobile.ui.login.LoginScreen
import org.jellyfin.mobile.ui.login.LoginViewModel
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

            else -> {
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
                    // Item detail and playback are Phase 3/4 — see PLAN.md.
                    onItemClick = {},
                    onSignOut = container.session::signOut,
                )
            }
        }
    }
}
