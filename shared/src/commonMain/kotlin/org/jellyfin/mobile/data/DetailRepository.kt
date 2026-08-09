package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.Season
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession

class DetailRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun load(itemId: String): ItemDetail {
        val serverUrl = session.requireServerUrl()
        return api.item(itemId).toItemDetail(serverUrl)
    }

    suspend fun loadSeasons(seriesId: String): List<Season> {
        val serverUrl = session.requireServerUrl()
        return api.seasons(seriesId).items.map { it.toSeason(serverUrl) }
    }

    suspend fun loadEpisodes(seriesId: String, seasonId: String?): List<Episode> {
        val serverUrl = session.requireServerUrl()
        return api.episodes(seriesId, seasonId).items.map { it.toEpisode(serverUrl) }
    }
}
