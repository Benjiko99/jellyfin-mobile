package org.jellyfin.mobile.network

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun authorizer(session: JellyfinSession = testSession()) = StreamAuthorizer(
    session = session,
    clientInfo = ClientInfo(name = "Test Client", version = "1.2.3"),
    deviceInfo = TEST_DEVICE_INFO,
)

class StreamAuthorizerTest {
    @Test
    fun `authorizes streams on the signed-in server`() {
        val headers = authorizer().headersFor("$TEST_SERVER_URL/Videos/item-1/stream?static=true")

        assertContains(headers.getValue("Authorization"), "Token=\"token-abc\"")
        assertContains(headers.getValue("Authorization"), "DeviceId=\"device-id\"")
    }

    @Test
    fun `never sends the token to another host`() {
        // A media source can point anywhere — a live TV tuner, a remote share, or a CDN a redirect
        // lands on. Attaching the token there would hand the user's credentials to a third party.
        assertEquals(emptyMap(), authorizer().headersFor("http://tuner.local/stream.m3u8"))
        assertEquals(emptyMap(), authorizer().headersFor("https://cdn.example.com/video.mp4"))
    }

    @Test
    fun `treats a different port on the same host as a different server`() {
        assertEquals(emptyMap(), authorizer().headersFor("http://jellyfin.test:9999/Videos/item-1/stream"))
    }

    @Test
    fun `ignores scheme, since an https server may hand back an http stream`() {
        val headers = authorizer().headersFor("http://jellyfin.test/Videos/item-1/stream")

        assertTrue(headers.isNotEmpty())
    }

    @Test
    fun `matches the host case-insensitively`() {
        val headers = authorizer().headersFor("http://JELLYFIN.TEST/Videos/item-1/stream")

        assertTrue(headers.isNotEmpty())
    }

    @Test
    fun `sends nothing when signed out`() {
        val session = testSession().apply { signOut() }

        assertEquals(emptyMap(), authorizer(session).headersFor("$TEST_SERVER_URL/Videos/item-1/stream"))
    }

    @Test
    fun `sends nothing for a malformed URL rather than defaulting to authorized`() {
        assertEquals(emptyMap(), authorizer().headersFor("not a url"))
    }

    @Test
    fun `puts the token in the query string for engines that cannot send a header`() {
        val url = authorizer().authorizedUrl("$TEST_SERVER_URL/Videos/item-1/stream?static=true")

        assertEquals("$TEST_SERVER_URL/Videos/item-1/stream?static=true&api_key=token-abc", url)
    }

    @Test
    fun `starts a query string when the URL has none`() {
        val url = authorizer().authorizedUrl("$TEST_SERVER_URL/Videos/item-1/stream")

        assertEquals("$TEST_SERVER_URL/Videos/item-1/stream?api_key=token-abc", url)
    }

    @Test
    fun `never puts the token in a URL belonging to another host`() {
        // Worse than the header case: a query parameter is logged by every proxy on the way.
        val foreign = "https://cdn.example.com/video.mp4"

        assertEquals(foreign, authorizer().authorizedUrl(foreign))
    }

    @Test
    fun `leaves a server-built URL that already carries a key alone`() {
        // Transcoding URLs arrive with `api_key` already in them. Appending a second one would give
        // the server two values for one parameter.
        val transcode = "$TEST_SERVER_URL/videos/item-1/master.m3u8?PlaySessionId=ps-1&api_key=k"

        assertEquals(transcode, authorizer().authorizedUrl(transcode))
    }

    @Test
    fun `leaves the URL alone when signed out`() {
        val session = testSession().apply { signOut() }
        val url = "$TEST_SERVER_URL/Videos/item-1/stream"

        assertEquals(url, authorizer(session).authorizedUrl(url))
    }
}
