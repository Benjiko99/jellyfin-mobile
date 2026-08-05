package org.jellyfin.mobile.ui

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Serializable so Navigation Compose can encode them into the graph. */
@Serializable
data object HomeRoute

@Serializable
data class DetailRoute(val itemId: String)

@Serializable
data class PersonRoute(val personId: String)

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
