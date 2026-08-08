package org.jellyfin.mobile.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.TEST_SERVER_URL
import org.jellyfin.mobile.network.jsonEngine
import org.jellyfin.mobile.network.testApi
import org.jellyfin.mobile.player.DecoderCapabilities
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private object TestCapabilities : DecoderCapabilities {
    override val videoCodecs = mapOf("h264" to setOf("high"))
    override val audioCodecs = setOf("aac")
    override val embeddedSubtitleFormats = setOf("srt")
    override val externalSubtitleFormats = setOf("srt")
}

private fun repository(engine: MockEngine) = PlaybackRepository(testApi(engine), TestCapabilities)

/** A `PlaybackInfoResponse` with one media source, shaped by [source]. */
private fun response(source: String, playSessionId: String? = "ps-1") = """
    {
      ${playSessionId?.let { "\"PlaySessionId\": \"$it\"," } ?: ""}
      "MediaSources": [ $source ]
    }
""".trimIndent()

class PlaybackRepositoryTest {
    @Test
    fun `direct plays a file through the stream route`() = runTest {
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "File",
                  "Container": "mkv",
                  "RunTimeTicks": 72000000000,
                  "SupportsDirectPlay": true,
                  "SupportsDirectStream": true,
                  "SupportsTranscoding": true
                }
                """.trimIndent(),
            ),
        )

        val source = repository(engine).resolve("item-1")

        assertEquals(PlayMethod.DirectPlay, source.playMethod)
        assertEquals(
            "$TEST_SERVER_URL/Videos/item-1/stream" +
                "?static=true&playSessionId=ps-1&mediaSourceId=ms-1&deviceId=device-id",
            source.url,
        )
        assertFalse(source.isHls)
        assertEquals("ps-1", source.playSessionId)
    }

    @Test
    fun `direct plays an http source at the path the server gave`() = runTest {
        // Live TV and remote shares arrive as a URL we play directly rather than through /Videos.
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "Http",
                  "Path": "http://tuner.local/stream.m3u8",
                  "SupportsDirectPlay": true
                }
                """.trimIndent(),
            ),
        )

        val source = repository(engine).resolve("item-1")

        assertEquals(PlayMethod.DirectPlay, source.playMethod)
        assertEquals("http://tuner.local/stream.m3u8", source.url)
        assertTrue(source.isHls)
    }

    @Test
    fun `falls back to a remux when direct play is refused`() = runTest {
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "File",
                  "Container": "mkv",
                  "SupportsDirectPlay": false,
                  "SupportsDirectStream": true,
                  "SupportsTranscoding": true
                }
                """.trimIndent(),
            ),
        )

        val source = repository(engine).resolve("item-1")

        assertEquals(PlayMethod.DirectStream, source.playMethod)
        assertEquals(
            "$TEST_SERVER_URL/Videos/item-1/stream.mkv" +
                "?playSessionId=ps-1&mediaSourceId=ms-1&deviceId=device-id",
            source.url,
        )
        assertFalse(source.isHls)
    }

    @Test
    fun `falls back to the transcode URL the server prepared`() = runTest {
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "File",
                  "Container": "mkv",
                  "SupportsDirectPlay": false,
                  "SupportsDirectStream": false,
                  "SupportsTranscoding": true,
                  "TranscodingUrl": "/videos/item-1/master.m3u8?PlaySessionId=ps-1&api_key=k",
                  "TranscodingSubProtocol": "hls"
                }
                """.trimIndent(),
            ),
        )

        val source = repository(engine).resolve("item-1")

        assertEquals(PlayMethod.Transcode, source.playMethod)
        // Used verbatim — the server already built the query string.
        assertEquals("$TEST_SERVER_URL/videos/item-1/master.m3u8?PlaySessionId=ps-1&api_key=k", source.url)
        assertTrue(source.isHls)
    }

    @Test
    fun `refuses a transcode protocol it cannot read`() = runTest {
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "SupportsTranscoding": true,
                  "TranscodingUrl": "/videos/item-1/stream.ts",
                  "TranscodingSubProtocol": "http"
                }
                """.trimIndent(),
            ),
        )

        val error = assertFailsWith<UnsupportedContentException> { repository(engine).resolve("item-1") }
        assertContains(error.message.orEmpty(), "http")
    }

    @Test
    fun `fails when the server offers nothing playable`() = runTest {
        val engine = jsonEngine(response("""{ "Id": "ms-1", "Protocol": "File" }"""))

        assertFailsWith<UnsupportedContentException> { repository(engine).resolve("item-1") }
    }

    @Test
    fun `fails when there are no media sources at all`() = runTest {
        val engine = jsonEngine("""{ "PlaySessionId": "ps-1", "MediaSources": [] }""")

        assertFailsWith<UnsupportedContentException> { repository(engine).resolve("item-1") }
    }

    @Test
    fun `fails without a play session, which the server needs to track the stream`() = runTest {
        val engine = jsonEngine(
            response(
                """{ "Id": "ms-1", "Protocol": "File", "SupportsDirectPlay": true }""",
                playSessionId = null,
            ),
        )

        assertFailsWith<UnsupportedContentException> { repository(engine).resolve("item-1") }
    }

    @Test
    fun `prefers the source matching the item over other renditions`() = runTest {
        // Media source ids come back without dashes; matching has to normalise both sides or we
        // silently play the wrong version of a film that has several.
        val engine = jsonEngine(
            """
            {
              "PlaySessionId": "ps-1",
              "MediaSources": [
                { "Id": "other-source", "Protocol": "File", "SupportsDirectPlay": true },
                { "Id": "aabbccdd", "Protocol": "File", "Container": "mkv", "SupportsDirectPlay": true }
              ]
            }
            """.trimIndent(),
        )

        val source = repository(engine).resolve("aa-bb-ccdd")

        assertEquals("aabbccdd", source.mediaSourceId)
    }

    @Test
    fun `adopts the server's default tracks when we did not ask for any`() = runTest {
        // The server echoes only its own defaults, so without this the track menu would show
        // nothing selected while a track was plainly playing.
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "File",
                  "SupportsDirectPlay": true,
                  "DefaultAudioStreamIndex": 1,
                  "DefaultSubtitleStreamIndex": 3,
                  "MediaStreams": [
                    { "Index": 1, "Type": "Audio", "Codec": "eac3", "Language": "eng", "DisplayTitle": "English" },
                    { "Index": 3, "Type": "Subtitle", "Codec": "subrip", "DisplayTitle": "English", "DeliveryMethod": "Embed" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val source = repository(engine).resolve("item-1")

        assertEquals(1, source.selectedAudioIndex)
        assertEquals(3, source.selectedSubtitleIndex)
        assertEquals(UiText.Raw("English"), source.selectedAudio?.label)
        assertEquals(listOf(1), source.audioTracks.map { it.index })
        assertEquals(listOf(3), source.subtitleTracks.map { it.index })
    }

    @Test
    fun `asks the server for the track the user picked`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond(
                content = response("""{ "Id": "ms-1", "Protocol": "File", "SupportsDirectPlay": true }"""),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val source = repository(engine).resolve("item-1", audioStreamIndex = 2, subtitleStreamIndex = 5)

        assertContains(body.orEmpty(), "\"AudioStreamIndex\":2")
        assertContains(body.orEmpty(), "\"SubtitleStreamIndex\":5")
        // The request wins over the server's defaults — otherwise switching tracks would appear to
        // do nothing.
        assertEquals(2, source.selectedAudioIndex)
        assertEquals(5, source.selectedSubtitleIndex)
    }

    @Test
    fun `caps the stream at the bitrate the user picked`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond(
                content = response("""{ "Id": "ms-1", "Protocol": "File", "SupportsDirectPlay": true }"""),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val source = repository(engine).resolve("item-1", maxStreamingBitrate = 4_000_000)

        assertContains(body.orEmpty(), "\"MaxStreamingBitrate\":4000000")
        assertEquals(4_000_000, source.maxStreamingBitrate)
    }

    @Test
    fun `sends no cap at all on auto, leaving the ceiling to the device profile`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond(
                content = response("""{ "Id": "ms-1", "Protocol": "File", "SupportsDirectPlay": true }"""),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val source = repository(engine).resolve("item-1")

        // The device profile carries a ceiling of its own, so "no cap" is the absence of a *second*
        // MaxStreamingBitrate rather than of the name entirely.
        assertEquals(1, Regex("MaxStreamingBitrate").findAll(body.orEmpty()).count())
        assertNull(source.maxStreamingBitrate)
    }

    @Test
    fun `reports what the source is, for the quality ladder and the debug overlay`() = runTest {
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "File",
                  "Container": "mkv",
                  "Bitrate": 8400000,
                  "SupportsDirectPlay": true,
                  "MediaStreams": [
                    { "Index": 0, "Type": "Video", "Codec": "h264", "Width": 1920, "Height": 1080, "BitRate": 8000000 },
                    { "Index": 1, "Type": "Audio", "Codec": "eac3" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val stream = repository(engine).resolve("item-1").stream

        assertEquals("mkv", stream.container)
        assertEquals("h264", stream.videoCodec)
        assertEquals(1920, stream.width)
        assertEquals(1080, stream.height)
        // The source's total, not the video stream's — that is what "what my file is" means.
        assertEquals(8_400_000, stream.bitrate)
    }

    @Test
    fun `falls back to the video stream's bitrate when the source carries no total`() = runTest {
        val engine = jsonEngine(
            response(
                """
                {
                  "Id": "ms-1",
                  "Protocol": "File",
                  "SupportsDirectPlay": true,
                  "MediaStreams": [
                    { "Index": 0, "Type": "Video", "Codec": "hevc", "BitRate": 6000000 }
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(6_000_000, repository(engine).resolve("item-1").stream.bitrate)
    }

    @Test
    fun `sends the device profile, with protocols in the casing the server expects`() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond(
                content = response("""{ "Id": "ms-1", "Protocol": "File", "SupportsDirectPlay": true }"""),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        repository(engine).resolve("item-1")

        val sent = body.orEmpty()
        assertContains(sent, "\"DirectPlayProfiles\"")
        assertContains(sent, "\"VideoCodec\":\"h264\"")
        // MediaStreamProtocol is lowercase on the wire; JsonNamingStrategy does not touch enum
        // entries, so this only holds because of the explicit @SerialName.
        assertContains(sent, "\"Protocol\":\"hls\"")
        // The dash-stripped id the server matches on.
        assertContains(sent, "\"MediaSourceId\":\"item1\"")
    }
}
