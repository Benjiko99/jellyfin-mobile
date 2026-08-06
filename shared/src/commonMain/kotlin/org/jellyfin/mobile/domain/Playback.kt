package org.jellyfin.mobile.domain

/**
 * How the server decided to deliver an item, in descending order of preference.
 *
 * Worth surfacing in the UI eventually: a user seeing constant [Transcode] is being told their
 * server is doing expensive work their client could have avoided.
 */
enum class PlayMethod {
    /** The original file, byte for byte. Cheapest for the server, best quality. */
    DirectPlay,

    /** Original streams, new container. Cheap — no re-encoding, only remuxing. */
    DirectStream,

    /** Re-encoded. Expensive for the server and lossy, but always works. */
    Transcode,
}

/**
 * Everything needed to start playing one item, after negotiation.
 *
 * [url] is unauthenticated on purpose. The playback engine fetches it, not our HTTP client, so the
 * engine attaches the `Authorization` header — and must only do so when the host matches the server,
 * or the access token leaks to whatever CDN a remote stream redirects to.
 */
data class PlaybackSource(
    val itemId: String,
    val mediaSourceId: String,
    /** Ties the stream and every progress report to one playback attempt on the server. */
    val playSessionId: String,
    val playMethod: PlayMethod,
    val url: String,
    /** True when [url] is an HLS manifest rather than a progressive stream. */
    val isHls: Boolean,
    val startPositionTicks: Long,
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<MediaTrack> = emptyList(),
    /** Server stream index of the active audio track, or null if the server chose for us. */
    val selectedAudioIndex: Int? = null,
    /** Server stream index of the active subtitle, or null for none. */
    val selectedSubtitleIndex: Int? = null,
) {
    val selectedAudio: MediaTrack? get() = audioTracks.firstOrNull { it.index == selectedAudioIndex }
    val selectedSubtitle: MediaTrack? get() = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }
}

/**
 * One selectable audio or subtitle stream.
 *
 * [index] is the server's stream index within the media source — the value `PlaybackInfo` expects
 * back when asking for a different track, and the only stable way to identify one. It is not a
 * position in [PlaybackSource.audioTracks] or [PlaybackSource.subtitleTracks], which are filtered.
 *
 * There is no `kind` field: which list a track is in already says whether it is audio or subtitle.
 */
data class MediaTrack(
    val index: Int,
    /** The server's own label, e.g. "English - Dolby Digital - 5.1 - Default". */
    val label: String,
    val language: String?,
    val codec: String?,
    /**
     * Set on subtitles the server delivers as a separate file rather than muxed into the stream.
     * The player has to fetch and render these itself.
     */
    val deliveryUrl: String?,
)

/** Jellyfin measures time in 100-nanosecond ticks. */
const val TICKS_PER_MILLISECOND = 10_000L

fun Long.ticksToMs(): Long = this / TICKS_PER_MILLISECOND

fun Long.msToTicks(): Long = this * TICKS_PER_MILLISECOND
