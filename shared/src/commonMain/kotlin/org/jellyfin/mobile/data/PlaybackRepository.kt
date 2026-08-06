package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.PlayMethod
import org.jellyfin.mobile.domain.PlaybackSource
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.dto.DeviceProfile
import org.jellyfin.mobile.network.dto.MediaSourceInfo
import org.jellyfin.mobile.network.dto.PlaybackProgressInfo
import org.jellyfin.mobile.network.dto.PlaybackStopInfo
import org.jellyfin.mobile.player.DecoderCapabilities
import org.jellyfin.mobile.player.buildDeviceProfile

/** Raised when the server offers no rendition this client can play. */
class UnsupportedContentException(message: String) : Exception(message)

/**
 * Negotiates playback with the server and turns its answer into a URL the engine can open.
 *
 * The device profile is built once and reused: it describes the hardware, which does not change
 * while the app is running, and rebuilding it per item would re-enumerate every codec on the device.
 */
class PlaybackRepository(
    private val api: JellyfinApi,
    capabilities: DecoderCapabilities,
    profileName: String = "Jellyfin Mobile",
) {
    private val deviceProfile: DeviceProfile by lazy { buildDeviceProfile(profileName, capabilities) }

    suspend fun resolve(
        itemId: String,
        startPositionTicks: Long = 0,
        maxStreamingBitrate: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): PlaybackSource {
        val response = api.playbackInfo(
            itemId = itemId,
            deviceProfile = deviceProfile,
            maxStreamingBitrate = maxStreamingBitrate,
            startTimeTicks = startPositionTicks.takeIf { it > 0 },
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )

        response.errorCode?.let { throw UnsupportedContentException("Server refused playback: $it") }

        val playSessionId = response.playSessionId
            ?: throw UnsupportedContentException("Server returned no play session")

        // The server may return several renditions of the same item (different files, qualities or
        // languages). Prefer the one matching the requested item, then fall back to the first.
        val source = response.mediaSources.firstOrNull { it.id?.replace("-", "") == itemId.replace("-", "") }
            ?: response.mediaSources.firstOrNull()
            ?: throw UnsupportedContentException("Server returned no media sources")

        val mediaSourceId = source.id ?: itemId

        return buildSource(
            itemId = itemId,
            source = source,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            startPositionTicks = startPositionTicks,
            // What we asked for, falling back to what the server picked. The server only echoes its
            // own defaults, so without this fallback the menu would show nothing selected on the
            // first load even though a track is plainly playing.
            audioStreamIndex = audioStreamIndex ?: source.defaultAudioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex ?: source.defaultSubtitleStreamIndex,
        )
    }

    @Suppress("LongParameterList")
    private fun buildSource(
        itemId: String,
        source: MediaSourceInfo,
        mediaSourceId: String,
        playSessionId: String,
        startPositionTicks: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
    ): PlaybackSource {
        val (method, url, isHls) = when {
            // The flags are not mutually exclusive, so order is the preference.
            source.supportsDirectPlay -> directPlay(itemId, source, mediaSourceId, playSessionId)

            source.supportsDirectStream -> {
                val container = source.container
                    ?: throw UnsupportedContentException("Direct stream offered without a container")
                Triple(
                    PlayMethod.DirectStream,
                    api.directStreamUrl(itemId, container, playSessionId, mediaSourceId),
                    false,
                )
            }

            source.supportsTranscoding -> {
                val path = source.transcodingUrl
                    ?: throw UnsupportedContentException("Transcode offered without a URL")
                // Anything other than HLS here means the server built a stream shape we have no
                // reader for; failing loudly beats handing the engine a URL it will stall on.
                if (!source.transcodingSubProtocol.equals("hls", ignoreCase = true)) {
                    throw UnsupportedContentException(
                        "Unsupported transcode protocol '${source.transcodingSubProtocol}'",
                    )
                }
                Triple(PlayMethod.Transcode, api.absoluteUrl(path), true)
            }

            else -> throw UnsupportedContentException("No playable rendition for $itemId")
        }

        return PlaybackSource(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            playMethod = method,
            url = url,
            isHls = isHls,
            container = source.container,
            runTimeTicks = source.runTimeTicks,
            startPositionTicks = startPositionTicks,
            audioTracks = source.mediaStreams.audioTracks(),
            subtitleTracks = source.mediaStreams.subtitleTracks(api::absoluteUrl),
            selectedAudioIndex = audioStreamIndex,
            selectedSubtitleIndex = subtitleStreamIndex,
        )
    }

    suspend fun reportStart(source: PlaybackSource, positionMs: Long) =
        api.reportPlaybackStart(source.progressInfo(positionMs, isPaused = false))

    suspend fun reportProgress(source: PlaybackSource, positionMs: Long, isPaused: Boolean) =
        api.reportPlaybackProgress(source.progressInfo(positionMs, isPaused))

    suspend fun reportStopped(source: PlaybackSource, positionMs: Long) = api.reportPlaybackStopped(
        PlaybackStopInfo(
            itemId = source.itemId,
            playSessionId = source.playSessionId,
            mediaSourceId = source.mediaSourceId,
            positionTicks = positionMs * TICKS_PER_MILLISECOND,
        ),
    )

    private fun PlaybackSource.progressInfo(positionMs: Long, isPaused: Boolean) = PlaybackProgressInfo(
        itemId = itemId,
        playSessionId = playSessionId,
        mediaSourceId = mediaSourceId,
        positionTicks = positionMs * TICKS_PER_MILLISECOND,
        isPaused = isPaused,
        playMethod = playMethod.name,
    )

    /**
     * Direct play splits on protocol: a `File` source lives on the server's disk and is fetched
     * through the stream route, while an `Http` source (live TV, a remote share) is already a URL
     * — and an HLS one, so the engine needs to be told.
     */
    private fun directPlay(
        itemId: String,
        source: MediaSourceInfo,
        mediaSourceId: String,
        playSessionId: String,
    ): Triple<PlayMethod, String, Boolean> = when {
        source.protocol.equals("http", ignoreCase = true) -> {
            val path = source.path
                ?: throw UnsupportedContentException("HTTP source without a path")
            Triple(PlayMethod.DirectPlay, path, true)
        }

        else -> Triple(
            PlayMethod.DirectPlay,
            api.directPlayUrl(itemId, playSessionId, mediaSourceId),
            false,
        )
    }

    private companion object {
        const val TICKS_PER_MILLISECOND = 10_000L
    }
}
