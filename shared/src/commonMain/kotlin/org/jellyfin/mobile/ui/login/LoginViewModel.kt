package org.jellyfin.mobile.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.Session
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.login_error_enter_address
import org.jellyfin.mobile.resources.login_error_failed
import org.jellyfin.mobile.resources.login_error_no_token
import org.jellyfin.mobile.resources.login_error_unreachable

data class LoginState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val serverName: String? = null,
    val error: UiText? = null,
)

/**
 * Minimal connect + login, enough to reach the home screen.
 *
 * This is *not* the real connect flow — PLAN.md Phase 2 covers local network discovery, address
 * candidate generation, recommended-server scoring, Quick Connect, and persisting servers/users.
 * Ported from jellyfin-android's `ConnectionHelper` / `ServerSelection` at that point.
 */
class LoginViewModel(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val current = _state.value
        if (current.busy) return

        val serverUrl = normalizeServerUrl(current.serverUrl)
        if (serverUrl == null) {
            _state.update { it.copy(error = UiText.Resource(Res.string.login_error_enter_address)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }

            // Check the server responds before sending credentials, so a typo'd address reports
            // "can't reach server" rather than "login failed".
            val systemInfo = runCatching { api.publicSystemInfo(serverUrl) }
            if (systemInfo.isFailure) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = UiText.Resource(
                            Res.string.login_error_unreachable,
                            listOf(serverUrl),
                        ),
                    )
                }
                return@launch
            }

            runCatching {
                api.authenticateByName(serverUrl, current.username, current.password)
            }.onSuccess { result ->
                val accessToken = result.accessToken
                val user = result.user
                if (accessToken == null || user == null) {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = UiText.Resource(Res.string.login_error_no_token),
                        )
                    }
                    return@onSuccess
                }
                session.authenticated(
                    Session(
                        serverUrl = serverUrl,
                        accessToken = accessToken,
                        userId = user.id,
                        userName = user.name.orEmpty(),
                        userImageTag = user.primaryImageTag,
                    ),
                )
            }.onFailure {
                _state.update {
                    it.copy(busy = false, error = UiText.Resource(Res.string.login_error_failed))
                }
            }
        }
    }
}

/**
 * Accepts what people actually type: `192.168.1.10:8096`, `jellyfin.example.com`,
 * `https://jellyfin.example.com/`. Defaults to `http` because the overwhelmingly common case is a
 * server on the local network without TLS.
 */
internal fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    return when {
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        else -> "http://$trimmed"
    }
}
