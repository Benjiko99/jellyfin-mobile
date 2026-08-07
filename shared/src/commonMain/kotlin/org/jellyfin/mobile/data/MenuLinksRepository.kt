package org.jellyfin.mobile.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jellyfin.mobile.domain.MenuLink
import org.jellyfin.mobile.network.JellyfinApi

/**
 * The administrator's custom menu links, from the web client's `config.json`.
 *
 * Never fails. jellyfin-web treats a config it cannot read as "no links" rather than as an error,
 * and the reasons it might not read are all ordinary: a server run with `--nowebclient`, a reverse
 * proxy that exposes `/` but not `/web/`, an admin who has never edited the file. None of those are
 * worth a message in a navigation drawer.
 *
 * Swallowing everything matters more here than the empty list suggests. `config.json` is static
 * content, so a proxy with its own authentication in front of `/web/` answers 401 — and an
 * unhandled [org.jellyfin.mobile.network.SessionExpiredException] would sign the user out of a
 * perfectly good Jellyfin session over a file that is not part of the API.
 */
class MenuLinksRepository(
    private val api: JellyfinApi,
) {
    suspend fun loadMenuLinks(): List<MenuLink> {
        val config = runCatching { api.webConfig() }

        // runCatching swallows cancellation too, and this load races the drawer being disposed.
        currentCoroutineContext().ensureActive()

        return config.getOrNull()?.menuLinks.orEmpty().mapNotNull { link ->
            // Hand-edited JSON: an entry with no name would render as a blank row, and one with no
            // URL as a row that does nothing.
            val name = link.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = link.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MenuLink(name = name.trim(), url = url.trim())
        }
    }
}
