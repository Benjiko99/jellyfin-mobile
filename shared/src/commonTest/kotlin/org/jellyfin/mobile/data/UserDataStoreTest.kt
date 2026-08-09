package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.UserDataChange
import org.jellyfin.mobile.network.testApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun MockRequestHandleScope.jsonResponse(body: String) = respond(
    content = body,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

/** Answers each path with its own body, since these flows make more than one request. */
private fun routedEngine(vararg routes: Pair<String, String>) = MockEngine { request ->
    val body = routes.firstOrNull { request.url.encodedPath.startsWith(it.first) }?.second
        ?: error("no stubbed response for ${request.url}")
    jsonResponse(body)
}

class UserDataStoreTest {
    /**
     * Starts listening before anything is published, and waits for exactly [count] changes.
     *
     * Both halves matter. The flow has no replay, so a collector that subscribed late would see an
     * empty list rather than a failure; and the ancestor refresh runs on the store's own scope over
     * a real HTTP round trip, which no amount of advancing virtual time will wait for.
     */
    private fun TestScope.awaitChanges(
        store: UserDataStore,
        count: Int,
    ): Deferred<List<UserDataChange>> =
        async(UnconfinedTestDispatcher(testScheduler)) { store.changes.take(count).toList() }

    @Test
    fun `marking played broadcasts what the server said, not what was asked for`() = runTest {
        // The toggle asked for played; the server decides, and here it says the item is only
        // partway through. Every screen has to follow the server's answer, not the request.
        val engine = routedEngine(
            "/UserPlayedItems" to
                """{"Played":false,"IsFavorite":true,"PlayedPercentage":40.0,"PlaybackPositionTicks":99}""",
        )
        val store = UserDataStore(testApi(engine), backgroundScope)
        val changes = awaitChanges(store, count = 1)

        val played = store.setPlayed("movie-1", played = true)

        assertFalse(played)
        val change = changes.await().single()
        assertEquals("movie-1", change.itemId)
        assertFalse(change.played)
        assertTrue(change.isFavorite)
        assertEquals(0.4f, change.progress)
        assertEquals(99, change.playbackPositionTicks)
        assertFalse(change.cascadedToChildren)
    }

    @Test
    fun `an episode's season and series are re-read, because only the server knows the new counts`() = runTest {
        val engine = routedEngine(
            "/UserPlayedItems" to """{"Played":true,"PlayedPercentage":100.0}""",
            "/Items" to """{"Items":[
                {"Id":"season-1","Type":"Season","UserData":{"Played":false,"UnplayedItemCount":4}},
                {"Id":"series-1","Type":"Series","UserData":{"Played":false,"UnplayedItemCount":9}}
            ],"TotalRecordCount":2,"StartIndex":0}""",
        )
        val store = UserDataStore(testApi(engine), backgroundScope)
        val changes = awaitChanges(store, count = 3)

        store.setPlayed("episode-1", played = true, ancestorIds = listOf("season-1", "series-1"))

        val recorded = changes.await()
        assertEquals(listOf("episode-1", "season-1", "series-1"), recorded.map { it.itemId })
        assertEquals(listOf(null, 4, 9), recorded.map { it.unplayedItemCount })
        // One request for both ancestors, not one each.
        val refresh = engine.requestHistory.single { it.url.encodedPath == "/Items" }
        assertEquals("season-1,series-1", refresh.url.parameters["ids"])
    }

    @Test
    fun `marking a container played says so, since its children changed with it`() = runTest {
        val engine = routedEngine("/UserPlayedItems" to """{"Played":true}""")
        val store = UserDataStore(testApi(engine), backgroundScope)
        val changes = awaitChanges(store, count = 1)

        store.setPlayed("series-1", played = true, cascadesToChildren = true)

        assertTrue(changes.await().single().cascadedToChildren)
        // No ancestors were named, so nothing beyond the toggle itself was requested.
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun `a refresh chases the ancestors off the response it just read`() = runTest {
        // What the player is left holding after playback: an item id, and no idea where it sits.
        var call = 0
        val engine = MockEngine {
            jsonResponse(
                when (call++) {
                    0 ->
                        """{"Items":[{"Id":"episode-1","SeasonId":"season-1","SeriesId":"series-1",
                        "UserData":{"Played":true}}],"TotalRecordCount":1,"StartIndex":0}"""

                    else ->
                        """{"Items":[{"Id":"season-1","UserData":{"UnplayedItemCount":2}},
                        {"Id":"series-1","UserData":{"UnplayedItemCount":7}}],
                        "TotalRecordCount":2,"StartIndex":0}"""
                },
            )
        }
        val store = UserDataStore(testApi(engine), backgroundScope)
        val changes = awaitChanges(store, count = 3)

        store.refresh(listOf("episode-1"))

        assertEquals(listOf("episode-1", "season-1", "series-1"), changes.await().map { it.itemId })
        assertEquals("season-1,series-1", engine.requestHistory[1].url.parameters["ids"])
    }

    @Test
    fun `a failed refresh does not take the toggle down with it`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.startsWith("/UserFavoriteItems")) {
                jsonResponse("""{"IsFavorite":true}""")
            } else {
                error("network down")
            }
        }
        val store = UserDataStore(testApi(engine), backgroundScope)
        val changes = awaitChanges(store, count = 1)

        assertTrue(store.setFavorite("item-1", favorite = true))
        store.refresh(listOf("series-1"))

        assertEquals(listOf("item-1"), changes.await().map { it.itemId })
    }
}
