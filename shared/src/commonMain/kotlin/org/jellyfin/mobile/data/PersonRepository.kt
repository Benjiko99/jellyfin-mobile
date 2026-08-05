package org.jellyfin.mobile.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.Filmography
import org.jellyfin.mobile.domain.PersonDetail
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession

private const val CREDIT_LIMIT = 100

class PersonRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun load(personId: String): PersonDetail {
        val serverUrl = requireNotNull(session.serverUrl) { "No server configured" }
        return api.item(personId).toPersonDetail(serverUrl)
    }

    /**
     * Films, shows and episode appearances, fetched concurrently.
     *
     * A failing list yields an empty one rather than failing the whole page: a prolific TV actor
     * with hundreds of episode credits should still get their films if the episode query times out.
     */
    suspend fun loadFilmography(personId: String): Filmography = coroutineScope {
        val serverUrl = requireNotNull(session.serverUrl) { "No server configured" }

        suspend fun credits(vararg types: String) = runCatching {
            api.items(
                personIds = listOf(personId),
                includeItemTypes = types.toList(),
                recursive = true,
                sortBy = listOf("PremiereDate", "SortName"),
                sortOrder = listOf("Descending"),
                limit = CREDIT_LIMIT,
            ).items.map { it.toCredit(serverUrl) }
        }.getOrDefault(emptyList())

        val movies = async { credits("Movie") }
        val shows = async { credits("Series") }
        val episodes = async { credits("Episode") }

        Filmography(
            movies = movies.await(),
            shows = shows.await(),
            episodes = episodes.await(),
        )
    }

    /** People are items, so the ordinary favourite endpoint works on them. */
    suspend fun setFavorite(personId: String, favorite: Boolean): Boolean =
        api.setFavorite(personId, favorite).isFavorite
}
