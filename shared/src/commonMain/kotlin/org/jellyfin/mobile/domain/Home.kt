package org.jellyfin.mobile.domain

import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.section_collections
import org.jellyfin.mobile.resources.section_continue_watching
import org.jellyfin.mobile.resources.section_episodes
import org.jellyfin.mobile.resources.section_movies
import org.jellyfin.mobile.resources.section_next_up
import org.jellyfin.mobile.resources.section_people
import org.jellyfin.mobile.resources.section_recently_added_in
import org.jellyfin.mobile.resources.section_tv_shows

/**
 * Domain models for the home screen. Wire DTOs stop at the repository — see AGENTS.md.
 */

/** How a row's cards are drawn. Jellyfin rows are either portrait artwork or landscape stills. */
enum class CardShape {
    /** 2:3 poster — movies, series. */
    Poster,

    /** 16:9 still — episodes in progress, next up. */
    Thumb,
}

/**
 * Whose artwork an episode card shows.
 *
 * Episodes are the only items with two answers, and the card shape does not settle it: Continue
 * Watching and Favorite Episodes are both landscape episode cards, but one is showing you a series
 * you are partway through and the other is showing you an episode you starred.
 */
enum class EpisodeArtwork {
    /** The episode is the item — favourites, search results, a library's Episodes tab. */
    Own,

    /**
     * The episode stands in for its series — Continue Watching, Next Up, and the grouped
     * "Recently Added" rows. A row of still frames here is a row of dark interchangeable images
     * with no clue which show each belongs to.
     */
    Series,
}

/** The corner badge on a card, summarising what is left to watch. */
sealed interface WatchBadge {
    /** [count] children still unplayed. For a series that is episodes, for a collection entries. */
    data class Unwatched(val count: Int) : WatchBadge

    /** Fully played — everything inside, for a container. */
    data object Watched : WatchBadge
}

data class MediaItem(
    val id: String,
    val title: String,
    /** The line under the artwork: a year, or a show and episode number. */
    val subtitle: UiText?,
    val imageUrl: String?,
    /** Resume position as a 0..1 fraction, or null when the item hasn't been started. */
    val progress: Float?,
    val watched: Boolean,
    /**
     * Children still unplayed, which the server reports only for the containers that aggregate their
     * children's played state — series, seasons and collections. Null on everything else, and on a
     * container the server declined to count.
     */
    val unwatchedCount: Int? = null,
    /** Decides where tapping the card goes — a person has a screen of their own. */
    val kind: ItemKind = ItemKind.Other,
) {
    /**
     * The badge for this card, or null for no badge.
     *
     * A count needs children to count, so only containers can carry one — and a series nobody has
     * started is badged with its *full* episode count, which is the honest answer to "how much is
     * left" and matches the web client.
     *
     * The tick goes on a watched movie as well as a finished container. Episodes are left out: the
     * only rows that could show it are favourites and search results, since Continue Watching and
     * Next Up hold unfinished episodes by construction.
     */
    val watchBadge: WatchBadge? get() {
        val remaining = if (kind.isContainer) unwatchedCount ?: 0 else 0
        return when {
            remaining > 0 -> WatchBadge.Unwatched(remaining)
            watched && (kind.isContainer || kind == ItemKind.Movie) -> WatchBadge.Watched
            else -> null
        }
    }
}

/**
 * Which query produced a row, so the "More" screen can page the same content.
 *
 * A row shows only its first few items, so the full list has to be re-fetched rather than sliced
 * out of what is already on screen.
 *
 * The per-kind facts live on the enum so a row and the screen behind its "More" action cannot
 * disagree about them — they were previously restated at each producing site, in the repository
 * that pages them, and again in the navigation route.
 */
