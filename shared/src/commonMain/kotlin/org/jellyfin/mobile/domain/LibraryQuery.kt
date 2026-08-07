package org.jellyfin.mobile.domain

/**
 * How a library grid is ordered.
 *
 * The wire values are `ItemSortBy` entries from the spec. Only the orderings that mean something
 * for video are here — the enum also carries album, artist and audio-bitrate orderings that would
 * silently do nothing on a movie library.
 *
 * [defaultDescending] is what each ordering means when you pick it: newest first for a date,
 * highest first for a rating, but A–Z for a name.
 */
enum class LibrarySort(
    val label: String,
    val wireValue: String,
    val defaultDescending: Boolean = false,
) {
    Name("Name", "SortName"),
    DateAdded("Date added", "DateCreated", defaultDescending = true),
    ReleaseDate("Release date", "PremiereDate", defaultDescending = true),
    CommunityRating("Community rating", "CommunityRating", defaultDescending = true),
    Runtime("Runtime", "Runtime"),
    Random("Random", "Random"),
}

/** Whether a grid shows everything, or only what the user has or has not finished. */
enum class PlayedFilter {
    All,
    Played,
    Unplayed,
}

/**
 * The state of the filter sheet: everything narrowing the grid, in one object.
 *
 * A single value the view model swaps wholesale, so that changing two filters at once cannot leave
 * the query half-updated, and so "is anything filtering?" is one question rather than six.
 *
 * The alphabet rail is deliberately *not* in here. It is a jump-to rather than a filter — it lives
 * next to the grid, it survives the filter sheet being reset, and mixing it in would make the
 * filter button light up because someone tapped "M".
 */
data class LibraryFilters(
    val sort: LibrarySort = LibrarySort.Name,
    val descending: Boolean = false,
    val played: PlayedFilter = PlayedFilter.All,
    val favoritesOnly: Boolean = false,
    val genres: Set<String> = emptySet(),
    val officialRatings: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
) {
    /** Whether anything here narrows the list, which is what badges the filter button. */
    val isFiltering: Boolean get() =
        played != PlayedFilter.All ||
            favoritesOnly ||
            genres.isNotEmpty() ||
            officialRatings.isNotEmpty() ||
            years.isNotEmpty()

    /** How many filters are applied, for the badge. Sorting is not a filter and does not count. */
    val activeCount: Int get() =
        (if (played != PlayedFilter.All) 1 else 0) +
            (if (favoritesOnly) 1 else 0) +
            genres.size + officialRatings.size + years.size

    /** Clears the filters but keeps the ordering, which is what "Reset" means in the sheet. */
    fun cleared(): LibraryFilters = LibraryFilters(sort = sort, descending = descending)
}

/**
 * What this library can be filtered by, from `/Items/Filters`.
 *
 * Fetched per library and per item type rather than assumed: the genres in a TV library are not the
 * genres in a movie library, and a server whose metadata has no age ratings should not be offered a
 * rating filter at all.
 */
data class LibraryFilterOptions(
    val genres: List<String> = emptyList(),
    val officialRatings: List<String> = emptyList(),
    /** Newest first, which is the order anyone scans a list of years in. */
    val years: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = genres.isEmpty() && officialRatings.isEmpty() && years.isEmpty()
}

/**
 * The alphabet rail down the side of a library grid.
 *
 * `#` first, for everything sorting before "a" — digits and punctuation, which is where "2001" and
 * "[REC]" end up. Ported from jellyfin-android's `AlphaBrowser`, which uses the same two-parameter
 * trick against `/Items`: a letter is `nameStartsWith`, and `#` is `nameLessThan = "a"`.
 */
object Alphabet {
    const val OTHER = "#"
    val letters: List<String> = listOf(OTHER) + ('A'..'Z').map(Char::toString)
}
