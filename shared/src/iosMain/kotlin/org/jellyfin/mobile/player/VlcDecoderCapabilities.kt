package org.jellyfin.mobile.player

actual fun platformDecoderCapabilities(): DecoderCapabilities = VlcDecoderCapabilities()

/**
 * What VLC can decode on iOS.
 *
 * Unlike Android, this is a static declaration rather than a device query. VLC bundles its own
 * FFmpeg-derived decoders and falls back to software for anything the hardware won't take, so the
 * answer does not vary by device — it varies by VLC build, and we control that.
 *
 * The entire point of choosing VLCKit over AVPlayer is here: MKV, H.265, AC-3/DTS/TrueHD and
 * ASS/SSA all direct-play, where AVPlayer would force the server to transcode. See PLAN.md §6.1.
 *
 * Video codec profiles are declared empty on purpose. VLC decodes every profile of the codecs it
 * supports, so enumerating them would only risk constraining playback to a stale list;
 * [buildDeviceProfile] reads an empty set as "unconstrained".
 *
 * NOTE: unverified on real hardware — no iOS build has ever run (see AGENTS.md on the Windows-host
 * limits). Revisit once the VLCKit engine is actually wired up and a device can be tested.
 */
class VlcDecoderCapabilities : DecoderCapabilities {
    override val videoCodecs: Map<String, Set<String>> = listOf(
        "mpeg1video",
        "mpeg2video",
        "h263",
        "mpeg4",
        "h264",
        "hevc",
        "av1",
        "vp8",
        "vp9",
    ).associateWith { emptySet<String>() }

    override val audioCodecs: Set<String> = setOf(
        "pcm_s8",
        "pcm_s16be",
        "pcm_s16le",
        "pcm_s24le",
        "pcm_s32le",
        "pcm_f32le",
        "pcm_alaw",
        "pcm_mulaw",
        "mp1",
        "mp2",
        "mp3",
        "aac",
        "vorbis",
        "opus",
        "flac",
        "alac",
        "ac3",
        "eac3",
        "dts",
        "mlp",
        "truehd",
        "3gpp",
    )

    /** VLC renders ASS/SSA properly, which is the other half of why it was chosen. */
    override val embeddedSubtitleFormats = setOf(
        "ass",
        "dvbsub",
        "pgssub",
        "srt",
        "ssa",
        "subrip",
        "ttml",
        "vtt",
        "webvtt",
    )

    override val externalSubtitleFormats = embeddedSubtitleFormats
}
