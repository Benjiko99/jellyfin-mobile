package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.LibraryRowTarget
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.network.testSession
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val EMPTY_RESULT = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""

private fun items(vararg names: String) = names.joinToString(
    prefix = """{"Items":[""",
    postfix = """],"TotalRecordCount":${names.size},"StartIndex":0}""",
) { """{"Id":"${it.lowercase().replace(' ', '-')}","Name":"$it","Type":"Movie"}""" }

/**
 * Routes each of the tabs' endpoints separately, so a fan-out can be asserted as a whole rather
 * than one request at a time.
 */
private class RowsEngine(
    private val resume: String = EMPTY_RESULT,
    private val nextUp: String = EMPTY_RESULT,
    private val latest: String = "[]",
    private val recommendations: String = "[]",
    private val genres: String = EMPTY_RESULT,
    private val studios: String = EMPTY_RESULT,
    private val upcoming: String = EMPTY_RESULT,
    private val items: String = EMPTY_RESULT,
    private val itemsStatus: HttpStatusCode = HttpStatusCode.OK,
) {
    val urls = mutableListOf<String>()

    val engine = MockEngine { request ->
        val url = request.url.toString()
        urls += url
        val json = ContentType.Application.Json.toString()
        val body = when {
            "/UserItems/Resume" in url -> resume
            "/Shows/NextUp" in url -> nextUp
            "/Items/Latest" in url -> latest
            "/Movies/Recommendations" in url -> recommendations
            "/Genres" in url -> genres
            "/Studios" in url -> studios
            "/Shows/Upcoming" in url -> upcoming
            else -> if (itemsStatus != HttpStatusCode.OK) {
                return@MockEngine respondError(itemsStatus)
            } else {
                items
            }
        }
        respond(content = body, headers = headersOf(HttpHeaders.ContentType, json))
    }
}

class LibraryRowsRepositoryTest {
    private fun repositoryWith(engine: RowsEngine) =
        LibraryRowsRepository(testApi(engine.engine), testSession())

    // ---- Suggestions -------------------------------------------------------------------------

    @Test
    fun `movie suggestions are continue watching, recently added and the server's picks`() = runTest {
        val engine = RowsEngine(
            resume = items("The Cartographer"),
            latest = """[{"Id":"m2","Name":"Harbour Lights","Type":"Movie"}]""",
            recommendations = """
                [{
                  "CategoryId": "cat-1",
                  "RecommendationType": "SimilarToRecentlyPlayed",
                  "BaselineItemName": "Nine Winters",
                  "Items": [{"Id":"m3","Name":"A Quiet Signal","Type":"Movie"}]
                }]
            """.trimIndent(),
        )

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.SuggestionsMovies, startIndex = 0)

