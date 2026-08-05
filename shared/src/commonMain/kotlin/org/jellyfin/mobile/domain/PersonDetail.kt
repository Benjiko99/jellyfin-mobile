package org.jellyfin.mobile.domain

/** One entry in a person's filmography. */
data class Credit(
    val id: String,
    val title: String,
    /** Year for a film or show; "Show · S1:E4" for an episode. */
    val subtitle: String?,
    val imageUrl: String?,
    val isPlayed: Boolean,
)

data class PersonDetail(
    val id: String,
    val name: String,
    /** The person's biography. Jellyfin stores it in the item's overview. */
    val biography: String?,
    val imageUrl: String?,
    val birthYear: Int?,
    val birthPlace: String?,
    val isFavorite: Boolean,
)

/** The filmography lists, which are queried and paged separately. */
enum class CreditKind(val title: String, val itemType: String) {
    Movies(title = "Movies", itemType = "Movie"),
    Shows(title = "Shows", itemType = "Series"),
    Episodes(title = "Episodes", itemType = "Episode"),
    ;

    companion object {
        fun from(name: String): CreditKind = entries.firstOrNull { it.name == name } ?: Movies
    }
}

/**
 * A preview of one credit list.
 *
 * [hasMore] comes from asking for one item more than we display: if the extra row comes back there
 * is at least one beyond the preview, which is cheaper than making the server count every match.
 */
data class CreditList(
    val credits: List<Credit> = emptyList(),
    val hasMore: Boolean = false,
)

/**
 * A person's work, split by type.
 *
 * Kept as three lists rather than one mixed list because they read differently: films and shows
 * are browsable artwork, while episode credits are a long flat list that is only meaningful with
 * the show name attached.
 */
data class Filmography(
    val movies: CreditList = CreditList(),
    val shows: CreditList = CreditList(),
    val episodes: CreditList = CreditList(),
) {
    val isEmpty: Boolean
        get() = movies.credits.isEmpty() && shows.credits.isEmpty() && episodes.credits.isEmpty()

    operator fun get(kind: CreditKind): CreditList = when (kind) {
        CreditKind.Movies -> movies
        CreditKind.Shows -> shows
        CreditKind.Episodes -> episodes
    }
}

/** One page of a full credit list. */
data class CreditPage(
    val credits: List<Credit>,
    val totalCount: Int,
)
