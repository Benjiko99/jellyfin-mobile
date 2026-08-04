package org.jellyfin.mobile.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.storage.InMemoryPreferencesDataStore
import org.jellyfin.mobile.storage.SessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val EMPTY_QUERY_RESULT = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""

class JellyfinApiTest {
    private fun jsonEngine(body: String) = MockEngine {
        respond(
            content = body,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    private fun apiWith(engine: MockEngine): JellyfinApi {
        val session = JellyfinSession(
            store = SessionStore(InMemoryPreferencesDataStore()),
            scope = CoroutineScope(Dispatchers.Unconfined),
        ).apply {
            authenticated(
                Session(
                    serverUrl = "http://jellyfin.test",
                    accessToken = "token-abc",
                    userId = "user-1",
                    userName = "ben",
                ),
            )
        }
        val client = createHttpClient(
            session = session,
            clientInfo = ClientInfo(name = "Test Client", version = "1.2.3"),
            deviceInfo = DeviceInfo(name = "Test Device", id = "device-id"),
            engine = engine,
        )
        return JellyfinApi(client, session)
    }

    @Test
    fun `a rejected token surfaces as SessionExpiredException`() = runTest {
        // The case that matters once tokens are persisted: a stored token the server no longer accepts.
        val api = apiWith(MockEngine { respondError(HttpStatusCode.Unauthorized) })

        assertFailsWith<SessionExpiredException> { api.userViews() }
    }

    @Test
    fun `sends the MediaBrowser authorization header`() = runTest {
        val engine = jsonEngine(EMPTY_QUERY_RESULT)

        apiWith(engine).userViews()

        assertEquals(
            "MediaBrowser Client=\"Test Client\", Device=\"Test Device\", " +
                "DeviceId=\"device-id\", Version=\"1.2.3\", Token=\"token-abc\"",
            engine.requestHistory.single().headers[HttpHeaders.Authorization],
        )
    }

    @Test
    fun `resume items request carries the parameters the home screen depends on`() = runTest {
        val engine = jsonEngine(EMPTY_QUERY_RESULT)

        apiWith(engine).resumeItems(limit = 12)

        val url = engine.requestHistory.single().url
        assertEquals("/UserItems/Resume", url.encodedPath)
        assertEquals("user-1", url.parameters["userId"])
        assertEquals("12", url.parameters["limit"])
        assertEquals("Video", url.parameters["mediaTypes"])
        // Array parameters are comma-joined, not repeated.
        assertEquals("Primary,Backdrop,Thumb", url.parameters["enableImageTypes"])
    }

    @Test
    fun `next up excludes resumable episodes so they do not appear in two rows`() = runTest {
        val engine = jsonEngine(EMPTY_QUERY_RESULT)

        apiWith(engine).nextUp(limit = 24)

        val url = engine.requestHistory.single().url
        assertEquals("/Shows/NextUp", url.encodedPath)
        assertEquals("false", url.parameters["enableResumable"])
    }

    @Test
    fun `latest items decodes a bare array`() = runTest {
        // Unlike the other list endpoints, /Items/Latest is not wrapped in a QueryResult.
        val engine = jsonEngine("""[{"Id":"1","Name":"Dune","Type":"Movie"}]""")

        val items = apiWith(engine).latestItems(parentId = "lib-1", limit = 16, groupItems = false)

        assertEquals("Dune", items.single().name)
    }

    @Test
    fun `header value escaping keeps quoted-string syntax intact`() {
        // A device name with a quote and an emoji: both are ordinary in the real world and both
        // would otherwise produce a header the HTTP client rejects.
        val header = buildAuthorizationHeader(
            client = ClientInfo(name = "Jellyfin Mobile", version = "0.1.0"),
            device = DeviceInfo(name = "Ben\"s iPhone 📱", id = "abc"),
            accessToken = null,
        )

        assertEquals(
            "MediaBrowser Client=\"Jellyfin Mobile\", Device=\"Ben\\\"s iPhone ??\", " +
                "DeviceId=\"abc\", Version=\"0.1.0\"",
            header,
        )
    }
}
