package org.jellyfin.mobile.domain

/**
 * A link to this item on an external site — IMDb, TMDb, TheTVDB and so on.
 *
 * The set depends on which metadata providers the server has configured, so it is whatever the
 * server offers rather than a fixed list we decide on.
 */
data class ExternalLink(
    val name: String,
    val url: String,
)
