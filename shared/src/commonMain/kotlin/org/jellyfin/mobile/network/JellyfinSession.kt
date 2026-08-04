package org.jellyfin.mobile.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Identifies this app to the server. Shown in the server's dashboard and device list. */
data class ClientInfo(
    val name: String = "Jellyfin Mobile",
    val version: String = "0.1.0",
)

/** Identifies this device to the server. */
data class DeviceInfo(
    val name: String,
    val id: String,
)

/** Platform-provided device identity. */
expect fun platformDeviceInfo(): DeviceInfo

data class Session(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String,
)

/**
 * Holds the currently connected server and authenticated user.
 *
 * In-memory only for now — persistence (multi-server, multi-user, token refresh) lands with the
 * real connect flow in Phase 2. See PLAN.md.
 */
class JellyfinSession {
    private val _state = MutableStateFlow<Session?>(null)
    val state: StateFlow<Session?> = _state.asStateFlow()

    /**
     * Server URL used for requests made *before* authentication (i.e. the login call itself),
     * which is why this is tracked separately from [state].
     */
    var pendingServerUrl: String? = null

    val serverUrl: String? get() = _state.value?.serverUrl ?: pendingServerUrl
    val accessToken: String? get() = _state.value?.accessToken
    val userId: String? get() = _state.value?.userId

    fun authenticated(session: Session) {
        pendingServerUrl = null
        _state.value = session
    }

    fun clear() {
        pendingServerUrl = null
        _state.value = null
    }
}
