package org.jellyfin.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
fun App() {
    val container = remember { AppContainer() }

    // Images go through the same authenticated client as API calls, so the access token stays in
    // the Authorization header instead of being appended to every image URL.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { container.httpClient })) }
            .build()
    }

    AppTheme {
        val session by container.session.state.collectAsStateWithLifecycle()

        if (session == null) {
            val viewModel = viewModel { LoginViewModel(container.api, container.session) }
            val state by viewModel.state.collectAsStateWithLifecycle()
            LoginScreen(
                state = state,
                onServerUrlChange = viewModel::onServerUrlChange,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSubmit = viewModel::login,
            )
        } else {
            val viewModel = viewModel { HomeViewModel(container.homeRepository) }
            val state by viewModel.state.collectAsStateWithLifecycle()
            HomeScreen(
                state = state,
                onRetry = viewModel::load,
                // Item detail and playback are Phase 3/4 — see PLAN.md.
                onItemClick = {},
            )
        }
    }
}
