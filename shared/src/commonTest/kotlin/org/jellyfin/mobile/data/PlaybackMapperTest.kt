package org.jellyfin.mobile.data

import org.jellyfin.mobile.network.dto.MediaStream
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
        assertEquals("English - Dolby Digital+", tracks.first().label)
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

        assertEquals(listOf("Commentary", "fra", "Track 3"), tracks.map { it.label })
    }

    @Test
    fun `an item with no alternate streams yields no tracks`() {
        val videoOnly = listOf(MediaStream(index = 0, type = "Video", codec = "h264"))

        assertEquals(emptyList(), videoOnly.audioTracks())
        assertEquals(emptyList(), videoOnly.subtitleTracks(::resolve))
    }
}
