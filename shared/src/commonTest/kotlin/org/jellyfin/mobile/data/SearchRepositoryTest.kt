package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.network.testSession
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Answers each category's request separately, and records the URLs so the queries can be asserted. */
private class SearchEngine(
    private val movies: String = "[]",
    private val series: String = "[]",
    private val episodes: String = "[]",
    private val people: String = "[]",
    private val suggestions: String = "[]",
    private val peopleStatus: HttpStatusCode = HttpStatusCode.OK,
    /**
     * What the untyped scan returns. Box sets can only be found in there — asking for them by type
     * alongside a search term does not work — so this is where a collections match comes from.
     */
    private val untyped: String = "[]",
) {
    val urls = mutableListOf<String>()

    val engine = MockEngine { request ->
        val url = request.url.toString()
        urls += url
        val json = ContentType.Application.Json.toString()

        if (url.contains("/Persons")) {
            return@MockEngine if (peopleStatus == HttpStatusCode.OK) {
                respond("""{"Items":$people}""", headers = headersOf(HttpHeaders.ContentType, json))
            } else {
                respondError(peopleStatus)
            }
        }

        val items = when {
            url.contains("/Items/Suggestions") -> suggestions
            url.contains("includeItemTypes=Movie") -> movies
            url.contains("includeItemTypes=Series") -> series
            url.contains("includeItemTypes=Episode") -> episodes
            // No includeItemTypes at all: the untyped scan the collections row is built from.
            !url.contains("includeItemTypes") -> untyped
            else -> "[]"
        }
        respond("""{"Items":$items}""", headers = headersOf(HttpHeaders.ContentType, json))
    }
}

private fun repository(engine: MockEngine): SearchRepository {
    val session = testSession()
    return SearchRepository(testApi(engine, session), session)
}

private fun searchItem(id: String, name: String, type: String) =
    """{"Id":"$id","Name":"$name","Type":"$type"}"""

class SearchRepositoryTest {
    @Test
    fun `searches every category, in a fixed order`() = runTest {
        val search = SearchEngine(
            movies = "[${searchItem("m1", "Batman", "Movie")}]",
            series = "[${searchItem("s1", "Batman: TAS", "Series")}]",
            episodes = "[${searchItem("e1", "Batman", "Episode")}]",
            people = "[${searchItem("p1", "Christian Bale", "Person")}]",
            untyped = "[${searchItem("b1", "Batman Collection", "BoxSet")}]",
        )

        val sections = repository(search.engine).search("batman")

        assertEquals(
            listOf(
                SectionKind.SearchMovies,
                SectionKind.SearchSeries,
                SectionKind.SearchEpisodes,
                SectionKind.SearchCollections,
                SectionKind.SearchPeople,
            ),
            sections.map { it.kind },
        )
    }

    @Test
    fun `sends the term to every category`() = runTest {
        val search = SearchEngine(movies = "[${searchItem("m1", "Batman", "Movie")}]")

        repository(search.engine).search("batman")

        // Five categories, plus the library lookup the collections query needs.
        val queries = search.urls.filterNot { "/UserViews" in it }
        assertEquals(5, queries.size)
        assertTrue(queries.all { "searchTerm=batman" in it }, queries.toString())
    }

    @Test
    fun `collections are picked out of an untyped search, never asked for by type`() = runTest {
        // searchTerm with includeItemTypes=BoxSet comes back as an empty non-JSON body on a server
        // where the term plainly matches a box set, so the row is built from an untyped scan.
        val search = SearchEngine(
            untyped = "[${searchItem("m1", "Batman", "Movie")}," +
                "${searchItem("b1", "Batman", "BoxSet")}," +
                "${searchItem("e1", "Batman", "Episode")}]",
        )

        val sections = repository(search.engine).search("batman")

        assertTrue(search.urls.none { "includeItemTypes=BoxSet" in it }, search.urls.toString())
        val collections = sections.single { it.kind == SectionKind.SearchCollections }
        assertEquals(listOf("b1"), collections.items.map { it.id })
        assertEquals(ItemKind.BoxSet, collections.items.single().kind)
    }

    @Test
    fun `the collections row never offers a More it could not honour`() = runTest {
        // The filter happens here, so the server's startIndex counts the untyped list rather than
        // the box sets in it — the row cannot be paged, so it must not advertise that it can.
        val many = (1..20).joinToString(",") { searchItem("b$it", "Collection $it", "BoxSet") }
        val search = SearchEngine(untyped = "[$many]")

        val collections = repository(search.engine).search("batman").single()

        assertEquals(10, collections.items.size)
        assertFalse(collections.hasMore)
    }

