package org.jellyfin.mobile.domain

/**
 * What a library holds, from its `CollectionType`.
 *
 * The *types* are a fixed server-side enum; the libraries themselves are not. An administrator
 * decides how many exist, what each is called and what type it is, so a server can have two movie
 * libraries, none, or one named "Films" — which is why the drawer is built from `/UserViews`
 * rather than from a list of names we chose.
 *
 * [Playlists] and [Collections] are the exception: the server creates those two itself, which is
 * why they can be relied on to exist in a way "Movies" cannot.
 */
enum class LibraryKind(val collectionType: String?) {
    Movies("movies"),
    TvShows("tvshows"),
    Playlists("playlists"),
    Collections("boxsets"),

    /** Music, books, photos, home videos — libraries we have no tailored browse view for yet. */
    Other(null),
    ;

    companion object {
        fun from(collectionType: String?): LibraryKind =
            entries.firstOrNull { it.collectionType != null && it.collectionType == collectionType }
                ?: Other
    }
}

/** One of the user's libraries, as the drawer lists it and the browse screen queries it. */
data class LibraryView(
    val id: String,
    val name: String,
    val kind: LibraryKind,
)

/**
 * One tab of a library browse screen.
 *
 * The tab list is **not** an API concept. jellyfin-web keeps a table of tabs per collection type in
 * its own source — `src/apps/modern/features/libraries/constants/views/{movies,tvshows}.ts`, a
 * `Record<number, LibraryTabContent>` — and the capability flags on each entry
 * (`isAlphabetPickerEnabled`, `isBtnFilterEnabled`, …) are hardcoded there too. This is that table,
 * so the same reasoning applies: it is a client decision about how to present a library, and the
 * server has no opinion on it.
 *
 * @param itemKind what the grid lists, or null to list whatever the library holds — which is what
 * the [LibraryKind.Other] libraries need, since we do not know what is in them.
 * @param alphabetPicker off where the tab is not ordered by name in the first place. Upstream turns
 * it off for episodes for the same reason.
 * @param favoritesOnly the Favorites tab is the same query as the tab beside it plus `isFavorite`,
 * rather than a screen of its own.
 */
enum class LibraryTab(
    val label: String,
    val itemKind: ItemKind?,
    val cardShape: CardShape,
    val alphabetPicker: Boolean = true,
    val favoritesOnly: Boolean = false,
) {
    Shows("Shows", ItemKind.Series, CardShape.Poster),
    Movies("Movies", ItemKind.Movie, CardShape.Poster),

    /**
     * Every episode in the library, flat. No alphabet picker: episode names are not what anyone
     * scans this list by, and upstream leaves it off here too.
     */
    Episodes("Episodes", ItemKind.Episode, CardShape.Thumb, alphabetPicker = false),

    FavoriteMovies("Favorites", ItemKind.Movie, CardShape.Poster, favoritesOnly = true),

    /**
     * Box sets, which do not live inside the movie or TV library they group — they have a library
     * of their own. The tab therefore queries every box set on the server rather than the current
     * library's, which is also what the web client's Collections tab shows.
     */
    Collections("Collections", ItemKind.BoxSet, CardShape.Poster),

    PlaylistItems("Playlists", ItemKind.Playlist, CardShape.Poster),

    /** The single tab a library we have no tailored view for gets. */
    Everything("All", null, CardShape.Poster),
    ;

    companion object {
        /**
         * The tabs for a library.
         *
         * Short of what jellyfin-web offers: Suggestions, Upcoming, Genres and TV Networks are not
         * here yet. Each is a different screen rather than this grid with different parameters —
         * Genres and Networks navigate one level deeper, Upcoming groups by air date, Suggestions
         * is a rows screen — so they are their own piece of work rather than an entry in this list.
         */
        fun forLibrary(kind: LibraryKind): List<LibraryTab> = when (kind) {
            LibraryKind.Movies -> listOf(Movies, FavoriteMovies, Collections)
            LibraryKind.TvShows -> listOf(Shows, Episodes, Collections)
            LibraryKind.Playlists -> listOf(PlaylistItems)
            LibraryKind.Collections -> listOf(Collections)
            LibraryKind.Other -> listOf(Everything)
        }
    }
}
