package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
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
    suspend fun loadPage(
        kind: SectionKind,
        parentId: String?,
        startIndex: Int,
        limit: Int = SECTION_PAGE_SIZE,
    ): SectionPage {
        val serverUrl = requireNotNull(session.serverUrl) { "No server configured" }
        val shape = kind.cardShape

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
                includeItemTypes = listOfNotNull(latestItemType(parentId)),
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

            else -> api.items(
                includeItemTypes = listOfNotNull(kind.favoriteItemKind?.wireType),
                isFavorite = true,
                sortBy = SORT_BY_NAME,
                startIndex = startIndex,
                limit = limit,
                enableTotalRecordCount = startIndex == 0,
            )
        }

        return result.toPage(
            serverUrl = serverUrl,
            shape = shape,
            limit = limit,
            forceKind = kind.forcedItemKind,
            // Only the first page asks for a count. On later pages the server fills
            // TotalRecordCount with the size of the page it just returned, which would otherwise
            // overwrite the real total with 40.
            hasTotal = startIndex == 0,
        )
    }

    /**
     * Which type a library's "recently added" list should contain.
     *
     * `/Items/Latest` groups episodes under their series for TV; `/Items` does not, so without this
     * the full list would be a wall of individual episodes where the row showed shows.
     *
     * Cached because it is needed on every page and a library's type never changes. Page loads are
     * sequential, so the plain map needs no synchronisation.
     */
    private val libraryItemTypes = mutableMapOf<String, String?>()

    private suspend fun latestItemType(parentId: String): String? =
        libraryItemTypes.getOrPut(parentId) {
            when (runCatching { api.item(parentId).collectionType }.getOrNull()) {
                COLLECTION_TV -> ItemKind.Series.wireType
                COLLECTION_MOVIES -> ItemKind.Movie.wireType
                else -> null
            }
        }
}

private fun BaseItemDtoQueryResult.toPage(
    serverUrl: String,
    shape: CardShape,
    limit: Int,
    forceKind: ItemKind?,
    hasTotal: Boolean,
): SectionPage {
    val mapped = items.map { dto ->
        dto.toMediaItem(serverUrl, shape).let { if (forceKind != null) it.copy(kind = forceKind) else it }
    }
    return SectionPage(
        items = mapped,
        totalCount = totalRecordCount.takeIf { hasTotal && it > 0 },
        // A page shorter than asked for is the end. This is the only signal `/Persons` gives, and
        // it is correct for the others too.
        endReached = items.size < limit,
    )
}

private val SectionKind.cardShape: CardShape
    get() = when (this) {
        SectionKind.Resume, SectionKind.NextUp, SectionKind.FavoriteEpisodes -> CardShape.Thumb
        else -> CardShape.Poster
    }

/** The `includeItemTypes` filter for the favourite rows that are a single item type. */
private val SectionKind.favoriteItemKind: ItemKind?
    get() = when (this) {
        SectionKind.FavoriteMovies -> ItemKind.Movie
        SectionKind.FavoriteSeries -> ItemKind.Series
        SectionKind.FavoriteEpisodes -> ItemKind.Episode
        SectionKind.FavoriteCollections -> ItemKind.BoxSet
        else -> null
    }

/** See FavoritesRepository: `/Persons` does not reliably set `Type` on its results. */
private val SectionKind.forcedItemKind: ItemKind?
    get() = if (this == SectionKind.FavoritePeople) ItemKind.Person else null
