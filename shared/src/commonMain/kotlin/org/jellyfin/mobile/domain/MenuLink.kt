package org.jellyfin.mobile.domain

/**
 * An entry the server's administrator added to the client's navigation menu.
 *
 * These are how a Jellyfin install points at the companion services it runs alongside itself — a
 * Jellyseerr or Ombi request page, most often, since neither is part of Jellyfin and neither can be
 * discovered from the API. Whatever is configured is what we show; the client has no opinion about
 * which services exist or what they should be called.
 *
 * The link leaves the app: these are separate web apps with their own sessions, not screens of
 * ours.
 */
data class MenuLink(
    val name: String,
    val url: String,
)
