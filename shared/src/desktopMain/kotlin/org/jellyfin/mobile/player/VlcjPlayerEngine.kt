package org.jellyfin.mobile.player

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.ticksToMs
import org.jellyfin.mobile.network.StreamAuthorizer
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_error_failed
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat as VlcBufferFormat

/**
 * libVLC on the desktop, through VLCJ.
 *
 * The same library iOS is heading for, which is why [VlcDecoderCapabilities] is shared: MKV, H.265,
 * AC-3/DTS/TrueHD and ASS/SSA all direct-play, so the server is asked to transcode only for content
 * nothing could read. See PLAN.md §6.1.
 *
 * **The binaries are not on the classpath.** libVLC is a native library, packaged with the app where
 * we can package it ([useBundledVlc]) and otherwise found wherever the user installed VLC. Discovery
 * runs before anything else, so a machine with neither gets a sentence naming what to install rather
 * than an `UnsatisfiedLinkError` out of the constructor.
 *
 * **Frames come to us, rather than us handing VLC a window.** A `CallbackVideoSurface` has libVLC
 * decode into memory we then draw in Compose, where the alternative — an AWT `Canvas` in a
 * `SwingPanel` — is a heavyweight component that would draw *over* every shared control the player
 * puts on top of the picture. See [VideoSurface].
 *
 * Two libVLC rules shape the code below. Never call back into the player from an event callback,
 * which is what [MediaPlayer.submit] is for; and `controls().pause()` toggles, so intent is stated
 * with `setPause` instead.
 */
class VlcjPlayerEngine(private val authorizer: StreamAuthorizer) : PlayerEngine {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val frames = Frames()

    /**
     * The picture, one frame at a time. Compose state rather than a flow because its only reader is
     * [VideoSurface], which redraws when it changes and does nothing else with it.
     */
    internal val frame: State<ImageBitmap?> get() = frames.image

    private val factory: MediaPlayerFactory? = createFactory()

    private val player: EmbeddedMediaPlayer? = factory?.mediaPlayers()?.newEmbeddedMediaPlayer()?.apply {
        videoSurface().set(
            CallbackVideoSurface(
                frames,
                frames,
                // Locked buffers: libVLC holds the frame still while `display` copies it out, which
                // is the difference between a clean picture and one torn across the middle.
                true,
                VideoSurfaceAdapters.getVideoSurfaceAdapter(),
            ),
        )
        events().addMediaPlayerEventListener(Events())
    }

    /** What was asked for, kept because VLC can only be told about tracks once it has parsed them. */
    private var pending: PlaybackSource? = null
    private var pendingSeekMs = 0L

    private var status = PlayerStatus.Idle
    private var playWhenReady = false
    private var durationMs = 0L

    init {
        if (player == null) {
            status = PlayerStatus.Failed
            publish(UiText.Resource(missingLibVlc()))
        }
    }

    override fun load(source: PlaybackSource) {
        val player = player ?: return

        frames.clear()
        pending = source
        pendingSeekMs = source.startPositionTicks.ticksToMs()
        status = PlayerStatus.Buffering
        durationMs = 0
        publish()

        // Authorized in the URL rather than in a header: libVLC opens HTTP sources itself and has no
        // way to set one. See StreamAuthorizer.authorizedUrl.
        player.media().prepare(authorizer.authorizedUrl(source.url))

        // A subtitle the server delivers as a separate file is not in the stream, so it has to be
        // attached as a slave or it never appears. Selected here; embedded tracks wait for a parse.
        source.selectedSubtitle?.deliveryUrl?.let { url ->
            player.media().addSlave(MediaSlaveType.SUBTITLE, authorizer.authorizedUrl(url), true)
        }
    }

    override fun play() {
        playWhenReady = true
        publish()
        player?.controls()?.play()
    }

    override fun pause() {
        playWhenReady = false
        publish()
        player?.controls()?.setPause(true)
    }

