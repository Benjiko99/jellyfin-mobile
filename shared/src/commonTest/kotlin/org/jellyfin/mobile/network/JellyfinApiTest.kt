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
import kotlin.test.assertNull

private const val EMPTY_QUERY_RESULT = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""

class JellyfinApiTest {
    private fun apiWith(engine: MockEngine): JellyfinApi = testApi(engine)

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
    fun `seasons and episodes exclude entries the library does not actually have`() = runTest {
        val seasons = jsonEngine(EMPTY_QUERY_RESULT)
        apiWith(seasons).seasons("series-1")
        seasons.requestHistory.single().url.let {
            assertEquals("/Shows/series-1/Seasons", it.encodedPath)
            // Without this the server also returns seasons it only knows about from metadata.
            assertEquals("false", it.parameters["isMissing"])
        }

        val episodes = jsonEngine(EMPTY_QUERY_RESULT)
        apiWith(episodes).episodes("series-1", seasonId = "season-2")
        episodes.requestHistory.single().url.let {
            assertEquals("/Shows/series-1/Episodes", it.encodedPath)
            assertEquals("season-2", it.parameters["seasonId"])
            assertEquals("false", it.parameters["isMissing"])
        }
    }

    @Test
    fun `omitting the season returns every episode of the series`() = runTest {
        val engine = jsonEngine(EMPTY_QUERY_RESULT)

        apiWith(engine).episodes("series-1", seasonId = null)

        assertNull(engine.requestHistory.single().url.parameters["seasonId"])
    }

    @Test
    fun `filmography queries by person id`() = runTest {
        val engine = jsonEngine(EMPTY_QUERY_RESULT)

        apiWith(engine).items(
            personIds = listOf("person-1"),
            includeItemTypes = listOf("Movie"),
            sortBy = listOf("PremiereDate", "SortName"),
            sortOrder = listOf("Descending"),
            limit = 100,
        )

        val url = engine.requestHistory.single().url
        assertEquals("/Items", url.encodedPath)
        assertEquals("person-1", url.parameters["personIds"])
        assertEquals("Movie", url.parameters["includeItemTypes"])
        assertEquals("PremiereDate,SortName", url.parameters["sortBy"])
        // Without recursion the query only looks at the library's top level and finds nothing.
        assertEquals("true", url.parameters["recursive"])
    }

    @Test
    fun `favourite uses POST to add and DELETE to remove`() = runTest {
        val added = jsonEngine("""{"IsFavorite":true,"Played":false}""")
        apiWith(added).setFavorite("item-1", favorite = true)
        added.requestHistory.single().let {
            assertEquals("POST", it.method.value)
            assertEquals("/UserFavoriteItems/item-1", it.url.encodedPath)
            assertEquals("user-1", it.url.parameters["userId"])
        }

        val removed = jsonEngine("""{"IsFavorite":false,"Played":false}""")
        apiWith(removed).setFavorite("item-1", favorite = false)
        assertEquals("DELETE", removed.requestHistory.single().method.value)
    }

    @Test
    fun `marking played returns the server's resulting state`() = runTest {
        val engine = jsonEngine("""{"IsFavorite":false,"Played":true,"PlayedPercentage":100.0}""")

        val userData = apiWith(engine).setPlayed("series-1", played = true)

        assertEquals("POST", engine.requestHistory.single().method.value)
        assertEquals("/UserPlayedItems/series-1", engine.requestHistory.single().url.encodedPath)
        assertEquals(true, userData.played)
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
