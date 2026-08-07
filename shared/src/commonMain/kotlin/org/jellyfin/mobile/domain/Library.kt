package org.jellyfin.mobile.domain

import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.library_tab_all
import org.jellyfin.mobile.resources.library_tab_collections
import org.jellyfin.mobile.resources.library_tab_episodes
import org.jellyfin.mobile.resources.library_tab_favorites
import org.jellyfin.mobile.resources.library_tab_genres
import org.jellyfin.mobile.resources.library_tab_movies
import org.jellyfin.mobile.resources.library_tab_networks
import org.jellyfin.mobile.resources.library_tab_playlists
import org.jellyfin.mobile.resources.library_tab_shows
import org.jellyfin.mobile.resources.library_tab_suggestions
import org.jellyfin.mobile.resources.library_tab_upcoming
import org.jetbrains.compose.resources.StringResource

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
 * What a tab's body looks like.
 *
 * [Grid] is one paged query of items. [Rows] is a screenful of horizontal strips, which is what
 * every tab that groups rather than lists needs — suggestions, air dates, genres, networks.
 */
enum class TabShape {
    Grid,
    Rows,
}

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
    val label: StringResource,
    val itemKind: ItemKind?,
    val cardShape: CardShape,
    val shape: TabShape = TabShape.Grid,
    val alphabetPicker: Boolean = true,
    val favoritesOnly: Boolean = false,
) {
    Shows(Res.string.library_tab_shows, ItemKind.Series, CardShape.Poster),
    Movies(Res.string.library_tab_movies, ItemKind.Movie, CardShape.Poster),

    /**
     * Every episode in the library, flat. No alphabet picker: episode names are not what anyone
     * scans this list by, and upstream leaves it off here too.
     */
    Episodes(Res.string.library_tab_episodes, ItemKind.Episode, CardShape.Thumb, alphabetPicker = false),

    FavoriteMovies(Res.string.library_tab_favorites, ItemKind.Movie, CardShape.Poster, favoritesOnly = true),

    /**
     * Box sets, which do not live inside the movie or TV library they group — they have a library
     * of their own. The tab therefore queries every box set on the server rather than the current
     * library's, which is also what the web client's Collections tab shows.
     */
    Collections(Res.string.library_tab_collections, ItemKind.BoxSet, CardShape.Poster),

    PlaylistItems(Res.string.library_tab_playlists, ItemKind.Playlist, CardShape.Poster),

    /**
     * What the server thinks you might watch next.
     *
     * A movie library's comes from `/Movies/Recommendations` — "Because you watched …" — on top of
     * what you have started and what has just arrived. A TV library has no such endpoint, so its
     * suggestions are Next Up and the latest episodes, which is the same substitution jellyfin-web
     * makes.
     */
    SuggestionsMovies(
        Res.string.library_tab_suggestions,
        ItemKind.Movie,
        CardShape.Poster,
        TabShape.Rows,
        alphabetPicker = false,
    ),
    SuggestionsShows(
        Res.string.library_tab_suggestions,
        ItemKind.Series,
        CardShape.Thumb,
        TabShape.Rows,
        alphabetPicker = false,
    ),

    /** Episodes that have not aired, grouped by the day they will. */
    Upcoming(Res.string.library_tab_upcoming, ItemKind.Episode, CardShape.Thumb, TabShape.Rows, alphabetPicker = false),

    /** One row per genre, each leading to the grid narrowed to it. */
    MovieGenres(Res.string.library_tab_genres, ItemKind.Movie, CardShape.Poster, TabShape.Rows, alphabetPicker = false),
    ShowGenres(Res.string.library_tab_genres, ItemKind.Series, CardShape.Poster, TabShape.Rows, alphabetPicker = false),

    /** The networks a library's series aired on. Studios, in the API's terms. */
    Networks(Res.string.library_tab_networks, ItemKind.Series, CardShape.Poster, TabShape.Rows, alphabetPicker = false),

    /** The single tab a library we have no tailored view for gets. */
    Everything(Res.string.library_tab_all, null, CardShape.Poster),
    ;

    companion object {
        /**
         * The tabs for a library, in the order jellyfin-web lists them.
         *
         * Genres and Suggestions are two entries each rather than one taking a parameter, because
         * what they contain differs by more than a type: a movie library's suggestions come from a
         * different endpoint than a TV library's, and its genre rows hold posters where TV holds
         * series. Splitting them keeps that in the table instead of in a branch further down.
         */
        fun forLibrary(kind: LibraryKind): List<LibraryTab> = when (kind) {
            LibraryKind.Movies -> listOf(Movies, SuggestionsMovies, FavoriteMovies, Collections, MovieGenres)
            LibraryKind.TvShows -> listOf(Shows, SuggestionsShows, Upcoming, ShowGenres, Networks, Episodes)
            LibraryKind.Playlists -> listOf(PlaylistItems)
            LibraryKind.Collections -> listOf(Collections)
            LibraryKind.Other -> listOf(Everything)
        }
    }
}
