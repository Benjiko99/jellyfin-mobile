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

/**
 * A person's work, split by type.
 *
 * Kept as three lists rather than one mixed list because they read differently: films and shows
 * are browsable artwork, while episode credits are a long flat list that is only meaningful with
 * the show name attached.
 */
data class Filmography(
    val movies: List<Credit> = emptyList(),
    val shows: List<Credit> = emptyList(),
    val episodes: List<Credit> = emptyList(),
) {
    val isEmpty: Boolean get() = movies.isEmpty() && shows.isEmpty() && episodes.isEmpty()
}
