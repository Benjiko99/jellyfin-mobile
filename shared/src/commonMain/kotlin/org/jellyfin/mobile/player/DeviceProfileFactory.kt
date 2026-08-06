// Derived from jellyfin-android, GPL-2.0
// https://github.com/jellyfin/jellyfin-android/blob/master/app/src/main/java/org/jellyfin/mobile/player/deviceprofile/DeviceProfileBuilder.kt

package org.jellyfin.mobile.player

import org.jellyfin.mobile.network.dto.CodecProfile
import org.jellyfin.mobile.network.dto.CodecType
import org.jellyfin.mobile.network.dto.ContainerProfile
import org.jellyfin.mobile.network.dto.DeviceProfile
import org.jellyfin.mobile.network.dto.DirectPlayProfile
import org.jellyfin.mobile.network.dto.DlnaProfileType
import org.jellyfin.mobile.network.dto.MediaStreamProtocol
import org.jellyfin.mobile.network.dto.ProfileCondition
import org.jellyfin.mobile.network.dto.ProfileConditionType
import org.jellyfin.mobile.network.dto.ProfileConditionValue
import org.jellyfin.mobile.network.dto.SubtitleDeliveryMethod
import org.jellyfin.mobile.network.dto.SubtitleProfile
import org.jellyfin.mobile.network.dto.TranscodingProfile

/**
 * Which codecs each container may legally carry.
 *
 * A property of the formats, not of the device — so it is shared, and intersected with
 * [DecoderCapabilities] to produce the profile. jellyfin-android keeps this as three parallel
 * arrays that must stay index-aligned (its own comment says "IMPORTANT: Must have same length");
 * one list of triples removes that footgun without changing the data.
 */
private data class ContainerFormat(
    val container: String,
    val videoCodecs: List<String> = emptyList(),
    val audioCodecs: Collection<String> = emptyList(),
)

/**
 * PCM variants, spelled out because Jellyfin names each one separately.
 *
 * Shared with the platform [DecoderCapabilities] implementations: the profile and the capability
 * set must agree, and three copies were three chances to disagree.
 */
internal val PCM_CODECS = setOf(
    "pcm_s8",
    "pcm_s16be",
    "pcm_s16le",
    "pcm_s24le",
    "pcm_s32le",
    "pcm_f32le",
    "pcm_alaw",
    "pcm_mulaw",
)

private val CONTAINER_FORMATS = listOf(
    ContainerFormat(
        container = "mp4",
        videoCodecs = listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
        audioCodecs = listOf("mp1", "mp2", "mp3", "aac", "alac", "ac3", "opus"),
    ),
    ContainerFormat(
        container = "fmp4",
        videoCodecs = listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp9"),
        audioCodecs = listOf("mp3", "aac", "ac3", "eac3"),
    ),
    ContainerFormat(
        container = "webm",
        videoCodecs = listOf("vp8", "vp9", "av1"),
        audioCodecs = listOf("vorbis", "opus"),
    ),
    ContainerFormat(
        container = "mkv",
        videoCodecs = listOf("mpeg1video", "mpeg2video", "h263", "mpeg4", "h264", "hevc", "av1", "vp8", "vp9"),
        audioCodecs = PCM_CODECS +
            listOf("mp1", "mp2", "mp3", "aac", "vorbis", "opus", "flac", "alac", "ac3", "eac3", "dts", "mlp", "truehd"),
    ),
    ContainerFormat(container = "mp3", audioCodecs = listOf("mp3")),
    ContainerFormat(container = "ogg", audioCodecs = listOf("vorbis", "opus", "flac")),
    ContainerFormat(container = "wav", audioCodecs = PCM_CODECS),
    ContainerFormat(
        container = "mpegts",
        videoCodecs = listOf("mpeg1video", "mpeg2video", "mpeg4", "h264", "hevc"),
        audioCodecs = PCM_CODECS + listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd"),
    ),
    ContainerFormat(
        container = "flv",
        videoCodecs = listOf("mpeg4", "h264"),
        audioCodecs = listOf("mp3", "aac"),
    ),
    ContainerFormat(container = "aac", audioCodecs = listOf("aac")),
    ContainerFormat(container = "flac", audioCodecs = listOf("flac")),
    ContainerFormat(
        container = "3gp",
        videoCodecs = listOf("h263", "mpeg4", "h264", "hevc"),
        audioCodecs = listOf("3gpp", "aac", "flac"),
    ),
)

/**
 * Audio codecs the server may encode *to* when it has to transcode, per target container. Narrower
 * than what we can decode: PCM is deliberately absent, since asking the server to produce
 * uncompressed audio for a stream we are transcoding to save bandwidth defeats the point.
 */
private val TS_TRANSCODE_AUDIO = listOf("mp1", "mp2", "mp3", "aac", "ac3", "eac3", "dts", "mlp", "truehd")

