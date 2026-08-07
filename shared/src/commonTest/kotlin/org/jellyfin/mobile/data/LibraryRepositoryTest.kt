package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.Alphabet
import org.jellyfin.mobile.domain.LibraryFilters
import org.jellyfin.mobile.domain.LibrarySort
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.domain.PlayedFilter
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.network.testSession
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EMPTY_RESULT = """{"Items":[],"TotalRecordCount":0,"StartIndex":0}"""

private const val FILTERS = """
    {
      "Genres": ["Drama", "Comedy"],
      "OfficialRatings": ["PG", "R"],
      "Years": [1999, 2024, 2011],
      "Tags": ["rewatch"]
    }
"""

class LibraryRepositoryTest {
    private fun repositoryWith(engine: MockEngine) =
        LibraryRepository(testApi(engine), testSession())

    private fun jsonEngine(body: String) = MockEngine {
        respond(
            content = body,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    private suspend fun urlFor(
        tab: LibraryTab = LibraryTab.Movies,
        filters: LibraryFilters = LibraryFilters(),
        startLetter: String? = null,
        startIndex: Int = 0,
    ): String {
        val engine = jsonEngine(EMPTY_RESULT)
        repositoryWith(engine).loadPage(
            libraryId = "lib-1",
            tab = tab,
            filters = filters,
            startLetter = startLetter,
            startIndex = startIndex,
        )
        return engine.requestHistory.single().url.toString()
    }

    @Test
    fun `scopes the query to the library and the tab's item type`() = runTest {
        val url = urlFor(tab = LibraryTab.Shows)

        assertContains(url, "parentId=lib-1")
        assertContains(url, "includeItemTypes=Series")
        assertContains(url, "recursive=true")
    }

    /**
     * Box sets live in a library of their own, so scoping the Collections tab to the movie library
     * showing it would return nothing at all.
     */
    @Test
    fun `the collections tab is not scoped to the current library`() = runTest {
        val url = urlFor(tab = LibraryTab.Collections)

        assertFalse("parentId" in url, "expected no parentId, got $url")
        assertContains(url, "includeItemTypes=BoxSet")
    }

    @Test
    fun `a letter from the rail becomes nameStartsWith`() = runTest {
        val url = urlFor(startLetter = "M")

        assertContains(url, "nameStartsWith=M")
        assertFalse("nameLessThan" in url, "expected no upper bound for a letter, got $url")
    }

    /**
     * "#" is everything sorting before "a" — digits and brackets, where "2001" and "[REC]" land.
     * Ported from jellyfin-android's AlphaBrowser, which drives `/Items` the same way.
     */
    @Test
    fun `the hash bucket becomes nameLessThan rather than a prefix`() = runTest {
        val url = urlFor(startLetter = Alphabet.OTHER)

        assertContains(url, "nameLessThan=a")
        assertFalse("nameStartsWith" in url, "expected no prefix for '#', got $url")
    }

    @Test
    fun `sorting carries its direction`() = runTest {
        val url = urlFor(filters = LibraryFilters(sort = LibrarySort.DateAdded, descending = true))

        assertContains(url, "sortBy=DateCreated")
        assertContains(url, "sortOrder=Descending")
    }

    @Test
    fun `filters become query parameters`() = runTest {
        val url = urlFor(
            filters = LibraryFilters(
                played = PlayedFilter.Unplayed,
                favoritesOnly = true,
                genres = setOf("Drama"),
                officialRatings = setOf("PG"),
                years = setOf(1999),
            ),
        )

        assertContains(url, "isPlayed=false")
        assertContains(url, "isFavorite=true")
        assertContains(url, "genres=Drama")
        assertContains(url, "officialRatings=PG")
        assertContains(url, "years=1999")
    }

    /** The Favorites tab is the neighbouring tab's query plus `isFavorite`, not a screen of its own. */
    @Test
    fun `the favorites tab filters itself`() = runTest {
        val url = urlFor(tab = LibraryTab.FavoriteMovies)

        assertContains(url, "isFavorite=true")
        assertContains(url, "includeItemTypes=Movie")
    }

    @Test
    fun `an unfiltered query names none of the filter parameters`() = runTest {
        val url = urlFor()

        assertFalse("isPlayed" in url, "got $url")
        assertFalse("isFavorite" in url, "got $url")
        assertFalse("genres" in url, "got $url")
    }

    /**
     * With `enableTotalRecordCount=false` the server fills `TotalRecordCount` with the size of the
     * page it just returned, so asking on a later page would overwrite the real total with 60.
     */
    @Test
    fun `only the first page asks for a total count`() = runTest {
        assertContains(urlFor(startIndex = 0), "enableTotalRecordCount=true")
        assertContains(urlFor(startIndex = 60), "enableTotalRecordCount=false")
    }

    @Test
    fun `a short page is the end of the list`() = runTest {
        val page = repositoryWith(jsonEngine(EMPTY_RESULT)).loadPage(
            libraryId = "lib-1",
            tab = LibraryTab.Movies,
            filters = LibraryFilters(),
            startLetter = null,
            startIndex = 0,
        )

        assertTrue(page.endReached)
    }

    @Test
    fun `filter options come back newest year first`() = runTest {
        val options = repositoryWith(jsonEngine(FILTERS))
            .loadFilterOptions("lib-1", LibraryTab.Movies)

        assertEquals(listOf("Drama", "Comedy"), options.genres)
        assertEquals(listOf("PG", "R"), options.officialRatings)
        assertEquals(listOf(2024, 2011, 1999), options.years)
    }

    /**
     * The sheet still has the filters that need no server support, so a server that will not answer
     * this leaves it poorer rather than refusing to open it.
     */
    @Test
    fun `a server that cannot list filters leaves the sheet usable`() = runTest {
        val options = repositoryWith(MockEngine { respondError(HttpStatusCode.InternalServerError) })
            .loadFilterOptions("lib-1", LibraryTab.Movies)

        assertTrue(options.isEmpty)
    }
}
