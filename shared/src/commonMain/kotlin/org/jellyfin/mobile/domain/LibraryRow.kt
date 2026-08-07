package org.jellyfin.mobile.domain

/**
 * A horizontal row on one of the library's rows-shaped tabs.
 *
 * Deliberately not [HomeSection]. That type carries a [SectionKind], which is the recipe for
 * re-running a *home* query, and none of these rows are one: a genre row's full list is the grid
 * tab filtered by that genre, which is a place the user already is rather than a new screen.
 */
data class LibraryRow(
    val id: String,
    /**
     * Stored rather than derived, unlike [HomeSection.title]: these headings are a genre's name, an
     * air date and a recommendation the server explained, so there is no kind to derive them from.
     */
    val title: UiText,
    val items: List<MediaItem>,
    val cardShape: CardShape,
    /**
     * Where the row's chevron leads, or null for a row that is only ever a preview. Suggestions and
     * upcoming episodes have nothing behind them; a genre or a network does.
     */
    val target: LibraryRowTarget? = null,
)

/** What tapping a row header narrows the library to. */
sealed interface LibraryRowTarget {
    /** Matched by name, which is the form `/Genres` returns and `/Items?genres=` expects back. */
    data class Genre(val name: String) : LibraryRowTarget

    /** Matched by id: studio names collide across regions, and `/Items` accepts `studioIds`. */
    data class Studio(val id: String, val name: String) : LibraryRowTarget
}
