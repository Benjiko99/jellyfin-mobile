package org.jellyfin.mobile.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult

/** How many recommendations the resting state shows. Enough to fill a phone screen and scroll a little. */
private const val SUGGESTION_LIMIT = 24

/** What the server is asked to recommend. Suggestions are a browse aid, not a "resume" list. */
private val SUGGESTION_TYPES = listOfNotNull(ItemKind.Movie.wireType, ItemKind.Series.wireType)

/**
 * The search screen's two states: recommendations before anything is typed, then one row per
 * category once there is a query.
 *
 * Results are deliberately the same [HomeSection] list the home and favourites tabs produce, so all
 * three render through the same row and card composables, and a search row's "More" action lands on
 * the existing paged list screen.
 *
 * Categories are queried separately rather than through `/Search/Hints`, which returns everything in
 * one relevance-ordered list: a single list has to be split client-side, and a query matching fifty
 * movies would then push people off the end of it entirely. One query per category gives each row
 * its own budget. This is also how jellyfin-android's Android Auto search page does it.
 */
class SearchRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun loadSuggestions(): List<MediaItem> {
        val serverUrl = session.requireServerUrl()
        return api.suggestions(types = SUGGESTION_TYPES, limit = SUGGESTION_LIMIT)
            .items.map { it.toMediaItem(serverUrl, CardShape.Poster) }
    }

    /**
     * Like [FavoritesRepository], rows are fetched concurrently and fail independently — a server
     * that errors on one type still shows the rest — but if *every* query fails the first error
     * propagates so the UI shows a real error rather than "no results".
     *
     * A category with no matches yields no row at all, which is what makes a search for an actor
     * come back as a single People row instead of four empty ones.
     */
    suspend fun search(term: String): List<HomeSection> = coroutineScope {
        val serverUrl = session.requireServerUrl()

        val rows = listOf(
            SearchRow("search-movies", "Movies", SectionKind.SearchMovies),
            SearchRow("search-series", "TV Shows", SectionKind.SearchSeries),
            SearchRow("search-episodes", "Episodes", SectionKind.SearchEpisodes),
            SearchRow("search-collections", "Collections", SectionKind.SearchCollections),
        )

        val itemQueries: List<Deferred<Result<BaseItemDtoQueryResult>>> = rows.map { row ->
            async {
                runCatching {
                    api.items(
                        includeItemTypes = listOfNotNull(row.kind.itemKind?.wireType),
                        searchTerm = term,
                        limit = PREVIEW_PROBE_LIMIT,
                        // No sortBy: the server ranks search matches itself, and imposing SortName
                        // would bury an exact title match under everything alphabetically ahead of it.
                    )
                }
            }
        }
        // People are only reachable through `/Persons` — a recursive `/Items` query never returns
        // them however it is filtered.
        val peopleQuery = async {
            runCatching { api.persons(searchTerm = term, limit = PREVIEW_PROBE_LIMIT) }
        }

        val itemResults = itemQueries.map { it.await() }
        val people = peopleQuery.await()

        if (itemResults.all { it.isFailure } && people.isFailure) {
            throw itemResults.firstNotNullOf { it.exceptionOrNull() }
        }

        buildList {
            rows.zip(itemResults).forEach { (row, result) ->
                previewSection(
                    id = row.id,
                    title = row.title,
                    kind = row.kind,
                    items = result.getOrNull()?.items.orEmpty(),
                    serverUrl = serverUrl,
                    searchTerm = term,
                )?.let(::add)
            }

            previewSection(
                id = "search-people",
                title = "People",
                kind = SectionKind.SearchPeople,
                items = people.getOrNull()?.items.orEmpty(),
                serverUrl = serverUrl,
                searchTerm = term,
            )?.let { section ->
                // `/Persons` does not always set `Type` on its results, so the kind is asserted here
                // rather than inferred — getting it wrong would send a tap to the item detail screen
                // instead of the person's page.
                add(section.copy(items = section.items.map { it.copy(kind = ItemKind.Person) }))
            }
        }
    }
}

private data class SearchRow(
    val id: String,
    val title: String,
    val kind: SectionKind,
)
