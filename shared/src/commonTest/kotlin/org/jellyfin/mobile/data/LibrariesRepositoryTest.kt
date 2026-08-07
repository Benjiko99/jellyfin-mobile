package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.network.jsonEngine
import org.jellyfin.mobile.network.testApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A realistic `/UserViews`: two movie libraries — which is the case a fixed drawer of four entries
 * gets wrong — one renamed, a type we have no browse view for, and the two the server makes itself.
 */
private const val USER_VIEWS = """
    {
      "Items": [
        { "Id": "lib-films", "Name": "Films", "CollectionType": "movies" },
        { "Id": "lib-kids", "Name": "Kids Movies", "CollectionType": "movies" },
        { "Id": "lib-tv", "Name": "TV Shows", "CollectionType": "tvshows" },
        { "Id": "lib-music", "Name": "Music", "CollectionType": "music" },
        { "Id": "lib-pl", "Name": "Playlists", "CollectionType": "playlists" },
        { "Id": "lib-bs", "Name": "Collections", "CollectionType": "boxsets" }
      ],
      "TotalRecordCount": 6,
      "StartIndex": 0
    }
"""

class LibrariesRepositoryTest {
    @Test
    fun `keeps every library the server offers, in the server's order`() = runTest {
        val libraries = LibrariesRepository(testApi(jsonEngine(USER_VIEWS))).loadLibraries()

        assertEquals(
            listOf("Films", "Kids Movies", "TV Shows", "Music", "Playlists", "Collections"),
            libraries.map { it.name },
        )
    }

    @Test
    fun `resolves the collection type, and does not drop the ones it cannot browse`() = runTest {
        val libraries = LibrariesRepository(testApi(jsonEngine(USER_VIEWS))).loadLibraries()

        assertEquals(LibraryKind.Movies, libraries[0].kind)
        assertEquals(LibraryKind.Movies, libraries[1].kind)
        assertEquals(LibraryKind.TvShows, libraries[2].kind)
        // A music library still belongs in the drawer; it just gets the generic single tab.
        assertEquals(LibraryKind.Other, libraries[3].kind)
        assertEquals(LibraryKind.Playlists, libraries[4].kind)
        assertEquals(LibraryKind.Collections, libraries[5].kind)
    }

    /** Two libraries of the same type are distinct destinations, so the ids must survive. */
    @Test
    fun `carries the library id, which is what the browse screen queries by`() = runTest {
        val libraries = LibrariesRepository(testApi(jsonEngine(USER_VIEWS))).loadLibraries()

        assertEquals(listOf("lib-films", "lib-kids"), libraries.take(2).map { it.id })
    }

    @Test
    fun `a server that will not answer leaves the drawer without a Media section`() = runTest {
        val libraries = LibrariesRepository(
            testApi(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
        ).loadLibraries()

        assertTrue(libraries.isEmpty())
    }
}
