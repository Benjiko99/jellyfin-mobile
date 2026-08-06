package org.jellyfin.mobile.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.storage.SessionStore

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
 * Holds the currently connected server and authenticated user, restored from [SessionStore] on
 * launch and written back on sign in.
 *
 * The in-memory [StateFlow] stays the source of truth because the `Authorization` header is built
 * on every request from a non-suspending context; the store is only read once at startup.
 *
 * Still single-server / single-user — multi-server support lands with the real connect flow in
 * Phase 2 (see PLAN.md).
 */
class JellyfinSession(
    private val store: SessionStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<Session?>(null)
    val state: StateFlow<Session?> = _state.asStateFlow()

    /**
     * False until the persisted session has been read. The UI must wait for this, otherwise it
     * flashes the login screen for a frame before restoring.
     */
    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    /**
     * Server URL used for requests made *before* authentication (the login call itself), which is
     * why this is tracked separately from [state].
     */
    var pendingServerUrl: String? = null

    val serverUrl: String? get() = _state.value?.serverUrl ?: pendingServerUrl

    /**
     * The server every request and image URL is built against.
     *
     * Trailing slashes are stripped here so callers concatenating a path cannot produce a double
     * slash — previously each repository repeated the null check and only [JellyfinApi] normalised.
     */
    fun requireServerUrl(): String =
        requireNotNull(serverUrl) { "No server configured" }.trimEnd('/')
    val accessToken: String? get() = _state.value?.accessToken
    val userId: String? get() = _state.value?.userId

    suspend fun restore() {
        if (_restored.value) return
        // A corrupt or unreadable store must not prevent the app from starting; the worst case is
        // that the user signs in again.
        _state.value = runCatching { store.read() }.getOrNull()
        _restored.value = true
    }

    fun authenticated(session: Session) {
        pendingServerUrl = null
        _state.value = session
        scope.launch { store.write(session) }
    }

    fun signOut() {
        pendingServerUrl = null
        _state.value = null
        scope.launch { store.clear() }
    }
}
