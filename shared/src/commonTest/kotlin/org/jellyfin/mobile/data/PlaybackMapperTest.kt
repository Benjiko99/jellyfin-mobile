package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.MediaStream
import org.jellyfin.mobile.network.dto.UserItemDataDto
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_track_unnamed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun resolve(path: String) = "http://jellyfin.test$path"

private val streams = listOf(
    MediaStream(index = 0, type = "Video", codec = "h264"),
    MediaStream(
        index = 1,
        type = "Audio",
        codec = "eac3",
        language = "eng",
        displayTitle = "English - Dolby Digital+",
        isDefault = true,
    ),
    MediaStream(index = 2, type = "Audio", codec = "aac", language = "jpn", displayTitle = "Japanese - AAC - Stereo"),
    MediaStream(
        index = 3,
        type = "Subtitle",
        codec = "subrip",
        language = "eng",
        displayTitle = "English",
        deliveryMethod = "External",
        deliveryUrl = "/Videos/item-1/ms-1/Subtitles/3/Stream.srt",
    ),
    MediaStream(
        index = 4,
        type = "Subtitle",
        codec = "pgssub",
        language = "eng",
        displayTitle = "English (PGS)",
        deliveryMethod = "Encode",
    ),
)

class PlaybackMapperTest {
    @Test
    fun `keeps only audio streams, in server order`() {
        val tracks = streams.audioTracks()

        assertEquals(listOf(1, 2), tracks.map { it.index })
        assertEquals(UiText.Raw("English - Dolby Digital+"), tracks.first().label)
        assertEquals("jpn", tracks[1].language)
    }

    @Test
    fun `still offers a subtitle the server is burning in`() {
        // Delivery method describes this negotiation, not selectability — switching re-negotiates.
        // Filtering on it hid the *active* track from its own menu whenever the server transcoded,
        // because that is exactly when it burns subtitles in.
        val tracks = streams.subtitleTracks(::resolve)

        assertEquals(listOf(3, 4), tracks.map { it.index })
    }

    @Test
    fun `resolves a sidecar subtitle to an absolute URL`() {
        val subtitle = streams.subtitleTracks(::resolve).first { it.index == 3 }

        assertEquals("http://jellyfin.test/Videos/item-1/ms-1/Subtitles/3/Stream.srt", subtitle.deliveryUrl)
    }

    @Test
    fun `a burned-in subtitle has nothing for the player to fetch`() {
        // No deliveryUrl is what tells the engine it is already in the picture.
        assertNull(streams.subtitleTracks(::resolve).first { it.index == 4 }.deliveryUrl)
    }

    @Test
    fun `an embedded subtitle has no delivery URL to fetch`() {
        val embedded = listOf(
            MediaStream(
                index = 2,
                type = "Subtitle",
                codec = "subrip",
                displayTitle = "English",
                deliveryMethod = "Embed",
            ),
        )

        assertNull(embedded.subtitleTracks(::resolve).single().deliveryUrl)
    }

    @Test
    fun `falls back through title and language for a label`() {
        val unlabelled = listOf(
            MediaStream(index = 1, type = "Audio", title = "Commentary"),
            MediaStream(index = 2, type = "Audio", language = "fra"),
            MediaStream(index = 3, type = "Audio"),
        )
        val tracks = unlabelled.audioTracks()

        assertEquals(
            listOf(
                UiText.Raw("Commentary"),
                UiText.Raw("fra"),
                UiText.Resource(Res.string.player_track_unnamed, listOf("3")),
            ),
            tracks.map { it.label },
        )
    }

    @Test
    fun `an item with no alternate streams yields no tracks`() {
        val videoOnly = listOf(MediaStream(index = 0, type = "Video", codec = "h264"))

        assertEquals(emptyList(), videoOnly.audioTracks())
        assertEquals(emptyList(), videoOnly.subtitleTracks(::resolve))
    }

    @Test
    fun `takes the items either side of the one playing`() {
        val neighbours = adjacent.neighboursOf("ep-4")

        assertEquals("ep-3", neighbours.previous?.id)
        assertEquals("ep-5", neighbours.next?.id)
        assertEquals("Northern Line", neighbours.next?.seriesName)
        // parentIndexNumber is the season, indexNumber the episode — the player's header spells both.
        assertEquals(2, neighbours.next?.seasonNumber)
        assertEquals(5, neighbours.next?.episodeNumber)
    }

    @Test
    fun `carries a half-watched neighbour's own resume point`() {
        assertEquals(90_000_000L, adjacent.neighboursOf("ep-4").previous?.startPositionTicks)
        // Never started, which is the usual case for whatever comes next.
        assertEquals(0L, adjacent.neighboursOf("ep-4").next?.startPositionTicks)
    }

    @Test
    fun `the first episode of a series has nothing before it`() {
        // The server returns two items rather than three at either end, so position cannot be
        // assumed: taking first() here would offer the episode playing as its own previous.
        val neighbours = adjacent.drop(1).neighboursOf("ep-4")

        assertNull(neighbours.previous)
        assertEquals("ep-5", neighbours.next?.id)
    }

    @Test
    fun `an answer that does not contain the item yields no neighbours`() {
        val neighbours = adjacent.neighboursOf("ep-99")

        assertNull(neighbours.previous)
        assertNull(neighbours.next)
    }

    @Test
    fun `finds a film in the middle of a playlist`() {
        // The same function over a whole playlist rather than a three-item adjacentTo answer, and
        // over films rather than episodes: no numbers, and a year for the player's header instead.
        val playlist = listOf(
            film("film-1", "Slack Water", year = 2016),
            film("film-2", "The Cartographer", year = 2019),
            film("film-3", "The Cut", year = 2021),
        )

        val neighbours = playlist.neighboursOf("film-2")

        assertEquals("film-1", neighbours.previous?.id)
        assertEquals(2021, neighbours.next?.year)
        assertNull(neighbours.next?.seriesName)
        assertNull(neighbours.next?.episodeNumber)
    }

    @Test
    fun `a repeated playlist entry resolves to its first appearance`() {
        // Two appearances of one item are told apart by PlaylistItemId, which the player does not
        // carry — so skipping from the second lands after the first.
        val playlist = listOf(
            film("film-1", "Slack Water", year = 2016),
            film("film-2", "The Cartographer", year = 2019),
            film("film-1", "Slack Water", year = 2016),
            film("film-3", "The Cut", year = 2021),
        )

        assertEquals("film-2", playlist.neighboursOf("film-1").next?.id)
    }
}

/** An `adjacentTo` answer: the episode asked about, between its two neighbours. */
private val adjacent = listOf(
    episode("ep-3", "Slack Water", number = 3, positionTicks = 90_000_000),
    episode("ep-4", "The Undertow", number = 4),
    episode("ep-5", "The Cut", number = 5),
)

private fun film(id: String, name: String, year: Int) = BaseItemDto(
    id = id,
    name = name,
    type = "Movie",
    productionYear = year,
)

private fun episode(id: String, name: String, number: Int, positionTicks: Long = 0) = BaseItemDto(
    id = id,
    name = name,
    type = "Episode",
    seriesName = "Northern Line",
    indexNumber = number,
    parentIndexNumber = 2,
    userData = UserItemDataDto(playbackPositionTicks = positionTicks),
)
