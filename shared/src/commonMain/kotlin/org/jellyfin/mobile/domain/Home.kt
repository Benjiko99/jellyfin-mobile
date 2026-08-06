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
 */
enum class SectionKind {
    Resume,
    NextUp,

    /** Recently added within one library — the library is carried in [HomeSection.parentId]. */
    LatestInLibrary,
    FavoriteMovies,
    FavoriteSeries,
    FavoriteEpisodes,
    FavoriteCollections,
    FavoritePeople,
}

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val cardShape: CardShape,
    val kind: SectionKind,
    /** Library id for [SectionKind.LatestInLibrary]; null for every other kind. */
    val parentId: String? = null,
    /** Whether more items exist than the row is showing, which is what gates the "More" action. */
    val hasMore: Boolean = false,
)
