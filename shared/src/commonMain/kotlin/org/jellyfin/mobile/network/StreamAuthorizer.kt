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
     * [url] with the access token in its query string, for engines that cannot send a header.
     *
     * libVLC is the reason this exists: it opens HTTP sources itself and offers no way to set an
     * arbitrary request header, so the token has to travel as `api_key` instead. That is a route the
     * server plainly supports — the `TranscodingUrl` it hands back carries `api_key` in exactly this
     * form — rather than something invented here.
     *
     * It is second best, and only used where the first is unavailable: a query parameter ends up in
     * server access logs and in a proxy's, where a header usually does not. The same host check
     * applies for the same reason, and a URL that already carries a key is left alone — the server
     * built that one.
     */
    fun authorizedUrl(url: String): String {
        val serverUrl = session.serverUrl ?: return url
        if (!sameHost(url, serverUrl)) return url
        val token = session.accessToken?.takeUnless { it.isEmpty() } ?: return url
        if (url.contains("api_key=", ignoreCase = true)) return url

        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator$API_KEY_PARAMETER=$token"
    }

    private companion object {
        const val API_KEY_PARAMETER = "api_key"
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
