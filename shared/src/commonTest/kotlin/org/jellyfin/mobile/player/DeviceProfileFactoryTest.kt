package org.jellyfin.mobile.player

import org.jellyfin.mobile.network.dto.DlnaProfileType
import org.jellyfin.mobile.network.dto.MediaStreamProtocol
import org.jellyfin.mobile.network.dto.ProfileConditionValue
import org.jellyfin.mobile.network.dto.SubtitleDeliveryMethod
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeCapabilities(
    override val videoCodecs: Map<String, Set<String>> = emptyMap(),
    override val audioCodecs: Set<String> = emptySet(),
    override val embeddedSubtitleFormats: Set<String> = emptySet(),
    override val externalSubtitleFormats: Set<String> = emptySet(),
) : DecoderCapabilities

/** A mid-range Android device: H.264 and HEVC in hardware, no AV1, no lossless audio. */
private fun modestDevice() = FakeCapabilities(
    videoCodecs = mapOf(
        "h264" to setOf("baseline", "main", "high"),
        "hevc" to setOf("Main", "Main 10"),
    ),
    audioCodecs = setOf("aac", "mp3", "flac", "opus", "pcm_s16le"),
    embeddedSubtitleFormats = setOf("srt", "subrip"),
    externalSubtitleFormats = setOf("srt", "vtt"),
)

class DeviceProfileFactoryTest {
    @Test
    fun `declares only codecs the device can decode`() {
        val profile = buildDeviceProfile("Test", modestDevice())
        val mkv = profile.directPlayProfiles.single {
            it.container == "mkv" && it.type == DlnaProfileType.Video
        }

        assertEquals("h264,hevc", mkv.videoCodec)
        // vp9 and av1 are legal in mkv but this device has no decoder for them.
        assertFalse("av1" in mkv.videoCodec.orEmpty())
        assertFalse("vp9" in mkv.videoCodec.orEmpty())
        // Likewise ac3/dts are legal in mkv but undecodable here.
        assertEquals("pcm_s16le,mp3,aac,opus,flac", mkv.audioCodec)
    }

    @Test
    fun `omits containers it can play nothing in`() {
        // webm carries only vp8/vp9/av1 video and vorbis/opus audio; this device has opus but no
        // webm-capable video codec, so it may appear as audio-only and never as video.
        val profile = buildDeviceProfile("Test", modestDevice())

        assertTrue(profile.directPlayProfiles.none { it.container == "webm" && it.type == DlnaProfileType.Video })
        assertTrue(profile.directPlayProfiles.any { it.container == "webm" && it.type == DlnaProfileType.Audio })
    }

    @Test
    fun `a device that decodes nothing produces no direct play profiles`() {
        val profile = buildDeviceProfile("Test", FakeCapabilities())

        assertEquals(emptyList(), profile.directPlayProfiles)
        assertEquals(emptyList(), profile.containerProfiles)
        assertEquals(emptyList(), profile.codecProfiles)
    }

    @Test
    fun `constrains a codec to the profiles the decoder advertises`() {
        val profile = buildDeviceProfile("Test", modestDevice())
        val h264 = profile.codecProfiles.first { it.codec == "h264" && it.container == "mp4" }
        val condition = h264.conditions.single()

        assertEquals(ProfileConditionValue.VideoProfile, condition.property)
        assertEquals("baseline|main|high", condition.value)
        // Advisory, not required: a stricter reading makes the server transcode files that would
        // in fact have played.
        assertFalse(condition.isRequired)
    }

    @Test
    fun `declares a codec unconstrained when its profiles are unknown`() {
        // VLC reports no profile list because it decodes them all; constraining to an empty set
        // would tell the server we support nothing.
        val capabilities = FakeCapabilities(
            videoCodecs = mapOf("h264" to emptySet()),
            audioCodecs = setOf("aac"),
        )
        val profile = buildDeviceProfile("Test", capabilities)

        assertTrue(profile.directPlayProfiles.any { it.videoCodec == "h264" })
        assertEquals(emptyList(), profile.codecProfiles)
    }

    @Test
    fun `offers HLS transcode targets and an audio fallback`() {
        val profile = buildDeviceProfile("Test", modestDevice())

        val ts = profile.transcodingProfiles.first { it.container == "ts" }
        assertEquals(MediaStreamProtocol.Hls, ts.protocol)
        assertEquals("h264", ts.videoCodec)
        // Only decodable targets are offered, so the server cannot pick one we cannot play.
        assertEquals("mp3,aac", ts.audioCodec)

        val audio = profile.transcodingProfiles.first { it.type == DlnaProfileType.Audio }
        assertEquals("mp3", audio.container)
        assertEquals(MediaStreamProtocol.Http, audio.protocol)
    }

    @Test
    fun `transcode audio targets never include PCM`() {
        // Re-encoding to uncompressed audio defeats the purpose of transcoding.
        val profile = buildDeviceProfile("Test", modestDevice())
        val ts = profile.transcodingProfiles.first { it.container == "ts" }

        assertFalse("pcm" in ts.audioCodec)
    }

    @Test
    fun `splits subtitle formats by delivery method`() {
        val profile = buildDeviceProfile("Test", modestDevice())
        val embedded = profile.subtitleProfiles.filter { it.method == SubtitleDeliveryMethod.Embed }
        val external = profile.subtitleProfiles.filter { it.method == SubtitleDeliveryMethod.External }

        assertEquals(setOf("srt", "subrip"), embedded.map { it.format }.toSet())
        assertEquals(setOf("srt", "vtt"), external.map { it.format }.toSet())
    }

    @Test
    fun `carries the bitrate ceilings and the client name`() {
        val profile = buildDeviceProfile("Jellyfin Mobile", modestDevice())

        assertEquals("Jellyfin Mobile", profile.name)
        assertEquals(120_000_000, profile.maxStreamingBitrate)
        assertEquals(100_000_000, profile.maxStaticBitrate)
        assertEquals(384_000, profile.musicStreamingTranscodingBitrate)
        assertNull(profile.id)
    }

    @Test
    fun `every direct play profile has a matching container profile`() {
        // The server consults both; a container declared in one and not the other is a bug that
        // surfaces as an unexplained transcode.
        val profile = buildDeviceProfile("Test", modestDevice())

        profile.directPlayProfiles.forEach { direct ->
            assertContains(
                profile.containerProfiles.map { it.container to it.type },
                direct.container to direct.type,
            )
        }
    }
}
