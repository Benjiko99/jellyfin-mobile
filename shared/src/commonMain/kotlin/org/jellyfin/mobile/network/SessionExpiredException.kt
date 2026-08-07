package org.jellyfin.mobile.network

import org.jellyfin.mobile.domain.LocalizedError
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.error_session_expired

/**
 * The server rejected our access token.
 *
 * Persisted tokens outlive the app, so this is a normal condition rather than an edge case: the
 * user can revoke the device from the server dashboard, or the server can be reinstalled, and the
 * next launch will start with a token that is no longer valid.
 */
class SessionExpiredException(cause: Throwable? = null) :
    Exception("The session has expired. Sign in again.", cause), LocalizedError {
    override val uiText: UiText = UiText.Resource(Res.string.error_session_expired)
}
