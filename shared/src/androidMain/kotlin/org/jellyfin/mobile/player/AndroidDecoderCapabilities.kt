// Derived from jellyfin-android, GPL-2.0
// https://github.com/jellyfin/jellyfin-android/blob/master/app/src/main/java/org/jellyfin/mobile/player/deviceprofile/CodecHelpers.kt

package org.jellyfin.mobile.player

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat

// True because androidMain depends on org.jellyfin.media3:media3-ffmpeg-decoder and
// Media3PlayerEngine enables extension renderers. Both halves are required — declaring the codecs
// without the renderer enabled yields silent playback.
actual fun platformDecoderCapabilities(): DecoderCapabilities =
    AndroidDecoderCapabilities(ffmpegExtensionAvailable = true)

/**
 * What this Android device can decode, as reported by `MediaCodecList` plus the decoders ExoPlayer
 * ships itself.
 *
 * Detection is done once at construction: enumerating codecs costs tens of milliseconds and the
 * answer cannot change while the process is alive.
 *
 * @param ffmpegExtensionAvailable whether `org.jellyfin.media3:media3-ffmpeg-decoder` is on the
 * classpath. It software-decodes audio formats most Android devices have no hardware path for
 * (AC-3, DTS, TrueHD…). This **must** be false until that dependency is actually added: declaring
 * a codec we cannot decode makes the server direct-play a file that then plays silent.
 */
