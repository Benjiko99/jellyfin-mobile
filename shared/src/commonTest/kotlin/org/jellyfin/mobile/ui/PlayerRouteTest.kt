package org.jellyfin.mobile.ui

import org.jellyfin.mobile.domain.AdjacentItem
import org.jellyfin.mobile.domain.PlaybackQueue
import org.jellyfin.mobile.domain.PlaylistEntry
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_title_episode
import org.jellyfin.mobile.resources.player_title_episode_unnumbered
import org.jellyfin.mobile.resources.player_title_year
import org.jellyfin.mobile.ui.preview.PreviewData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun route(
    title: String,
    seriesName: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    year: Int? = null,
) = PlayerRoute(
    itemId = "item-1",
    title = title,
    startPositionTicks = 0,
    seriesName = seriesName,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    year = year,
)

/**
 * The header the player writes out of the parts its route carries, since a [UiText] cannot ride in
 * a serializable route. Padding and the fallbacks are the whole of the logic.
 */
class PlayerRouteTest {
    @Test
    fun `an episode is named after its show, with padded numbers`() {
        assertEquals(
            UiText.Resource(
                Res.string.player_title_episode,
                listOf("Northern Line", "The Undertow", "02", "04"),
            ),
            route("The Undertow", seriesName = "Northern Line", seasonNumber = 2, episodeNumber = 4)
                .header(),
        )
    }

    @Test
    fun `numbers past two digits are not truncated`() {
        assertEquals(
            UiText.Resource(
                Res.string.player_title_episode,
                listOf("Northern Line", "The Undertow", "12", "114"),
            ),
            route("The Undertow", seriesName = "Northern Line", seasonNumber = 12, episodeNumber = 114)
                .header(),
        )
    }

    @Test
    fun `an unnumbered episode drops the bracket rather than printing half of one`() {
        // A special often has no season, and a scraper that failed on a filename leaves no number.
        val expected = UiText.Resource(
            Res.string.player_title_episode_unnumbered,
            listOf("Northern Line", "The Undertow"),
        )
        assertEquals(expected, route("The Undertow", seriesName = "Northern Line").header())
        assertEquals(
            expected,
            route("The Undertow", seriesName = "Northern Line", episodeNumber = 4).header(),
        )
    }

    @Test
    fun `a movie is dated, which is what separates it from its remake`() {
        assertEquals(
            UiText.Resource(Res.string.player_title_year, listOf("The Cartographer", "2019")),
            route("The Cartographer", year = 2019).header(),
        )
    }

    @Test
    fun `an episode with no series name falls in with the films`() {
        // The show is the whole of the episode wording, so without it there is nothing to build.
        assertEquals(
            UiText.Resource(Res.string.player_title_year, listOf("The Undertow", "2018")),
            route("The Undertow", seriesName = " ", seasonNumber = 2, episodeNumber = 4, year = 2018)
                .header(),
        )
    }

    @Test
    fun `an undated title stands on its own`() {
        // Nothing of ours to translate — it is the title the server wrote, and nothing else.
        assertEquals(UiText.Raw("The Cartographer"), route("The Cartographer").header())
    }

    @Test
    fun `an episode is not dated, since its numbers already place it`() {
        assertEquals(
            UiText.Resource(
                Res.string.player_title_episode,
                listOf("Northern Line", "The Undertow", "02", "04"),
            ),
            route(
                "The Undertow",
                seriesName = "Northern Line",
                seasonNumber = 2,
                episodeNumber = 4,
                year = 2018,
            ).header(),
        )
    }
}

/** Which list the skip buttons move along, and how each way into the player picks it. */
class PlayerQueueTest {
    @Test
    fun `an episode opened on its own follows its series`() {
        assertEquals(
            PlaybackQueue.Series("series-1"),
            route("The Undertow", seriesName = "Northern Line").copy(seriesId = "series-1").queue(),
        )
    }

    @Test
    fun `a film opened on its own has no queue`() {
        assertNull(route("The Cartographer", year = 2019).queue())
    }

    @Test
    fun `a playlist beats the series an episode belongs to`() {
        // Someone who pressed play inside a playlist means the playlist, even where the next entry
        // happens to be the next episode. The two orders diverge the moment a playlist is arranged
        // into anything but air order.
        val fromPlaylist = route("The Undertow", seriesName = "Northern Line")
            .copy(seriesId = "series-1", playlistId = "playlist-1")

        assertEquals(PlaybackQueue.Playlist("playlist-1"), fromPlaylist.queue())
    }

    @Test
    fun `playing an entry carries the playlist and not the series`() {
        val entry = PlaylistEntry(
            item = PreviewData.episodeInProgress,
            playback = AdjacentItem(
                id = "episode-1",
                title = "The Undertow",
                seriesName = "Northern Line",
                seasonNumber = 2,
                episodeNumber = 4,
                startPositionTicks = 90_000_000,
            ),
        )

        val playerRoute = entry.playerRoute(playlistId = "playlist-1")

        assertEquals("episode-1", playerRoute.itemId)
        assertEquals(90_000_000, playerRoute.startPositionTicks)
        // The header still reads as an episode; only the queue changes.
        assertEquals("Northern Line", playerRoute.seriesName)
        assertNull(playerRoute.seriesId)
        assertEquals(PlaybackQueue.Playlist("playlist-1"), playerRoute.queue())
    }

    @Test
    fun `playing an item from its own page resumes where it was left`() {
        val playerRoute = PreviewData.movieDetail.playerRoute()

        assertEquals(PreviewData.movieDetail.playbackPositionTicks, playerRoute.startPositionTicks)
        assertEquals(2019, playerRoute.year)
        assertNull(playerRoute.playlistId)
    }
}