    @Test
    fun `a search matching no collections yields no collections row`() = runTest {
        val search = SearchEngine(
            movies = "[${searchItem("m1", "Batman", "Movie")}]",
            untyped = "[${searchItem("m1", "Batman", "Movie")}]",
        )

        assertEquals(
            listOf(SectionKind.SearchMovies),
            repository(search.engine).search("batman").map { it.kind },
        )
    }

    @Test
    fun `a category with no matches yields no row`() = runTest {
        // Searching an actor's name should come back as one People row, not that plus four empties.
        val search = SearchEngine(people = "[${searchItem("p1", "Christian Bale", "Person")}]")

        val sections = repository(search.engine).search("bale")

        assertEquals(listOf(SectionKind.SearchPeople), sections.map { it.kind })
    }

    @Test
    fun `nothing found anywhere yields no rows`() = runTest {
        assertEquals(emptyList(), repository(SearchEngine().engine).search("zzzz"))
    }

    @Test
    fun `people come from the Persons route and stay people`() = runTest {
        // A recursive /Items query never returns people however it is filtered, and /Persons does
        // not reliably set Type — so the route is separate and the kind is asserted.
        val search = SearchEngine(people = """[{"Id":"p1","Name":"Christian Bale"}]""")

        val sections = repository(search.engine).search("bale")

        assertContains(search.urls.single { "/Persons" in it }, "searchTerm=bale")
        assertEquals(ItemKind.Person, sections.single().items.single().kind)
    }

    @Test
    fun `carries the kind and term so More can page the same search`() = runTest {
        val search = SearchEngine(
            movies = "[${searchItem("m1", "Batman", "Movie")}]",
            people = "[${searchItem("p1", "Christian Bale", "Person")}]",
        )

        val sections = repository(search.engine).search("batman")

        val movies = sections.first { it.id == "search-movies" }
        assertEquals(SectionKind.SearchMovies, movies.kind)
        assertEquals("batman", movies.searchTerm)
        assertEquals(SectionKind.SearchPeople, sections.first { it.id == "search-people" }.kind)
    }

    @Test
    fun `episodes get landscape cards, everything else portrait`() = runTest {
        val search = SearchEngine(
            movies = "[${searchItem("m1", "Batman", "Movie")}]",
            episodes = "[${searchItem("e1", "Batman", "Episode")}]",
        )

        val sections = repository(search.engine).search("batman")

        assertEquals(CardShape.Poster, sections.first { it.id == "search-movies" }.cardShape)
        assertEquals(CardShape.Thumb, sections.first { it.id == "search-episodes" }.cardShape)
    }

    @Test
    fun `shows ten results and flags that more exist`() = runTest {
        val eleven = (1..11).joinToString(",") { searchItem("m$it", "Batman $it", "Movie") }
        val search = SearchEngine(movies = "[$eleven]")

        val movies = repository(search.engine).search("batman").single()

        assertEquals(10, movies.items.size)
        assertTrue(movies.hasMore)
    }

    @Test
    fun `a row holding exactly ten offers no More action`() = runTest {
        val ten = (1..10).joinToString(",") { searchItem("m$it", "Batman $it", "Movie") }
        val search = SearchEngine(movies = "[$ten]")

        assertFalse(repository(search.engine).search("batman").single().hasMore)
    }

    @Test
    fun `does not sort, leaving the server's ranking of the matches alone`() = runTest {
        // SortName would bury an exact title match under everything alphabetically ahead of it.
        val search = SearchEngine(movies = "[${searchItem("m1", "Batman", "Movie")}]")

        repository(search.engine).search("batman")

        assertTrue(search.urls.none { "sortBy=" in it }, search.urls.toString())
    }

    @Test
    fun `one failing category does not take out the rest`() = runTest {
        val search = SearchEngine(
            movies = "[${searchItem("m1", "Batman", "Movie")}]",
            peopleStatus = HttpStatusCode.InternalServerError,
        )

        assertEquals(
            listOf(SectionKind.SearchMovies),
            repository(search.engine).search("batman").map { it.kind },
        )
    }

    @Test
    fun `fails loudly when every category fails`() = runTest {
        // "No results" and "the server is down" must not look the same.
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        assertFailsWith<Exception> { repository(engine).search("batman") }
    }

    @Test
    fun `suggestions ask the server for its own recommendations`() = runTest {
        val search = SearchEngine(suggestions = "[${searchItem("m1", "Heat", "Movie")}]")

        val items = repository(search.engine).loadSuggestions()

        val url = search.urls.single()
        assertContains(url, "/Items/Suggestions")
        // The item-type parameter here is `type`, not the `includeItemTypes` its neighbours take.
        assertContains(url, "type=Movie%2CSeries")
        assertEquals(listOf("Heat"), items.map { it.title })
    }

    @Test
    fun `a fresh account with no viewing history simply has no suggestions`() = runTest {
        assertEquals(emptyList(), repository(SearchEngine().engine).loadSuggestions())
    }
}
