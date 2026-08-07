package org.jellyfin.mobile.domain

/** The item types this app treats differently. Anything else is [Other]. */
enum class ItemKind {
    Movie,
    Series,
    Season,
    Episode,
    BoxSet,
    Playlist,

    /** Not library content — people have their own screen, not an item detail page. */
    Person,
    Other,
    ;

    /** Marking these watched cascades to their children server-side. */
    val isContainer: Boolean get() = this == Series || this == Season || this == BoxSet

    companion object {
        /** The server's `BaseItemKind` values, which is also what `includeItemTypes` expects back. */
        fun from(type: String?): ItemKind = when (type) {
            "Movie" -> Movie
            "Series" -> Series
            "Season" -> Season
            "Episode" -> Episode
            "BoxSet" -> BoxSet
            "Playlist" -> Playlist
            "Person" -> Person
            else -> Other
        }
    }

    /** The wire value for this kind, for use as a query filter. Null where there isn't one. */
    val wireType: String? get() = when (this) {
        Other -> null
        else -> name
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
    val runtime: UiText?,
    val imageUrl: String?,
    val isPlayed: Boolean,
    val progress: Float?,
)
