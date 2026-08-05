package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.ExternalLink
import org.jellyfin.mobile.network.dto.BaseItemDto

/**
 * The server's own provider links, for items and people alike.
 *
 * Uses `ExternalUrls` rather than assembling addresses from `ProviderIds`: the server already
 * generates these for whichever metadata providers it has configured, so a title scraped from TMDb
 * gets a TMDb link and not a fabricated IMDb one.
 *
 * Entries missing a name or URL are dropped rather than rendered as an unlabelled or dead chip,
 * and duplicates are collapsed — a server with several providers enabled can report the same site
 * more than once.
 */
internal fun BaseItemDto.externalLinks(): List<ExternalLink> =
    externalUrls.orEmpty().mapNotNull { external ->
        val name = external.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val url = external.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ExternalLink(name = name, url = url)
    }.distinctBy { it.url }
