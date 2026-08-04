package org.jellyfin.mobile.network

/**
 * The server rejected our access token.
 *
 * Persisted tokens outlive the app, so this is a normal condition rather than an edge case: the
 * user can revoke the device from the server dashboard, or the server can be reinstalled, and the
 * next launch will start with a token that is no longer valid.
 */
class SessionExpiredException(cause: Throwable? = null) :
    Exception("The session has expired. Sign in again.", cause)
