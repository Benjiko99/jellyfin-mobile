package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession

class DetailRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun load(itemId: String): ItemDetail {
        val serverUrl = requireNotNull(session.serverUrl) { "No server configured" }
        return api.item(itemId).toItemDetail(serverUrl)
    }

    /**
     * Both toggles return the server's resulting user data rather than a boolean we assumed, so a
     * rejected or partially-applied change is reflected instead of silently diverging.
     */
    suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean =
        api.setFavorite(itemId, favorite).isFavorite

    suspend fun setPlayed(itemId: String, played: Boolean): Boolean =
        api.setPlayed(itemId, played).played
}
