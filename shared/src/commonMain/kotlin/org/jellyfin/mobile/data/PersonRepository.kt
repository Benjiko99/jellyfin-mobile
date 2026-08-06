package org.jellyfin.mobile.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.CreditKind
import org.jellyfin.mobile.domain.CreditList
import org.jellyfin.mobile.domain.CreditPage
import org.jellyfin.mobile.domain.Filmography
import org.jellyfin.mobile.domain.PersonDetail
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession

/** How many credits of each kind the person page shows before offering "More". */
const val CREDIT_PREVIEW_LIMIT = 10

/** Newest first, falling back to title so undated items keep a stable order. */
private val CREDIT_SORT = listOf("PremiereDate", "SortName")
private val CREDIT_SORT_ORDER = listOf("Descending")

class PersonRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun load(personId: String): PersonDetail {
        val serverUrl = session.requireServerUrl()
        return api.item(personId).toPersonDetail(serverUrl)
    }

    /**
     * The three preview lists, fetched concurrently.
     *
     * A failing list yields an empty one rather than failing the whole page: a prolific TV actor
     * with hundreds of episode credits should still get their films if the episode query times out.
     */
    suspend fun loadFilmography(personId: String): Filmography = coroutineScope {
        val serverUrl = session.requireServerUrl()

        suspend fun preview(kind: CreditKind) = runCatching {
            val items = api.items(
                personIds = listOf(personId),
                includeItemTypes = listOf(kind.itemType),
                sortBy = CREDIT_SORT,
                sortOrder = CREDIT_SORT_ORDER,
                // One more than we show, purely to learn whether a "More" button is warranted
                // without making the server count the whole result set.
                limit = CREDIT_PREVIEW_LIMIT + 1,
            ).items.map { it.toCredit(serverUrl) }

            CreditList(
                credits = items.take(CREDIT_PREVIEW_LIMIT),
                hasMore = items.size > CREDIT_PREVIEW_LIMIT,
            )
        }.getOrDefault(CreditList())

        val movies = async { preview(CreditKind.Movies) }
        val shows = async { preview(CreditKind.Shows) }
        val episodes = async { preview(CreditKind.Episodes) }

        Filmography(
            movies = movies.await(),
            shows = shows.await(),
            episodes = episodes.await(),
        )
    }

    /** One page of the full list behind a "More" button. */
    suspend fun loadCreditPage(
        personId: String,
        kind: CreditKind,
        startIndex: Int,
        limit: Int,
    ): CreditPage {
        val serverUrl = session.requireServerUrl()
        val result = api.items(
            personIds = listOf(personId),
            includeItemTypes = listOf(kind.itemType),
            sortBy = CREDIT_SORT,
            sortOrder = CREDIT_SORT_ORDER,
            startIndex = startIndex,
            limit = limit,
            // The full list shows a count and needs to know where the end is.
            enableTotalRecordCount = true,
        )
        return CreditPage(
            credits = result.items.map { it.toCredit(serverUrl) },
            totalCount = result.totalRecordCount,
        )
    }

    /** People are items, so the ordinary favourite endpoint works on them. */
    suspend fun setFavorite(personId: String, favorite: Boolean): Boolean =
        api.setFavorite(personId, favorite).isFavorite
}
