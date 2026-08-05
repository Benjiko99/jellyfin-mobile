package org.jellyfin.mobile.data

import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.network.jsonEngine
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.network.testSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonRepositoryTest {
    private fun repository(body: String) = run {
        val engine = jsonEngine(body)
        val session = testSession()
        engine to PersonRepository(testApi(engine, session), session)
    }

    private fun queryResult(count: Int, total: Int = count) = buildString {
        append("""{"Items":[""")
        append((1..count).joinToString(",") { """{"Id":"item-$it","Name":"Title $it","Type":"Movie"}""" })
        append("""],"TotalRecordCount":$total,"StartIndex":0}""")
    }

    @Test
    fun `preview shows ten and flags that there are more`() = runTest {
        // Eleven come back; the eleventh exists only to answer "is there more?".
        val (_, repository) = repository(queryResult(count = 11))

        val movies = repository.loadFilmography("person-1").movies

        assertEquals(CREDIT_PREVIEW_LIMIT, movies.credits.size)
        assertTrue(movies.hasMore)
    }

    @Test
    fun `preview with exactly ten offers no More button`() = runTest {
        val (_, repository) = repository(queryResult(count = 10))

        val movies = repository.loadFilmography("person-1").movies

        assertEquals(10, movies.credits.size)
        assertFalse(movies.hasMore)
    }

    @Test
    fun `preview asks for one more than it shows`() = runTest {
        val (engine, repository) = repository(queryResult(count = 3))

        repository.loadFilmography("person-1")

        // Three concurrent queries, one per credit kind.
        assertEquals(3, engine.requestHistory.size)
        engine.requestHistory.forEach {
            assertEquals("11", it.url.parameters["limit"])
            // Counting every match is wasted work when the extra row already answers the question.
            assertEquals("false", it.url.parameters["enableTotalRecordCount"])
        }
    }

    @Test
    fun `a failing credit list does not take the others down`() = runTest {
        // Not valid JSON for any of the three queries, so every one fails.
        val (_, repository) = repository("nonsense")

        val filmography = repository.loadFilmography("person-1")

        assertTrue(filmography.isEmpty)
    }

    @Test
    fun `a page requests its slice and a total`() = runTest {
        val (engine, repository) = repository(queryResult(count = 40, total = 137))

        val page = repository.loadCreditPage(
            personId = "person-1",
            kind = CreditKind.Episodes,
            startIndex = 40,
            limit = 40,
        )

        assertEquals(137, page.totalCount)
        assertEquals(40, page.credits.size)
        engine.requestHistory.single().url.let {
            assertEquals("40", it.parameters["startIndex"])
            assertEquals("40", it.parameters["limit"])
            // The full list shows a count and needs to know where the end is.
            assertEquals("true", it.parameters["enableTotalRecordCount"])
            assertEquals("Episode", it.parameters["includeItemTypes"])
        }
    }

    @Test
    fun `credit kind round trips through its route argument`() {
        CreditKind.entries.forEach { kind ->
            assertEquals(kind, CreditKind.from(kind.name))
        }
        // An unrecognised value must not crash navigation.
        assertEquals(CreditKind.Movies, CreditKind.from("nonsense"))
    }
}
