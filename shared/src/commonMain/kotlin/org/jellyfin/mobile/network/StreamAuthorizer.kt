package org.jellyfin.mobile.network

import io.ktor.http.Url

/**
 * Supplies the `Authorization` header for stream requests made by the playback engine.
 *
 * The engine fetches media itself rather than going through our [io.ktor.client.HttpClient], so it
 * has no default headers. It must not simply attach the token to everything either: a media source
 * can point at a third-party host (live TV tuners, remote shares, a CDN a redirect lands on), and
 * sending the user's access token there would leak it. The host check is the whole point of this
 * class, which is why it lives in shared, tested code rather than in each engine.
 */
class StreamAuthorizer(
    private val session: JellyfinSession,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
) {
    /** Headers for [url], or empty when it does not belong to the signed-in server. */
    fun headersFor(url: String): Map<String, String> {
        val serverUrl = session.serverUrl ?: return emptyMap()
        if (!sameHost(url, serverUrl)) return emptyMap()
        return mapOf(
            "Authorization" to buildAuthorizationHeader(clientInfo, deviceInfo, session.accessToken),
        )
    }

    /**
     * Compares host and port only. The scheme is ignored because a server reached over HTTPS may
     * still hand back an HTTP stream URL, and the path obviously varies.
     */
    private fun sameHost(url: String, serverUrl: String): Boolean {
        val target = runCatching { Url(url) }.getOrNull() ?: return false
        val server = runCatching { Url(serverUrl) }.getOrNull() ?: return false
        return target.host.equals(server.host, ignoreCase = true) && target.port == server.port
    }
}
