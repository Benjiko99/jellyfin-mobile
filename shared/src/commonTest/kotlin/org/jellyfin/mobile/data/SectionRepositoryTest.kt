package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.network.testSession
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records every URL requested so the query behind each section kind can be asserted. */
private class RecordingEngine(private val body: (String) -> String) {
    val urls = mutableListOf<String>()

    val engine = MockEngine { request ->
        val url = request.url.toString()
        urls += url
        respond(
            content = body(url),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
}

private fun items(count: Int, type: String = "Movie", total: Int? = null): String {
    val list = (1..count).joinToString(",") { """{"Id":"i$it","Name":"Item $it","Type":"$type"}""" }
    val totalField = total?.let { """"TotalRecordCount":$it,""" }.orEmpty()
    return """{$totalField"Items":[$list]}"""
}

private fun repository(engine: MockEngine): SectionRepository {
    val session = testSession()
    return SectionRepository(testApi(engine, session), session)
}

class SectionRepositoryTest {
    @Test
    fun `pages favourites through the item query`() = runTest {
        val recorder = RecordingEngine { items(count = 40, total = 95) }

        val page = repository(recorder.engine)
            .loadPage(SectionKind.FavoriteMovies, parentId = null, startIndex = 40, limit = 40)

        val url = recorder.urls.single()
        assertContains(url, "includeItemTypes=Movie")
        assertContains(url, "isFavorite=true")
        assertContains(url, "startIndex=40")
        assertEquals(40, page.items.size)
        assertFalse(page.endReached)
    }

    @Test
    fun `a short page is the end of the list`() = runTest {
        val recorder = RecordingEngine { items(count = 12) }

        val page = repository(recorder.engine)
            .loadPage(SectionKind.FavoriteMovies, parentId = null, startIndex = 0, limit = 40)

        assertTrue(page.endReached)
    }

    @Test
    fun `only counts the total on the first page`() = runTest {
        // The count costs the server a full scan of every match, and it cannot change mid-paging.
        val recorder = RecordingEngine { items(count = 40, total = 95) }
        val repo = repository(recorder.engine)

        repo.loadPage(SectionKind.FavoriteMovies, parentId = null, startIndex = 0, limit = 40)
        repo.loadPage(SectionKind.FavoriteMovies, parentId = null, startIndex = 40, limit = 40)

        assertContains(recorder.urls[0], "enableTotalRecordCount=true")
        assertContains(recorder.urls[1], "enableTotalRecordCount=false")
    }

    @Test
    fun `a later page never reports a total`() = runTest {
        // With enableTotalRecordCount off the server fills TotalRecordCount with the size of the
        // page it just returned, so trusting it would replace "1537 items" with "40 items" as soon
        // as the user scrolled.
        val recorder = RecordingEngine { items(count = 40, total = 40) }

        val page = repository(recorder.engine)
            .loadPage(SectionKind.FavoriteMovies, parentId = null, startIndex = 40, limit = 40)

        assertNull(page.totalCount)
    }

    @Test
    fun `the first page reports the real total`() = runTest {
        val recorder = RecordingEngine { items(count = 40, total = 1537) }

        val page = repository(recorder.engine)
            .loadPage(SectionKind.FavoriteMovies, parentId = null, startIndex = 0, limit = 40)

        assertEquals(1537, page.totalCount)
    }

    @Test
    fun `recently added pages by date rather than through Items Latest`() = runTest {
        // /Items/Latest takes no startIndex, so it cannot be paged at all. The full list uses
        // /Items sorted by DateCreated descending, which is the same content by a pageable route.
        val recorder = RecordingEngine { url ->
            if ("/Items/lib-1" in url) """{"Id":"lib-1","CollectionType":"tvshows"}""" else items(5, "Series")
        }

        repository(recorder.engine)
            .loadPage(SectionKind.LatestInLibrary, "lib-1", ItemKind.Series, startIndex = 0, limit = 40)

        val query = recorder.urls.last()
        assertContains(query, "parentId=lib-1")
        assertContains(query, "sortBy=DateCreated")
        assertContains(query, "sortOrder=Descending")
        assertFalse("/Items/Latest" in query)
    }

    @Test
    fun `a TV library's full list holds shows, not loose episodes`() = runTest {
        // /Items/Latest groups episodes under their series; /Items does not, so the type has to be
        // constrained or the row and its More screen disagree about what they contain.
        val recorder = RecordingEngine { url ->
            if ("/Items/lib-1" in url) """{"Id":"lib-1","CollectionType":"tvshows"}""" else items(5, "Series")
        }

        repository(recorder.engine)
            .loadPage(SectionKind.LatestInLibrary, "lib-1", ItemKind.Series, startIndex = 0, limit = 40)

        assertContains(recorder.urls.last(), "includeItemTypes=Series")
    }

    @Test
    fun `a movie library's full list holds movies`() = runTest {
        val recorder = RecordingEngine { url ->
            if ("/Items/lib-2" in url) """{"Id":"lib-2","CollectionType":"movies"}""" else items(5)
        }

        repository(recorder.engine)
            .loadPage(SectionKind.LatestInLibrary, "lib-2", ItemKind.Movie, startIndex = 0, limit = 40)

        assertContains(recorder.urls.last(), "includeItemTypes=Movie")
    }

    @Test
    fun `never looks the library up — the row already resolved its type`() = runTest {
        // The type is carried on the route, so opening "More" costs one request, not two serial
        // ones with the fat /Items/{libraryId} fetch first.
        val recorder = RecordingEngine { items(40) }
        val repo = repository(recorder.engine)

        repo.loadPage(SectionKind.LatestInLibrary, "lib-1", ItemKind.Movie, startIndex = 0, limit = 40)
        repo.loadPage(SectionKind.LatestInLibrary, "lib-1", ItemKind.Movie, startIndex = 40, limit = 40)

        assertEquals(0, recorder.urls.count { "/Items/lib-1" in it })
        assertEquals(2, recorder.urls.size)
    }

    @Test
    fun `people come from the Persons route and stay people`() = runTest {
        // /Persons does not reliably set Type, so the kind is asserted — otherwise a tap from the
        // full list would open an item detail page instead of the person's screen.
        val recorder = RecordingEngine { """{"Items":[{"Id":"p1","Name":"Akira Kurosawa"}]}""" }

        val page = repository(recorder.engine)
            .loadPage(SectionKind.FavoritePeople, parentId = null, startIndex = 0, limit = 40)

        assertContains(recorder.urls.single(), "/Persons")
        assertContains(recorder.urls.single(), "isFavorite=true")
        assertEquals(ItemKind.Person, page.items.single().kind)
    }

    @Test
    fun `Persons reports no total, so the header simply omits it`() = runTest {
        val recorder = RecordingEngine { """{"Items":[{"Id":"p1","Name":"Someone"}]}""" }

        val page = repository(recorder.engine)
            .loadPage(SectionKind.FavoritePeople, parentId = null, startIndex = 0, limit = 40)

        assertNull(page.totalCount)
        assertTrue(page.endReached)
    }

    @Test
    fun `a search row pages the same query the row ran`() = runTest {
        val recorder = RecordingEngine { items(count = 40, total = 95) }

        val page = repository(recorder.engine).loadPage(
            SectionKind.SearchMovies,
            parentId = null,
            searchTerm = "batman",
            startIndex = 40,
            limit = 40,
        )

        val url = recorder.urls.single()
        assertContains(url, "includeItemTypes=Movie")
        assertContains(url, "searchTerm=batman")
        assertContains(url, "startIndex=40")
        // Unsorted, matching the row: the server's own ranking of the matches.
        assertFalse("sortBy=" in url)
        assertEquals(40, page.items.size)
    }

    @Test
    fun `searched people page through Persons and stay people`() = runTest {
        val recorder = RecordingEngine { """{"Items":[{"Id":"p1","Name":"Christian Bale"}]}""" }

        val page = repository(recorder.engine).loadPage(
            SectionKind.SearchPeople,
            parentId = null,
            searchTerm = "bale",
            startIndex = 0,
            limit = 40,
        )

        assertContains(recorder.urls.single(), "/Persons")
        assertContains(recorder.urls.single(), "searchTerm=bale")
        assertEquals(ItemKind.Person, page.items.single().kind)
    }

    @Test
    fun `a search row without its term is a programming error, not a request`() = runTest {
        val recorder = RecordingEngine { items(count = 1) }

        assertFailsWith<IllegalArgumentException> {
            repository(recorder.engine)
                .loadPage(SectionKind.SearchMovies, parentId = null, startIndex = 0, limit = 40)
        }
    }

    @Test
    fun `continue watching and next up page through their own endpoints`() = runTest {
        val resume = RecordingEngine { items(40, "Episode", total = 50) }
        repository(resume.engine).loadPage(SectionKind.Resume, null, startIndex = 0, limit = 40)
        assertContains(resume.urls.single(), "/UserItems/Resume")

        val nextUp = RecordingEngine { items(40, "Episode", total = 50) }
        repository(nextUp.engine).loadPage(SectionKind.NextUp, null, startIndex = 40, limit = 40)
        assertContains(nextUp.urls.single(), "/Shows/NextUp")
        assertContains(nextUp.urls.single(), "startIndex=40")
    }
}
