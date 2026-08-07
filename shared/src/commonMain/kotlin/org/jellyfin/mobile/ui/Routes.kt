package org.jellyfin.mobile.ui

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Serializable so Navigation Compose can encode them into the graph. */
@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

/**
 * One library, browsed.
 *
 * [collectionType] is the raw `CollectionType` string rather than a resolved
 * [org.jellyfin.mobile.domain.LibraryKind], so an unrecognised type survives the round trip and is
 * resolved once, at the screen. [title] rides along because it is the library's administrator-given
 * name, which the drawer already knew and the screen would otherwise refetch to draw its header.
 */
@Serializable
data class LibraryRoute(
    val libraryId: String,
    val collectionType: String?,
    val title: String,
    /**
     * Set when the screen was opened from a genre or network row, which narrows it to that one
     * thing: [narrowedTab] is the tab to show — the only one, since the tabs either side of it lead
     * back out — and [genre] or [studioId] is what narrows it.
     *
     * A genre is matched by name and a studio by id, which is what each of their endpoints returns
     * and what `/Items` accepts back.
     */
    val narrowedTab: String? = null,
    val genre: String? = null,
    val studioId: String? = null,
)

@Serializable
data class DetailRoute(val itemId: String)

@Serializable
data class PersonRoute(val personId: String)

/**
 * The full, paged list behind a row's "More" action.
 *
 * [kind] is a [org.jellyfin.mobile.domain.SectionKind] name and identifies the query to re-run.
 * Neither the card shape nor the heading is carried — both are derived from the kind, which keeps
 * the row and this screen from disagreeing, and a heading could not ride along in any case: it is a
 * [org.jellyfin.mobile.domain.UiText] and this route has to serialize. [libraryName] is the one
 * thing the heading needs beyond the kind, and only for `LatestInLibrary`. [libraryItemKind] saves
 * the "More" screen a request to discover what a library holds. [searchTerm] is what the `Search*`
 * kinds need in place of a [parentId].
 */
@Serializable
data class SectionRoute(
    val kind: String,
    val parentId: String? = null,
    val libraryName: String? = null,
    val searchTerm: String? = null,
    val libraryItemKind: String? = null,
)

/**
 * [title] and [startPositionTicks] ride along so the player can render its header and resume at the
 * right frame without waiting on a second fetch of an item the detail screen already loaded.
 */
@Serializable
data class PlayerRoute(
    val itemId: String,
    val title: String,
    val startPositionTicks: Long,
)

/**
 * The full, paged list behind a "More" button.
 *
 * [personName] is carried in the route so the header renders immediately instead of blocking on a
 * second fetch of a person we have already loaded. [kind] is a [org.jellyfin.mobile.domain.CreditKind]
 * name.
 */
@Serializable
data class PersonCreditsRoute(
    val personId: String,
    val personName: String,
    val kind: String,
)
