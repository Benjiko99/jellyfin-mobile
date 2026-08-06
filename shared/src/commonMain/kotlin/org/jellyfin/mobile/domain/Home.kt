package org.jellyfin.mobile.domain

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

data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    /** Resume position as a 0..1 fraction, or null when the item hasn't been started. */
    val progress: Float?,
    val watched: Boolean,
    /** Decides where tapping the card goes — a person has a screen of their own. */
    val kind: ItemKind = ItemKind.Other,
)

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
) {
    Resume(CardShape.Thumb),
    NextUp(CardShape.Thumb),

    /**
     * Recently added within one library. The library is [HomeSection.parentId] and the type it
     * holds is [HomeSection.libraryItemKind], since that varies per library.
     */
    LatestInLibrary(CardShape.Poster),
    FavoriteMovies(CardShape.Poster, ItemKind.Movie),
    FavoriteSeries(CardShape.Poster, ItemKind.Series),
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
    SearchEpisodes(CardShape.Thumb, ItemKind.Episode),
    SearchCollections(CardShape.Poster, ItemKind.BoxSet),
    SearchPeople(CardShape.Poster, ItemKind.Person),
}

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val kind: SectionKind,
    /** Library id for [SectionKind.LatestInLibrary]; null for every other kind. */
    val parentId: String? = null,
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
    val cardShape: CardShape get() = kind.cardShape
}
