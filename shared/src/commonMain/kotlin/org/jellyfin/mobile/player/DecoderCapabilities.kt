package org.jellyfin.mobile.player

/**
 * What the platform's decoders and the chosen playback engine can actually handle.
 *
 * This is the only part of device profiling that is platform-specific. Android answers it by walking
 * `MediaCodecList` and adding the decoders ExoPlayer bundles; iOS answers it from VLC's static
 * capability set. Everything else — which codecs a container may legally hold, how that becomes a
 * `DeviceProfile` — is shared, because it is a property of the file formats rather than the device.
 *
 * Codec names are Jellyfin's (FFmpeg's), not the platform's MIME types; platform implementations
 * translate.
 */
interface DecoderCapabilities {
    /**
     * Decodable video codecs, each mapped to the codec profiles the decoder advertises
     * (e.g. `"h264" to setOf("high", "main", "baseline")`).
     *
     * An empty profile set means "this codec decodes, but we can't enumerate which profiles" — the
     * profile is then declared without a profile condition rather than being constrained to nothing.
     */
    val videoCodecs: Map<String, Set<String>>

    /** Decodable audio codecs, in Jellyfin's naming (`ac3`, `eac3`, `truehd`, `pcm_s16le`, …). */
    val audioCodecs: Set<String>

    /** Subtitle formats renderable when muxed into the container the server delivers. */
    val embeddedSubtitleFormats: Set<String>

    /** Subtitle formats renderable when fetched separately as a sidecar file. */
    val externalSubtitleFormats: Set<String>
}

/**
 * The capabilities of the engine this platform plays with.
 *
 * Constructing this enumerates the device's decoders on Android, so call it lazily — not during
 * startup, and not per item.
 */
expect fun platformDecoderCapabilities(): DecoderCapabilities
