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
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult

/** How many recommendations the resting state shows. Enough to fill a phone screen and scroll a little. */
private const val SUGGESTION_LIMIT = 24

/** What the server is asked to recommend. Suggestions are a browse aid, not a "resume" list. */
private val SUGGESTION_TYPES = listOfNotNull(ItemKind.Movie.wireType, ItemKind.Series.wireType)

/**
 * How far down an untyped search to look for box sets. See [SearchRepository.searchCollections] for
 * why they are found that way rather than asked for directly.
 */
private const val COLLECTION_SCAN_LIMIT = 60

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
            SearchRow("search-movies", SectionKind.SearchMovies),
            SearchRow("search-series", SectionKind.SearchSeries),
            SearchRow("search-episodes", SectionKind.SearchEpisodes),
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
        val collectionsQuery = async { runCatching { searchCollections(term) } }
        // People are only reachable through `/Persons` — a recursive `/Items` query never returns
        // them however it is filtered.
        val peopleQuery = async {
            runCatching { api.persons(searchTerm = term, limit = PREVIEW_PROBE_LIMIT) }
        }

        val itemResults = itemQueries.map { it.await() }
        val collections = collectionsQuery.await()
        val people = peopleQuery.await()

        if (itemResults.all { it.isFailure } && collections.isFailure && people.isFailure) {
            throw itemResults.firstNotNullOf { it.exceptionOrNull() }
        }

        buildList {
            rows.zip(itemResults).forEach { (row, result) ->
                previewSection(
                    id = row.id,
                    kind = row.kind,
                    items = result.getOrNull()?.items.orEmpty(),
                    serverUrl = serverUrl,
                    searchTerm = term,
                )?.let(::add)
            }

            previewSection(
                id = "search-collections",
                kind = SectionKind.SearchCollections,
                items = collections.getOrNull().orEmpty(),
                serverUrl = serverUrl,
                searchTerm = term,
            )?.let(::add)

            previewSection(
                id = "search-people",
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

    /**
     * Box set matches, picked out of an untyped search.
     *
     * The obvious query — `searchTerm` with `includeItemTypes=BoxSet` — does not work. The server
     * answers it with an empty body that is not even JSON, on a library where the same term plainly
     * matches a box set. It is specific to that combination: the filter works without a term, the
     * term works without the filter, and *both* work if a second item type rides along in the same
     * `includeItemTypes`. So this scans one untyped search instead and keeps the box sets.
     *
     * The cost of filtering here rather than server-side is that the row cannot be paged: the
     * server's `startIndex` counts the untyped list, not our filtered view of it. The row is
     * therefore capped at what it displays so it never offers a "More" it could not honour. A query
     * matching more than [SECTION_PREVIEW_LIMIT] box sets is rare enough to accept that; if it stops
     * being, `/Search/Hints` is the pageable alternative, at the cost of its own DTO.
     */
    private suspend fun searchCollections(term: String): List<BaseItemDto> =
        api.items(searchTerm = term, limit = COLLECTION_SCAN_LIMIT)
            .items
            .filter { it.type == ItemKind.BoxSet.wireType }
            .take(SECTION_PREVIEW_LIMIT)
}

private data class SearchRow(
    val id: String,
    val kind: SectionKind,
)
