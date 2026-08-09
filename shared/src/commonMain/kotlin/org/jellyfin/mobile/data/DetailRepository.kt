package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.EpisodeArtwork
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.PlaylistEntry
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

    /**
     * A playlist's entries, in playlist order.
     *
     * [MediaItem] rather than [Episode] because a playlist holds whatever was put in it — films,
     * episodes, or both — and one row has to render all of them. [CardShape.Thumb] follows from
     * that: a list mixing 2:3 posters with 16:9 stills reads as broken, and every kind of item has
     * landscape artwork to fall back through where a film has no still of its own.
     *
     * An episode in a playlist keeps its own artwork, the [EpisodeArtwork.Own] default: it is there
     * because someone put *it* there, not as a stand-in for its show.
     */
    suspend fun loadPlaylistItems(playlistId: String): List<PlaylistEntry> {
        val serverUrl = session.requireServerUrl()
        return api.playlistItems(playlistId).items.map { dto ->
            PlaylistEntry(
                item = dto.toMediaItem(serverUrl, CardShape.Thumb),
                // The same mapper the player's skip buttons use, from the same DTO: tapping an entry
                // and skipping onto it are two ways of starting the same thing, and they would be a
                // bug waiting to happen if they read the item differently.
                playback = dto.toAdjacentItem(),
            )
        }
    }
}
