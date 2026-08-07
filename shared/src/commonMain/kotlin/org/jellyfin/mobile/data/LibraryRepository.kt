package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.Alphabet
import org.jellyfin.mobile.domain.LibraryFilterOptions
import org.jellyfin.mobile.domain.LibraryFilters
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.domain.PlayedFilter
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession

/**
 * One screenful of a library grid. Larger than a row's page: a grid shows four or five to a row,
 * so the same page count runs out much sooner.
 */
const val LIBRARY_PAGE_SIZE = 60

/**
 * Pages one tab of a library browse screen.
 *
 * Every tab is the same `/Items` query with different parameters — which is the whole reason the
 * TV and movie screens can be one screen — so the tab, the filters and the alphabet rail all fold
 * into a single request here rather than into a query per tab.
 */
class LibraryRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    @Suppress("LongParameterList")
    suspend fun loadPage(
        libraryId: String,
        tab: LibraryTab,
        filters: LibraryFilters,
        /** A letter from [Alphabet], or null for the whole list. */
        startLetter: String?,
        startIndex: Int,
        limit: Int = LIBRARY_PAGE_SIZE,
    ): SectionPage {
        val serverUrl = session.requireServerUrl()

        val result = api.items(
            // Box sets live in a library of their own, so scoping them to the movie or TV library
            // whose tab is showing them would return nothing at all.
            parentId = libraryId.takeUnless { tab == LibraryTab.Collections },
            includeItemTypes = listOfNotNull(tab.itemKind?.wireType),
            sortBy = listOf(filters.sort.wireValue),
            sortOrder = listOf(if (filters.descending) "Descending" else "Ascending"),
            isFavorite = true.takeIf { tab.favoritesOnly || filters.favoritesOnly },
            isPlayed = when (filters.played) {
                PlayedFilter.All -> null
                PlayedFilter.Played -> true
                PlayedFilter.Unplayed -> false
            },
            // "#" is everything sorting before "a" — digits and punctuation. The two parameters are
            // mutually exclusive by construction: a letter is a prefix, "#" is an upper bound.
            nameStartsWith = startLetter?.takeIf { it != Alphabet.OTHER },
            nameLessThan = "a".takeIf { startLetter == Alphabet.OTHER },
            genres = filters.genres.toList(),
            officialRatings = filters.officialRatings.toList(),
            years = filters.years.toList(),
            startIndex = startIndex,
            limit = limit,
            // Only the first page asks for a count; on later pages the server fills
            // TotalRecordCount with the size of the page it just returned.
            enableTotalRecordCount = startIndex == 0,
        )

        val items = result.items.map { it.toMediaItem(serverUrl, tab.cardShape) }
        return SectionPage(
            items = items,
            totalCount = result.totalRecordCount.takeIf { startIndex == 0 && it > 0 },
            endReached = result.items.size < limit,
        )
    }

    /**
     * What this tab can be filtered by.
     *
     * Never fails: a server that will not answer leaves the sheet with the filters that need no
     * server support — played state and favourites — rather than refusing to open. Nothing else on
     * the screen depends on it.
     */
    suspend fun loadFilterOptions(libraryId: String, tab: LibraryTab): LibraryFilterOptions {
        val filters = runCatching {
            api.itemFilters(
                parentId = libraryId.takeUnless { tab == LibraryTab.Collections },
                includeItemTypes = listOfNotNull(tab.itemKind?.wireType),
            )
        }.getOrNull() ?: return LibraryFilterOptions()

        return LibraryFilterOptions(
            genres = filters.genres.orEmpty(),
            officialRatings = filters.officialRatings.orEmpty(),
            // The server returns years ascending; a library's newest years are the ones anyone
            // looks for first.
            years = filters.years.orEmpty().sortedDescending(),
        )
    }
}
