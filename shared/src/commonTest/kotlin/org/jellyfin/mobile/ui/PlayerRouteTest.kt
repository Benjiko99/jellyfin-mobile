package org.jellyfin.mobile.ui

import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_title_episode
import org.jellyfin.mobile.resources.player_title_episode_unnumbered
import org.jellyfin.mobile.resources.player_title_year
import kotlin.test.Test
import kotlin.test.assertEquals

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
