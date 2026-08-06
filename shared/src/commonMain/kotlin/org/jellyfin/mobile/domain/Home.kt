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

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val cardShape: CardShape,
)
