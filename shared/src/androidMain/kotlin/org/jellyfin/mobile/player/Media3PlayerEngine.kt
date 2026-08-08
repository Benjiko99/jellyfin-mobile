// Derived from jellyfin-android, GPL-2.0
// https://github.com/jellyfin/jellyfin-android/blob/master/app/src/main/java/org/jellyfin/mobile/app/AppModule.kt

package org.jellyfin.mobile.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.domain.ticksToMs
import org.jellyfin.mobile.network.StreamAuthorizer

/**
 * ExoPlayer, with the Jellyfin FFmpeg audio extension and authenticated data sources.
 *
 * @param authorizer decides whether the access token may be attached to a given stream URL.
 */
@OptIn(UnstableApi::class)
class Media3PlayerEngine(
    context: Context,
    authorizer: StreamAuthorizer,
) : PlayerEngine {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(
            // PREFER, not ON: the FFmpeg extension decodes formats the device has no hardware path
            // for, and we would rather use it than let ExoPlayer fail over to nothing. Hardware
            // decoders are still chosen for video.
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER),
        )
        .setMediaSourceFactory(DefaultMediaSourceFactory(authenticatedDataSourceFactory(context, authorizer)))
        .build()
        .apply { addListener(StateListener()) }

    override fun load(source: PlaybackSource) {
        val subtitle = source.selectedSubtitle

        val item = MediaItem.Builder()
            .setMediaId(source.itemId)
            .setUri(source.url)
            // Transcodes and live sources are HLS manifests whose URLs often carry no recognisable
            // extension, so the type has to be declared or ExoPlayer guesses wrong and fails.
            .apply { if (source.isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            // A subtitle the server delivers as a sidecar file is not in the stream at all, so it
            // has to be attached as a separate source or it simply never appears.
            .setSubtitleConfigurations(listOfNotNull(subtitle?.toSubtitleConfiguration()))
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .apply {
                source.selectedAudio?.language?.let { setPreferredAudioLanguage(it) }
                // Selecting by language rather than index: ExoPlayer's track order is its own, and
                // does not have to match the server's stream indices.
                when {
                    subtitle == null -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    else -> {
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        subtitle.language?.let { setPreferredTextLanguage(it) }
                    }
                }
            }
            .build()

        player.setMediaItem(item)
        if (source.startPositionTicks > 0) {
            player.seekTo(source.startPositionTicks.ticksToMs())
        }
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun positionMs(): Long = player.currentPosition.coerceAtLeast(0)

    override fun release() {
        player.release()
    }

    /** Exposed so the Android [VideoSurface] can attach; not part of [PlayerEngine]. */
    internal val exoPlayer: ExoPlayer get() = player

    private inner class StateListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publish()

        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()

        /**
         * Intent changes without `isPlaying` following it whenever the player is not READY, so a
         * pause taken mid-rebuffer would otherwise never reach the UI.
         */
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publish()

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                status = PlayerStatus.Failed,
                playWhenReady = false,
                // ExoPlayer's own code name ("ERROR_CODE_IO_BAD_HTTP_STATUS"). Not ours to
                // translate, and the most useful thing we have to say about an arbitrary failure.
                error = UiText.Raw(error.errorCodeName),
            )
        }

        private fun publish() {
            _state.value = PlayerState(
                status = when (player.playbackState) {
                    Player.STATE_IDLE -> PlayerStatus.Idle
                    Player.STATE_BUFFERING -> PlayerStatus.Buffering
                    Player.STATE_READY -> PlayerStatus.Ready
                    Player.STATE_ENDED -> PlayerStatus.Ended
                    else -> PlayerStatus.Idle
                },
                // playWhenReady, not isPlaying: the latter is false throughout a rebuffer, which
                // the UI would read as the user having paused. See PlayerState.playWhenReady.
                playWhenReady = player.playWhenReady,
                // Unknown until the manifest or container is parsed; report 0 rather than the
                // C.TIME_UNSET sentinel, which is a large negative number.
                durationMs = player.duration.takeIf { it > 0 } ?: 0,
            )
        }
    }
}

/**
 * Subtitle MIME types, in Jellyfin's codec naming.
 *
 * ExoPlayer needs the type declared for a sidecar file: the URL is a `/Videos/.../Stream.srt` route
 * with no extension it can sniff.
 */
private fun MediaTrack.toSubtitleConfiguration(): MediaItem.SubtitleConfiguration? {
    val url = deliveryUrl ?: return null
    val mimeType = when (codec?.lowercase()) {
        "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
        "ssa", "ass" -> MimeTypes.TEXT_SSA
        "ttml" -> MimeTypes.APPLICATION_TTML
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "idx", "sub" -> MimeTypes.APPLICATION_VOBSUB
        "pgs", "pgssub" -> MimeTypes.APPLICATION_PGS
        else -> return null
    }
    return MediaItem.SubtitleConfiguration.Builder(url.toUri())
        .setMimeType(mimeType)
        .setLanguage(language)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}

/**
 * A data source that adds the `Authorization` header, and only to the Jellyfin server.
 *
 * ExoPlayer follows redirects and plays third-party URLs, so the header is resolved per request
 * against the actual host rather than being set once as a default.
 */
@OptIn(UnstableApi::class)
private fun authenticatedDataSourceFactory(
    context: Context,
    authorizer: StreamAuthorizer,
) = ResolvingDataSource.Factory(
    DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()),
) { dataSpec ->
    val headers = authorizer.headersFor(dataSpec.uri.toString())
    if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
}