        assertEquals(
            listOf("Continue Watching", "Recently Added", "Because you watched Nine Winters"),
            page.rows.map { it.title },
        )
        assertTrue(page.endReached, "suggestions have no second page")
    }

    /** The heading is built from two fields; a type we have no wording for still makes a usable row. */
    @Test
    fun `an unfamiliar recommendation type keeps the film it was built from`() = runTest {
        val engine = RowsEngine(
            recommendations = """
                [{
                  "RecommendationType": "SomethingNewInAFutureRelease",
                  "BaselineItemName": "Nine Winters",
                  "Items": [{"Id":"m3","Name":"A Quiet Signal","Type":"Movie"}]
                }]
            """.trimIndent(),
        )

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.SuggestionsMovies, startIndex = 0)

        assertEquals(listOf("Suggested by Nine Winters"), page.rows.map { it.title })
    }

    /**
     * `/Movies/Recommendations` is built from viewing history, so a fresh account gets nothing from
     * it — and a row that fails must not take the rows either side of it down.
     */
    @Test
    fun `a suggestions row that fails leaves the others standing`() = runTest {
        val engine = RowsEngine(
            resume = items("The Cartographer"),
            recommendations = """{"not":"an array"}""",
        )

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.SuggestionsMovies, startIndex = 0)

        assertEquals(listOf("Continue Watching"), page.rows.map { it.title })
    }

    @Test
    fun `empty rows are dropped rather than shown empty`() = runTest {
        val page = repositoryWith(RowsEngine())
            .loadRows("lib-1", LibraryTab.SuggestionsMovies, startIndex = 0)

        assertTrue(page.rows.isEmpty())
    }

    /** A TV library has no recommendations endpoint; Next Up stands in for one. */
    @Test
    fun `show suggestions use next up instead of recommendations`() = runTest {
        val engine = RowsEngine(nextUp = items("Northern Line"))

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.SuggestionsShows, startIndex = 0)

        assertEquals(listOf("Next Up"), page.rows.map { it.title })
        assertTrue(engine.urls.none { "/Movies/Recommendations" in it })
    }

    @Test
    fun `suggestions are scoped to the library, not the whole server`() = runTest {
        val engine = RowsEngine()

        repositoryWith(engine).loadRows("lib-1", LibraryTab.SuggestionsShows, startIndex = 0)

        assertTrue(
            engine.urls.all { "parentId=lib-1" in it },
            "every suggestions request should be scoped: ${engine.urls}",
        )
    }

    // ---- Upcoming ----------------------------------------------------------------------------

    @Test
    fun `upcoming episodes are grouped into one row per air date`() = runTest {
        val engine = RowsEngine(
            upcoming = """
                {
                  "Items": [
                    {"Id":"e1","Name":"Low Tide","Type":"Episode","PremiereDate":"2026-08-14T00:00:00.0000000Z"},
                    {"Id":"e2","Name":"High Water","Type":"Episode","PremiereDate":"2026-08-14T00:00:00.0000000Z"},
                    {"Id":"e3","Name":"The Undertow","Type":"Episode","PremiereDate":"2026-09-01T00:00:00.0000000Z"}
                  ],
                  "TotalRecordCount": 3,
                  "StartIndex": 0
                }
            """.trimIndent(),
        )

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.Upcoming, startIndex = 0)

        assertEquals(listOf("14 August 2026", "1 September 2026"), page.rows.map { it.title })
        assertEquals(2, page.rows.first().items.size)
    }

    /** An episode with no announced date is still an upcoming episode. */
    @Test
    fun `an episode without an air date gets its own row rather than being dropped`() = runTest {
        val engine = RowsEngine(
            upcoming = """
                {
                  "Items": [{"Id":"e1","Name":"Untitled","Type":"Episode"}],
                  "TotalRecordCount": 1,
                  "StartIndex": 0
                }
            """.trimIndent(),
        )

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.Upcoming, startIndex = 0)

        assertEquals(listOf("Date to be announced"), page.rows.map { it.title })
    }

    // ---- Genres and networks -------------------------------------------------------------------

    @Test
    fun `each genre becomes a row that leads back into the library`() = runTest {
        val engine = RowsEngine(genres = items("Drama", "Comedy"), items = items("The Cartographer"))

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.MovieGenres, startIndex = 0)

        assertEquals(listOf("Drama", "Comedy"), page.rows.map { it.title })
        assertEquals(LibraryRowTarget.Genre("Drama"), page.rows.first().target)
        // The genre list, plus one preview query per genre.
        assertEquals(3, engine.urls.size)
        assertTrue(engine.urls.any { "genres=Drama" in it })
    }

    @Test
    fun `genres are asked for by what carries them, not by the genre itself`() = runTest {
        val engine = RowsEngine(genres = items("Drama"))

        repositoryWith(engine).loadRows("lib-1", LibraryTab.ShowGenres, startIndex = 0)

        val genreRequest = engine.urls.first { "/Genres" in it }
        assertContains(genreRequest, "includeItemTypes=Series")
        assertContains(genreRequest, "parentId=lib-1")
    }

    /** Studio names collide across regions, so the row carries the id `/Items` matches on. */
    @Test
    fun `networks are matched by id`() = runTest {
        val engine = RowsEngine(studios = items("Channel Four"), items = items("Northern Line"))

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.Networks, startIndex = 0)

        assertEquals(
            LibraryRowTarget.Studio("channel-four", "Channel Four"),
            page.rows.single().target,
        )
        assertTrue(engine.urls.any { "studioIds=channel-four" in it })
    }

    /**
     * A row with no items would claim the genre holds nothing, which is a different statement from
     * "we could not find out".
     */
    @Test
    fun `a genre whose preview fails is dropped rather than shown empty`() = runTest {
        val engine = RowsEngine(
            genres = items("Drama"),
            itemsStatus = HttpStatusCode.InternalServerError,
        )

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.MovieGenres, startIndex = 0)

        assertTrue(page.rows.isEmpty())
    }

    @Test
    fun `a short page of genres is the end of the list`() = runTest {
        val engine = RowsEngine(genres = items("Drama"), items = items("The Cartographer"))

        val page = repositoryWith(engine).loadRows("lib-1", LibraryTab.MovieGenres, startIndex = 0)

        assertTrue(page.endReached)
    }

    @Test
    fun `paging a genre list carries the start index`() = runTest {
        val engine = RowsEngine(genres = EMPTY_RESULT)

        repositoryWith(engine).loadRows("lib-1", LibraryTab.MovieGenres, startIndex = 12)

        assertContains(engine.urls.first { "/Genres" in it }, "startIndex=12")
    }

    /** Nothing but a grid should reach this repository; a wrong tab is a programming error. */
    @Test
    fun `a grid tab is not a rows tab`() = runTest {
        val error = runCatching {
            repositoryWith(RowsEngine()).loadRows("lib-1", LibraryTab.Movies, startIndex = 0)
        }.exceptionOrNull()

        assertContains(error?.message.orEmpty(), "Movies is a grid")
        assertNull(error as? NullPointerException)
    }
}