class AndroidDecoderCapabilities(
    private val ffmpegExtensionAvailable: Boolean = false,
) : DecoderCapabilities {
    override val videoCodecs: Map<String, Set<String>>
    override val audioCodecs: Set<String>

    /**
     * ExoPlayer renders these itself. ASS/SSA is deliberately absent — its rendering is poor enough
     * that jellyfin-android puts it behind an opt-in preference and otherwise lets the server burn
     * subtitles in. We follow that default.
     */
    override val embeddedSubtitleFormats = setOf("dvbsub", "pgssub", "srt", "subrip", "ttml")

    override val externalSubtitleFormats = setOf("srt", "subrip", "ttml", "vtt", "webvtt")

    init {
        val video = mutableMapOf<String, MutableSet<String>>()
        val audio = mutableSetOf<String>()

        for (info in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (info.isEncoder) continue

            for (mimeType in info.supportedTypes) {
                videoCodecName(mimeType)?.let { codec ->
                    val profiles = video.getOrPut(codec) { mutableSetOf() }
                    // A device usually advertises the same codec across several decoders; union the
                    // profiles rather than letting the last one win.
                    runCatching { info.getCapabilitiesForType(mimeType).profileLevels }
                        .getOrNull()
                        .orEmpty()
                        .forEach { level -> videoProfileName(codec, level.profile)?.let(profiles::add) }
                }
                audioCodecName(mimeType)?.let(audio::add)
            }
        }

        videoCodecs = video
        audioCodecs = audio + FORCED_AUDIO_CODECS +
            if (ffmpegExtensionAvailable) FFMPEG_EXTENSION_AUDIO_CODECS else emptySet()
    }

    private companion object {
        /**
         * ExoPlayer decodes raw PCM regardless of what `MediaCodecList` advertises — `MediaCodecList`
         * reports one generic `audio/raw` entry that carries no information about which PCM variant,
         * so the mapping has to be asserted rather than detected.
         */
        val FORCED_AUDIO_CODECS = setOf(
            "pcm_s8",
            "pcm_s16be",
            "pcm_s16le",
            "pcm_s24le",
            "pcm_s32le",
            "pcm_f32le",
            "pcm_alaw",
            "pcm_mulaw",
        )

        /** Only decodable with the Jellyfin FFmpeg decoder extension bundled. */
        val FFMPEG_EXTENSION_AUDIO_CODECS = setOf("alac", "ac3", "eac3", "dts", "mlp", "truehd")

        fun videoCodecName(mimeType: String): String? = when (mimeType) {
            MediaFormat.MIMETYPE_VIDEO_MPEG2 -> "mpeg2video"
            MediaFormat.MIMETYPE_VIDEO_H263 -> "h263"
            MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "mpeg4"
            MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
            // Dolby Vision streams are HEVC underneath, so a DV decoder implies HEVC support.
            MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "hevc"
            MediaFormat.MIMETYPE_VIDEO_VP8 -> "vp8"
            MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
            MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
            else -> null
        }

        fun audioCodecName(mimeType: String): String? = when (mimeType) {
            MediaFormat.MIMETYPE_AUDIO_AAC -> "aac"
            MediaFormat.MIMETYPE_AUDIO_AC3 -> "ac3"
            MediaFormat.MIMETYPE_AUDIO_EAC3 -> "eac3"
            MediaFormat.MIMETYPE_AUDIO_AMR_WB, MediaFormat.MIMETYPE_AUDIO_AMR_NB -> "3gpp"
            MediaFormat.MIMETYPE_AUDIO_FLAC -> "flac"
            MediaFormat.MIMETYPE_AUDIO_MPEG -> "mp3"
            MediaFormat.MIMETYPE_AUDIO_OPUS -> "opus"
            MediaFormat.MIMETYPE_AUDIO_VORBIS -> "vorbis"
            else -> null
        }

        /**
         * Profile names must match what Jellyfin's server expects to compare against, which is
         * FFmpeg's spelling — not Android's constant names.
         */
        fun videoProfileName(codec: String, profile: Int): String? = when (codec) {
            "mpeg2video" -> when (profile) {
                CodecProfileLevel.MPEG2ProfileSimple -> "simple profile"
                CodecProfileLevel.MPEG2ProfileMain -> "main profile"
                CodecProfileLevel.MPEG2Profile422 -> "422 profile"
                CodecProfileLevel.MPEG2ProfileSNR -> "snr profile"
                CodecProfileLevel.MPEG2ProfileSpatial -> "spatial profile"
                CodecProfileLevel.MPEG2ProfileHigh -> "high profile"
                else -> null
            }

            "h263" -> when (profile) {
                CodecProfileLevel.H263ProfileBaseline -> "baseline"
                CodecProfileLevel.H263ProfileH320Coding -> "h320 coding"
                CodecProfileLevel.H263ProfileBackwardCompatible -> "backward compatible"
                CodecProfileLevel.H263ProfileISWV2 -> "isw v2"
                CodecProfileLevel.H263ProfileISWV3 -> "isw v3"
                CodecProfileLevel.H263ProfileHighCompression -> "high compression"
                CodecProfileLevel.H263ProfileInternet -> "internet"
                CodecProfileLevel.H263ProfileInterlace -> "interlace"
                CodecProfileLevel.H263ProfileHighLatency -> "high latency"
                else -> null
            }

            "mpeg4" -> when (profile) {
                CodecProfileLevel.MPEG4ProfileAdvancedCoding -> "advanced coding profile"
                CodecProfileLevel.MPEG4ProfileAdvancedCore -> "advanced core profile"
                CodecProfileLevel.MPEG4ProfileAdvancedRealTime -> "advanced realtime profile"
                CodecProfileLevel.MPEG4ProfileAdvancedSimple -> "advanced simple profile"
                CodecProfileLevel.MPEG4ProfileBasicAnimated -> "basic animated profile"
                CodecProfileLevel.MPEG4ProfileCore -> "core profile"
                CodecProfileLevel.MPEG4ProfileCoreScalable -> "core scalable profile"
                CodecProfileLevel.MPEG4ProfileHybrid -> "hybrid profile"
                CodecProfileLevel.MPEG4ProfileNbit -> "nbit profile"
                CodecProfileLevel.MPEG4ProfileScalableTexture -> "scalable texture profile"
                CodecProfileLevel.MPEG4ProfileSimple -> "simple profile"
                CodecProfileLevel.MPEG4ProfileSimpleFBA -> "simple fba profile"
                CodecProfileLevel.MPEG4ProfileSimpleFace -> "simple face profile"
                CodecProfileLevel.MPEG4ProfileSimpleScalable -> "simple scalable profile"
                CodecProfileLevel.MPEG4ProfileMain -> "main profile"
                else -> null
            }

            "h264" -> when (profile) {
                CodecProfileLevel.AVCProfileBaseline -> "baseline"
                CodecProfileLevel.AVCProfileMain -> "main"
                CodecProfileLevel.AVCProfileExtended -> "extended"
                CodecProfileLevel.AVCProfileHigh -> "high"
                CodecProfileLevel.AVCProfileHigh10 -> "high 10"
                CodecProfileLevel.AVCProfileHigh422 -> "high 422"
                CodecProfileLevel.AVCProfileHigh444 -> "high 444"
                CodecProfileLevel.AVCProfileConstrainedBaseline -> "constrained baseline"
                CodecProfileLevel.AVCProfileConstrainedHigh -> "constrained high"
                else -> null
            }

            "hevc" -> when (profile) {
                CodecProfileLevel.HEVCProfileMain -> "Main"
                CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
                CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR 10"
                CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR 10 Plus"
                CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
                else -> null
            }

            "vp8" -> when (profile) {
                CodecProfileLevel.VP8ProfileMain -> "main"
                else -> null
            }

            "vp9" -> when (profile) {
                CodecProfileLevel.VP9Profile0 -> "Profile 0"
                CodecProfileLevel.VP9Profile1 -> "Profile 1"
                CodecProfileLevel.VP9Profile2, CodecProfileLevel.VP9Profile2HDR -> "Profile 2"
                CodecProfileLevel.VP9Profile3, CodecProfileLevel.VP9Profile3HDR -> "Profile 3"
                else -> null
            }

            else -> null
        }
    }
}