    override fun seekTo(positionMs: Long) {
        // Before the media is parsed there is nothing to seek in, so the request is held and applied
        // with the resume point when the player reports itself ready.
        if (status == PlayerStatus.Idle || status == PlayerStatus.Buffering && durationMs == 0L) {
            pendingSeekMs = positionMs
            return
        }
        player?.controls()?.setTime(positionMs)
    }

    override fun positionMs(): Long = player?.status()?.time()?.coerceAtLeast(0) ?: 0

    override fun release() {
        frames.clear()
        player?.release()
        factory?.release()
    }

    private fun publish(error: UiText? = null) {
        _state.value = PlayerState(
            status = status,
            // Intent, not "are frames arriving" — see PlayerState.playWhenReady. VLC reports itself
            // paused while it rebuffers, which the UI would otherwise read as the user pausing.
            playWhenReady = playWhenReady,
            durationMs = durationMs,
            error = error,
        )
    }

    /**
     * Applies what the negotiation asked for, once libVLC knows what the media contains.
     *
     * Matched by language rather than by index: the server's stream indices and libVLC's track ids
     * are two different numberings of the same file, and nothing maps one to the other. A track that
     * cannot be matched is left as VLC chose, which is the same fallback the Android engine's
     * preferred-language selection has.
     */
    private fun applySelection(player: EmbeddedMediaPlayer, source: PlaybackSource) {
        source.selectedAudio?.language?.let { language ->
            player.media().info()?.audioTracks()
                ?.firstOrNull { it.language().equals(language, ignoreCase = true) }
                ?.let { player.audio().setTrack(it.id()) }
        }

        val subtitle = source.selectedSubtitle
        when {
            // No subtitle chosen. Said explicitly, because VLC turns on a default track of its own.
            subtitle == null -> player.subpictures().setTrack(SUBTITLE_TRACK_DISABLED)
            // A sidecar file, already attached and selected as a slave in `load`.
            subtitle.deliveryUrl != null -> Unit
            else -> player.media().info()?.textTracks()
                ?.firstOrNull { it.language().equals(subtitle.language, ignoreCase = true) }
                ?.let { player.subpictures().setTrack(it.id()) }
        }
    }

    private inner class Events : MediaPlayerEventAdapter() {
        override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
            val player = player ?: return
            val source = pending
            val seekMs = pendingSeekMs
            pendingSeekMs = 0

            // Off the event thread: calling into libVLC from one of its own callbacks deadlocks.
            mediaPlayer.submit {
                source?.let { applySelection(player, it) }
                if (seekMs > 0) player.controls().setTime(seekMs)
            }
        }

        override fun opening(mediaPlayer: MediaPlayer) {
            status = PlayerStatus.Buffering
            publish()
        }

