package org.jellyfin.mobile.ui

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Serializable so Navigation Compose can encode them into the graph. */
@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

@Serializable
data class DetailRoute(val itemId: String)

@Serializable
data class PersonRoute(val personId: String)

/**
 * The full, paged list behind a row's "More" action.
 *
 * [kind] is a [org.jellyfin.mobile.domain.SectionKind] name and identifies the query to re-run.
 * Card shape is not carried — it is a property of the kind, so deriving it keeps the row and this
 * screen from disagreeing. [libraryItemKind] saves the "More" screen a request to discover what a
 * library holds. [searchTerm] is what the `Search*` kinds need in place of a [parentId].
 */
@Serializable
data class SectionRoute(
    val kind: String,
    val title: String,
    val parentId: String? = null,
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