/** Taken from Jellyfin Web's `browserDeviceProfile.js`. */
private const val MAX_STREAMING_BITRATE = 120_000_000
private const val MAX_STATIC_BITRATE = 100_000_000
private const val MAX_MUSIC_TRANSCODING_BITRATE = 384_000

/**
 * Assembles the [DeviceProfile] this client sends to the server.
 *
 * Everything here is the intersection of what the format allows with what [capabilities] reports —
 * declaring a codec we cannot actually decode produces a black screen, and omitting one we can
 * forces a needless transcode on the user's server.
 */
fun buildDeviceProfile(name: String, capabilities: DecoderCapabilities): DeviceProfile {
    val containerProfiles = mutableListOf<ContainerProfile>()
    val directPlayProfiles = mutableListOf<DirectPlayProfile>()
    val codecProfiles = mutableListOf<CodecProfile>()

    for (format in CONTAINER_FORMATS) {
        val video = format.videoCodecs.filter { it in capabilities.videoCodecs }
        val audio = format.audioCodecs.filter { it in capabilities.audioCodecs }

        if (video.isNotEmpty()) {
            containerProfiles += ContainerProfile(type = DlnaProfileType.Video, container = format.container)
            directPlayProfiles += DirectPlayProfile(
                type = DlnaProfileType.Video,
                container = format.container,
                videoCodec = video.joinToString(","),
                audioCodec = audio.joinToString(","),
            )
            video.forEach { codec ->
                codecProfile(format.container, codec, capabilities)?.let(codecProfiles::add)
            }
        }

        if (audio.isNotEmpty()) {
            containerProfiles += ContainerProfile(type = DlnaProfileType.Audio, container = format.container)
            directPlayProfiles += DirectPlayProfile(
                type = DlnaProfileType.Audio,
                container = format.container,
                audioCodec = audio.joinToString(","),
            )
        }
    }

    return DeviceProfile(
        name = name,
        directPlayProfiles = directPlayProfiles,
        transcodingProfiles = transcodingProfiles(capabilities),
        containerProfiles = containerProfiles,
        codecProfiles = codecProfiles,
        subtitleProfiles = subtitleProfiles(capabilities),
        maxStreamingBitrate = MAX_STREAMING_BITRATE,
        maxStaticBitrate = MAX_STATIC_BITRATE,
        musicStreamingTranscodingBitrate = MAX_MUSIC_TRANSCODING_BITRATE,
    )
}

/**
 * Constrains a video codec to the profiles the decoder advertises — a device that decodes H.264
 * High but not High 10 must say so, or the server direct-plays a file it cannot render.
 *
 * Returns `null` when the profiles are unknown, which declares the codec unconstrained rather than
 * constraining it to nothing.
 */
private fun codecProfile(container: String, codec: String, capabilities: DecoderCapabilities): CodecProfile? {
    val profiles = capabilities.videoCodecs[codec]?.takeIf { it.isNotEmpty() } ?: return null
    return CodecProfile(
        type = CodecType.Video,
        container = container,
        codec = codec,
        conditions = listOf(
            ProfileCondition(
                condition = ProfileConditionType.EqualsAny,
                property = ProfileConditionValue.VideoProfile,
                value = profiles.joinToString("|"),
                isRequired = false,
            ),
        ),
    )
}

private fun transcodingProfiles(capabilities: DecoderCapabilities): List<TranscodingProfile> = buildList {
    // HLS-in-MPEG-TS is the universally supported fallback, so list order is the preference order.
    val hlsTargets = listOf(
        "ts" to TS_TRANSCODE_AUDIO,
        "mkv" to CONTAINER_FORMATS.first { it.container == "mkv" }.audioCodecs,
    )
    for ((container, candidates) in hlsTargets) {
        val audio = candidates.filter { it in capabilities.audioCodecs }
        if (audio.isEmpty()) continue
        add(
            TranscodingProfile(
                type = DlnaProfileType.Video,
                container = container,
                videoCodec = "h264",
                audioCodec = audio.joinToString(","),
                protocol = MediaStreamProtocol.Hls,
            ),
        )
    }
    add(
        TranscodingProfile(
            type = DlnaProfileType.Audio,
            container = "mp3",
            videoCodec = "",
            audioCodec = "mp3",
            protocol = MediaStreamProtocol.Http,
        ),
    )
}

/**
 * Formats we can render ourselves are declared so the server delivers them untouched. Anything not
 * listed here the server burns into the video, which forces a full transcode — so this list is the
 * difference between direct play and re-encoding for subtitled content.
 */
private fun subtitleProfiles(capabilities: DecoderCapabilities): List<SubtitleProfile> =
    capabilities.embeddedSubtitleFormats.map { SubtitleProfile(it, SubtitleDeliveryMethod.Embed) } +
        capabilities.externalSubtitleFormats.map { SubtitleProfile(it, SubtitleDeliveryMethod.External) }