        /**
         * Fires repeatedly while VLC fills its cache, from 0 to 100. Only the last one means
         * anything to us — everything before it is the stall the UI is already showing.
         */
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            status = if (newCache < FULLY_BUFFERED) PlayerStatus.Buffering else PlayerStatus.Ready
            publish()
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            status = PlayerStatus.Ready
            playWhenReady = true
            publish()
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            status = PlayerStatus.Ready
            publish()
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            status = PlayerStatus.Ended
            playWhenReady = false
            publish()
        }

        override fun error(mediaPlayer: MediaPlayer) {
            status = PlayerStatus.Failed
            playWhenReady = false
            // libVLC does not hand out a reason — `libvlc_errmsg` is empty by the time this fires —
            // so the shared wording for a failed playback is the most honest thing to show. What
            // went wrong is in VLC's own log, which is why the factory is not run with `--quiet`.
            publish(UiText.Resource(Res.string.player_error_failed))
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            durationMs = newLength.coerceAtLeast(0)
            publish()
        }
    }

    /**
     * The frame pipeline: libVLC decodes into a native buffer, this copies it out and hands Compose
     * an image.
     *
     * One copy per frame, into an array reused for the life of the item. A second copy happens
     * inside [Image.makeRaster]; both are the price of not handing VLC a native window, and at 1080p
     * they are a few milliseconds of memcpy against a frame budget of forty.
     */
    private inner class Frames : BufferFormatCallback, RenderCallback {
        val image = mutableStateOf<ImageBitmap?>(null)

        /**
         * The buffer and what shape it is, replaced as one value rather than as two fields — VLC
         * renders on its own thread, and a half-swapped pair is a frame read against the wrong size.
         */
        @Volatile
        private var format: Format? = null

        /**
         * The first size VLC asks for is the picture's own, and it is kept.
         *
         * Later calls are not to be trusted: for a 320x240 file this is asked a third time for
         * 320x258, which is VLC's vout aligning its own buffer rather than the video changing shape.
         * Answering that honestly hands back a picture stretched by seven percent, because the format
         * we return is the one VLC *scales into* — it is a request, not a description. Pinning the
         * first answer means anything that really does change resolution, like a step up an HLS
         * ladder, is scaled by VLC into the shape we already have, which is what we would do with it
         * anyway.
         *
         * Reset by [clear], so the next item is measured afresh.
         */
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): VlcBufferFormat {
            val format = format ?: Format(sourceWidth, sourceHeight).also { format = it }
            return RV32BufferFormat(format.info.width, format.info.height)
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit

        /**
         * The picture resized — a new resolution mid-stream, which an adaptive HLS ladder does. VLC
         * calls [getBufferFormat] again for the new size, and that is where everything this would
         * change already happens.
         */
        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit

        /**
         * Ours to implement only if we manage the buffers ourselves. The surface is built with
         * locked buffers, so VLCJ holds the frame still across [display] and there is nothing left
         * for these two to guard.
         */
        override fun lock(mediaPlayer: MediaPlayer) = Unit

        override fun unlock(mediaPlayer: MediaPlayer) = Unit

        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<out ByteBuffer>,
            bufferFormat: VlcBufferFormat,
            displayWidth: Int,
            displayHeight: Int,
        ) {
            val format = format ?: return
            val buffer = nativeBuffers.firstOrNull() ?: return

            buffer.rewind()
            buffer.get(format.pixels)
            // Written from libVLC's own thread. Compose state is safe to set from anywhere, and the
            // recomposition it schedules is what puts the frame on screen.
            image.value = Image
                .makeRaster(format.info, format.pixels, format.info.minRowBytes)
                .toComposeImageBitmap()
        }

        /**
         * Drops the last picture and the shape it was in, so the next item opens on nothing rather
         * than on the end of the one before, and is measured for itself.
         */
        fun clear() {
            image.value = null
            format = null
        }
    }

    /** One picture's worth of buffer, alongside the shape Skia reads it back in. */
    private class Format(width: Int, height: Int) {
        /**
         * RV32 is VLC's 32-bit RGB, which on every little-endian machine is BGRA in memory — the
         * order Skia calls [ColorType.BGRA_8888]. Opaque rather than premultiplied because video has
         * no alpha, and saying so saves Skia a pass over every pixel.
         */
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        val pixels = ByteArray(width * height * BYTES_PER_PIXEL)
    }

    private companion object {
        /**
         * `--no-video-title-show` matters more than it looks: with a callback surface VLC draws its
         * filename overlay *into the frames we render*, so without this every play would open with
         * a stream URL burnt across the top of the picture.
         */
        val FACTORY_ARGUMENTS = arrayOf("--no-video-title-show")

        const val BYTES_PER_PIXEL = 4
        const val FULLY_BUFFERED = 100f
        const val SUBTITLE_TRACK_DISABLED = -1

        /**
         * Null when there is no libVLC to bind to, which is the normal state of a machine without
         * VLC installed rather than a fault. `discover()` looks in the places each platform installs
         * it and gives up quietly; the constructor below is what would otherwise throw an
         * `UnsatisfiedLinkError`, which is an `Error` and so needs `runCatching` rather than a catch.
         */
        fun createFactory(): MediaPlayerFactory? = runCatching {
            useBundledVlc()
            if (!NativeDiscovery().discover()) return null
            MediaPlayerFactory(*FACTORY_ARGUMENTS)
        }.getOrNull()
    }
}
