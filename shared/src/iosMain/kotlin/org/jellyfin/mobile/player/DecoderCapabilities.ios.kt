package org.jellyfin.mobile.player

/** VLCKit is libVLC, so the answer is the shared one — see [VlcDecoderCapabilities]. */
actual fun platformDecoderCapabilities(): DecoderCapabilities = VlcDecoderCapabilities()
