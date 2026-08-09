package org.jellyfin.mobile.ui

import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_title_episode
import org.jellyfin.mobile.resources.player_title_episode_unnumbered
import kotlin.test.Test
import kotlin.test.assertEquals

private fun route(
    title: String,
    seriesName: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
) = PlayerRoute(
    itemId = "item-1",
    title = title,
    startPositionTicks = 0,
    seriesName = seriesName,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
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
    fun `a movie keeps its own title, and so does an episode with no series name`() {
        // Nothing of ours to translate in either case — it is the title the server wrote.
        assertEquals(UiText.Raw("The Cartographer"), route("The Cartographer").header())
        assertEquals(
            UiText.Raw("The Undertow"),
            route("The Undertow", seriesName = " ", seasonNumber = 2, episodeNumber = 4).header(),
        )
    }
}
