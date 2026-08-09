package org.jellyfin.mobile.player

/**
 * VLCJ is libVLC, so the answer is the shared one — see [VlcDecoderCapabilities].
 *
 * This is the same declaration iOS makes, which is the point: the two targets play through the same
 * library and would be lying if they told the server different things about it.
 *
 * It does assume libVLC is actually there. When it is not, `VlcjPlayerEngine` fails with a message
 * naming what is missing — which is a better outcome than a profile that promises nothing, since
 * that fails during negotiation with "no playable rendition" and never mentions VLC.
 */
actual fun platformDecoderCapabilities(): DecoderCapabilities = VlcDecoderCapabilities()
