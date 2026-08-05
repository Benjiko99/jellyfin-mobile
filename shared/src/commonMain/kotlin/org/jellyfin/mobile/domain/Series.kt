package org.jellyfin.mobile.domain

/** The item types this app treats differently. Anything else is [Other]. */
enum class ItemKind {
    Movie,
    Series,
    Season,
    Episode,
    BoxSet,
    Other,
    ;

    /** Marking these watched cascades to their children server-side. */
    val isContainer: Boolean get() = this == Series || this == Season || this == BoxSet

    companion object {
        fun from(type: String?): ItemKind = when (type) {
            "Movie" -> Movie
            "Series" -> Series
            "Season" -> Season
            "Episode" -> Episode
            "BoxSet" -> BoxSet
            else -> Other
        }
    }
}

data class Season(
    val id: String,
    val name: String,
    /** Season 0 is the specials season. */
    val indexNumber: Int?,
    val imageUrl: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val indexNumber: Int?,
    val overview: String?,
    val runtime: String?,
    val imageUrl: String?,
    val isPlayed: Boolean,
    val progress: Float?,
)
