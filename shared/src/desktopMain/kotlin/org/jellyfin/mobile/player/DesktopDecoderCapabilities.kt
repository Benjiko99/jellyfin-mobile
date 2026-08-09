package org.jellyfin.mobile.player

actual fun platformDecoderCapabilities(): DecoderCapabilities = DesktopDecoderCapabilities()

/**
 * Nothing, because there is no desktop engine yet — see [DesktopPlayerEngine].
 *
 * Deliberately empty rather than optimistic. This object is the honest answer to "what can this
 * client decode", and the server acts on it: declaring codecs no engine will read produces a direct
 * play into a surface that cannot show it, which is a black screen instead of a message. Every list
 * being empty means [buildDeviceProfile] offers no direct play profile at all, and playback fails
 * where it is negotiated rather than after the file is open.
 *
 * The engine decides what replaces this. If desktop follows iOS to libVLC — VLCJ is LGPL-2.1 and so
 * compatible with our GPL-2.0 — then `VlcDecoderCapabilities` in `iosMain` is already the answer and
 * should move to a source set both can see rather than being copied. That is a decision to take with
 * the engine, not ahead of it.
 */
class DesktopDecoderCapabilities : DecoderCapabilities {
    override val videoCodecs: Map<String, Set<String>> = emptyMap()

    override val audioCodecs: Set<String> = emptySet()

    override val embeddedSubtitleFormats: Set<String> = emptySet()

    override val externalSubtitleFormats: Set<String> = emptySet()
}
