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
}