enum class SectionKind(
    val cardShape: CardShape,
    /** The single item type this row contains, where it has one. */
    val itemKind: ItemKind? = null,
    /** Only meaningful on the rows that can hold an episode; ignored by the rest. */
    val episodeArtwork: EpisodeArtwork = EpisodeArtwork.Own,
) {
    Resume(CardShape.Thumb, episodeArtwork = EpisodeArtwork.Series),
    NextUp(CardShape.Thumb, episodeArtwork = EpisodeArtwork.Series),

    /**
     * Recently added within one library. The library is [HomeSection.parentId] and the type it
     * holds is [HomeSection.libraryItemKind], since that varies per library.
     *
     * A TV library's row is built from `/Items/Latest` with `groupItems`, which returns the episode
     * rather than the series it grouped it under — hence [EpisodeArtwork.Series].
     */
    LatestInLibrary(CardShape.Poster, episodeArtwork = EpisodeArtwork.Series),
    FavoriteMovies(CardShape.Poster, ItemKind.Movie),
    FavoriteSeries(CardShape.Poster, ItemKind.Series),

    /** A starred episode is itself, not a stand-in for its show, so it keeps its own still. */
    FavoriteEpisodes(CardShape.Thumb, ItemKind.Episode),
    FavoriteCollections(CardShape.Poster, ItemKind.BoxSet),
    FavoritePeople(CardShape.Poster, ItemKind.Person),

    /**
     * One search category. Kept as separate kinds rather than one kind plus a type, so that paging
     * a search row goes through the same [SectionKind] switch as everything else and the row and
     * its "More" screen stay in step. These carry a search term instead of a [HomeSection.parentId].
     */
    SearchMovies(CardShape.Poster, ItemKind.Movie),
    SearchSeries(CardShape.Poster, ItemKind.Series),

    /** A matched episode is the result, so it shows itself — the Series row above shows the show. */
    SearchEpisodes(CardShape.Thumb, ItemKind.Episode),
    SearchCollections(CardShape.Poster, ItemKind.BoxSet),
    SearchPeople(CardShape.Poster, ItemKind.Person),
    ;

    /**
     * The row's heading.
     *
     * Derived from the kind rather than stored on the row, because the screen behind "More" has to
     * produce the same heading and cannot be handed the text: it is reached through a serializable
     * route, and a [UiText] is not one. The route carries the kind and [libraryName], which is all
     * this needs.
     *
     * @param libraryName only [LatestInLibrary] names a library; every other kind ignores it.
     */
    fun title(libraryName: String? = null): UiText = when (this) {
        Resume -> UiText.Resource(Res.string.section_continue_watching)
        NextUp -> UiText.Resource(Res.string.section_next_up)
        LatestInLibrary ->
            UiText.Resource(Res.string.section_recently_added_in, listOf(libraryName.orEmpty()))

        FavoriteMovies, SearchMovies -> UiText.Resource(Res.string.section_movies)
        FavoriteSeries, SearchSeries -> UiText.Resource(Res.string.section_tv_shows)
        FavoriteEpisodes, SearchEpisodes -> UiText.Resource(Res.string.section_episodes)
        FavoriteCollections, SearchCollections -> UiText.Resource(Res.string.section_collections)
        FavoritePeople, SearchPeople -> UiText.Resource(Res.string.section_people)
    }
}

data class HomeSection(
    val id: String,
    val items: List<MediaItem>,
    val kind: SectionKind,
    /** Library id for [SectionKind.LatestInLibrary]; null for every other kind. */
    val parentId: String? = null,
    /** The library's name, which is what [SectionKind.LatestInLibrary] titles itself with. */
    val libraryName: String? = null,
    /** The term the `Search*` kinds matched on; null for every other kind. */
    val searchTerm: String? = null,
    /**
     * What [SectionKind.LatestInLibrary] should list, resolved from the library's collection type
     * when the row was built. Carried rather than looked up again, which would cost a request on
     * the critical path of the "More" screen's first page.
     */
    val libraryItemKind: ItemKind? = null,
    /** Whether more items exist than the row is showing, which is what gates the "More" action. */
    val hasMore: Boolean = false,
) {
    val title: UiText get() = kind.title(libraryName)
    val cardShape: CardShape get() = kind.cardShape
}
