package org.jellyfin.mobile.ui

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Serializable so Navigation Compose can encode them into the graph. */
@Serializable
data object HomeRoute

@Serializable
data class DetailRoute(val itemId: String)
