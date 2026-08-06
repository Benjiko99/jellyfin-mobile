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
import org.jellyfin.mobile.network.TEST_SERVER_URL
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.network.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Answers each request based on what the query is asking for, so all five rows can be exercised. */
private fun favoritesEngine(
    movies: String = "[]",
    series: String = "[]",
    episodes: String = "[]",
    boxSets: String = "[]",
    people: String = "[]",
    peopleStatus: HttpStatusCode = HttpStatusCode.OK,
) = MockEngine { request ->
    val url = request.url.toString()
    val json = ContentType.Application.Json.toString()

    if (url.contains("/Persons")) {
        return@MockEngine if (peopleStatus == HttpStatusCode.OK) {
            respond("""{"Items":$people}""", headers = headersOf(HttpHeaders.ContentType, json))
        } else {
            respondError(peopleStatus)
        }
    }

    val items = when {
        url.contains("includeItemTypes=Movie") -> movies
        url.contains("includeItemTypes=Series") -> series
        url.contains("includeItemTypes=Episode") -> episodes
        url.contains("includeItemTypes=BoxSet") -> boxSets
        else -> "[]"
    }
    respond("""{"Items":$items}""", headers = headersOf(HttpHeaders.ContentType, json))
}

private fun repository(engine: MockEngine): FavoritesRepository {
    val session = testSession()
    return FavoritesRepository(testApi(engine, session), session)
}

private fun item(id: String, name: String, type: String) =
    """{"Id":"$id","Name":"$name","Type":"$type","ImageTags":{"Primary":"tag-$id"}}"""

class FavoritesRepositoryTest {
    @Test
    fun `builds one row per kind, in a fixed order`() = runTest {
        val sections = repository(
            favoritesEngine(
                movies = "[${item("m1", "Heat", "Movie")}]",
                series = "[${item("s1", "Dexter", "Series")}]",
                episodes = "[${item("e1", "Pilot", "Episode")}]",
                boxSets = "[${item("b1", "Marvel", "BoxSet")}]",
                people = "[${item("p1", "Michael C. Hall", "Person")}]",
            ),
        ).loadFavorites()

        assertEquals(
            listOf("Movies", "TV Shows", "Episodes", "Collections", "People"),
            sections.map { it.title },
        )
    }

    @Test
    fun `omits rows the user has nothing favourited in`() = runTest {
        // A user with only favourite movies should see one row, not five, four of them empty.
        val sections = repository(
            favoritesEngine(movies = "[${item("m1", "Heat", "Movie")}]"),
        ).loadFavorites()

        assertEquals(listOf("Movies"), sections.map { it.title })
    }

    @Test
    fun `no favourites at all yields no rows`() = runTest {
        assertEquals(emptyList(), repository(favoritesEngine()).loadFavorites())
    }

    @Test
    fun `episodes get landscape cards, everything else portrait`() = runTest {
        val sections = repository(
            favoritesEngine(
                movies = "[${item("m1", "Heat", "Movie")}]",
                episodes = "[${item("e1", "Pilot", "Episode")}]",
                people = "[${item("p1", "Someone", "Person")}]",
            ),
        ).loadFavorites()

        assertEquals(CardShape.Poster, sections.first { it.title == "Movies" }.cardShape)
        assertEquals(CardShape.Thumb, sections.first { it.title == "Episodes" }.cardShape)
        assertEquals(CardShape.Poster, sections.first { it.title == "People" }.cardShape)
    }

    @Test
    fun `marks people as people so a tap opens their own screen`() = runTest {
        // /Persons does not reliably set Type, so the kind is asserted rather than inferred. Getting
        // this wrong sends the user to an item detail page for a person.
        val sections = repository(
            favoritesEngine(people = """[{"Id":"p1","Name":"Michael C. Hall"}]"""),
        ).loadFavorites()

        val person = sections.single { it.title == "People" }.items.single()
        assertEquals(ItemKind.Person, person.kind)
        assertEquals("Michael C. Hall", person.title)
    }

    @Test
    fun `carries the item kind so navigation can dispatch`() = runTest {
        val sections = repository(
            favoritesEngine(
                movies = "[${item("m1", "Heat", "Movie")}]",
                episodes = "[${item("e1", "Pilot", "Episode")}]",
            ),
        ).loadFavorites()

        assertEquals(ItemKind.Movie, sections.first { it.title == "Movies" }.items.single().kind)
        assertEquals(ItemKind.Episode, sections.first { it.title == "Episodes" }.items.single().kind)
    }

    @Test
    fun `builds image URLs against the signed-in server`() = runTest {
        val sections = repository(
            favoritesEngine(movies = "[${item("m1", "Heat", "Movie")}]"),
        ).loadFavorites()

        val url = sections.single().items.single().imageUrl
        assertTrue(url!!.startsWith("$TEST_SERVER_URL/Items/m1/Images/Primary"), url)
    }

    @Test
    fun `one failing row does not take out the rest`() = runTest {
        val sections = repository(
            favoritesEngine(
                movies = "[${item("m1", "Heat", "Movie")}]",
                peopleStatus = HttpStatusCode.InternalServerError,
            ),
        ).loadFavorites()

        assertEquals(listOf("Movies"), sections.map { it.title })
    }

    @Test
    fun `fails loudly when every query fails`() = runTest {
        // An empty tab and a broken server must not look the same.
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        assertFailsWith<Exception> { repository(engine).loadFavorites() }
    }
}
