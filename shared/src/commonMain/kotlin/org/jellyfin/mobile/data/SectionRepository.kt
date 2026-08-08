package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.EpisodeArtwork
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult

const val SECTION_PAGE_SIZE = 40

/** Newest first — what "recently added" means. */
private val SORT_BY_DATE_ADDED = listOf("DateCreated")
private val SORT_DESCENDING = listOf("Descending")

data class SectionPage(
    val items: List<MediaItem>,
    /** Null when the endpoint cannot report one; the UI then pages until a short page arrives. */
    val totalCount: Int?,
    val endReached: Boolean,
)

/**
 * Pages the full list behind a row's "More" action.
 *
 * Each [SectionKind] maps back to the query that produced the row. "Recently Added" is the awkward
 * one: it comes from `/Items/Latest`, which takes no `startIndex` and so cannot be paged at all.
 * The full list therefore uses `/Items` sorted by `DateCreated` descending, which is the same
 * content by a pageable route.
 */
class SectionRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    @Suppress("LongParameterList")
    suspend fun loadPage(
        kind: SectionKind,
        parentId: String?,
        libraryItemKind: ItemKind? = null,
        /** Required by the `Search*` kinds, unused by every other one. */
        searchTerm: String? = null,
        startIndex: Int,
        limit: Int = SECTION_PAGE_SIZE,
    ): SectionPage {
        val serverUrl = session.requireServerUrl()

        val result = when (kind) {
            SectionKind.Resume -> api.resumeItems(
                limit = limit,
                startIndex = startIndex,
                enableTotalRecordCount = startIndex == 0,
            )

            SectionKind.NextUp -> api.nextUp(
                limit = limit,
                startIndex = startIndex,
                enableTotalRecordCount = startIndex == 0,
            )

            SectionKind.LatestInLibrary -> api.items(
                parentId = requireNotNull(parentId) { "Recently Added needs a library" },
                // Resolved when the row was built, from the library's collection type.
                includeItemTypes = listOfNotNull(libraryItemKind?.wireType),
                sortBy = SORT_BY_DATE_ADDED,
                sortOrder = SORT_DESCENDING,
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = startIndex == 0,
            )

            SectionKind.FavoritePeople -> api.persons(
                isFavorite = true,
                limit = limit,
                startIndex = startIndex,
            )

            SectionKind.FavoriteMovies,
            SectionKind.FavoriteSeries,
            SectionKind.FavoriteEpisodes,
            SectionKind.FavoriteCollections,
            -> api.items(
                includeItemTypes = listOfNotNull(kind.itemKind?.wireType),
                isFavorite = true,
                sortBy = SORT_BY_NAME,
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = startIndex == 0,
            )

            SectionKind.SearchPeople -> api.persons(
                searchTerm = requireNotNull(searchTerm) { "Search needs a term" },
                limit = limit,
                startIndex = startIndex,
            )

            // Never reached: the collections row is built by filtering an untyped search, because
            // the server cannot filter a search to box sets, and a client-side filter cannot be
            // paged by a server-side startIndex. SearchRepository.searchCollections explains why,
            // and caps that row so it never offers the "More" that would land here.
            SectionKind.SearchCollections -> error("Collection search results are not pageable")

            SectionKind.SearchMovies,
            SectionKind.SearchSeries,
            SectionKind.SearchEpisodes,
            -> api.items(
                includeItemTypes = listOfNotNull(kind.itemKind?.wireType),
                searchTerm = requireNotNull(searchTerm) { "Search needs a term" },
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = startIndex == 0,
                // Unsorted, matching the row this pages: the server's own ranking of the matches.
            )
        }

        return result.toPage(
            serverUrl = serverUrl,
            shape = kind.cardShape,
            // The "More" screen behind a row draws the same cards it does, so it reads the artwork
            // choice off the same kind rather than restating it.
            artwork = kind.episodeArtwork,
            limit = limit,
            // `/Persons` does not reliably set `Type`; see FavoritesRepository.
            forceKind = ItemKind.Person.takeIf { kind.itemKind == ItemKind.Person },
            // Only the first page asks for a count. On later pages the server fills
            // TotalRecordCount with the size of the page it just returned, which would otherwise
            // overwrite the real total with 40.
            hasTotal = startIndex == 0,
        )
    }
}

@Suppress("LongParameterList")
private fun BaseItemDtoQueryResult.toPage(
    serverUrl: String,
    shape: CardShape,
    artwork: EpisodeArtwork,
    limit: Int,
    forceKind: ItemKind?,
    hasTotal: Boolean,
): SectionPage {
    val mapped = items.map { dto ->
        dto.toMediaItem(serverUrl, shape, artwork)
            .let { if (forceKind != null) it.copy(kind = forceKind) else it }
    }
    return SectionPage(
        items = mapped,
        totalCount = totalRecordCount.takeIf { hasTotal && it > 0 },
        // A page shorter than asked for is the end. This is the only signal `/Persons` gives, and
        // it is correct for the others too.
        endReached = items.size < limit,
    )
}
