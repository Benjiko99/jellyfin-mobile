package org.jellyfin.mobile.player

/**
 * What libVLC can decode, on any platform it runs on.
 *
 * In `commonMain` because two targets play through the same library — VLCJ on desktop and VLCKit on
 * iOS are both libVLC — and a device profile that differed between them would be a bug rather than a
 * difference. Android is the exception and stays a device query: it plays through the platform's own
 * decoders, so what it can read depends on the handset.
 *
 * That is the point of this being a static declaration. VLC bundles its own FFmpeg-derived decoders
 * and falls back to software for anything the hardware will not take, so the answer does not vary by
 * device — it varies by VLC build, and we control that.
 *
 * The entire reason for choosing libVLC over AVPlayer on iOS is here: MKV, H.265, AC-3/DTS/TrueHD
 * and ASS/SSA all direct-play, where AVPlayer would force the server to transcode. See PLAN.md §6.1.
 *
 * Video codec profiles are declared empty on purpose. VLC decodes every profile of the codecs it
 * supports, so enumerating them would only risk constraining playback to a stale list;
 * [buildDeviceProfile] reads an empty set as "unconstrained".
 *
 * NOTE: verified on desktop against libVLC 3. The iOS engine has never run — see AGENTS.md on the
 * Windows-host limits — so revisit once a device can be tested.
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

    override val audioCodecs: Set<String> = PCM_CODECS + setOf(
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
