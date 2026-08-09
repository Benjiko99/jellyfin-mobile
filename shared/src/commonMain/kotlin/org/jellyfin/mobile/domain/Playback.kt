package org.jellyfin.mobile.domain

/**
 * How the server decided to deliver an item, in descending order of preference.
 *
 * Deliberately not on the player's face: someone watching a film has no use for it and cannot act
 * on it. It lives in the debug overlay, where a user investigating why their server's fans are
 * loud can find [Transcode] and know their client asked for expensive work.
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
    val stream: StreamInfo = StreamInfo(),
    /** The cap this negotiation asked for, in bits per second. Null means no cap of ours. */
    val maxStreamingBitrate: Int? = null,
) {
    val selectedAudio: MediaTrack? get() = audioTracks.firstOrNull { it.index == selectedAudioIndex }
    val selectedSubtitle: MediaTrack? get() = subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }
}

/**
 * An episode either side of the one playing, and what the player needs to start it.
 *
 * The name parts are carried rather than a finished header because the header is a `UiText` written
 * from them, in the one place that writes it — `playerHeader` — and the player has to be able to
 * rewrite it as it moves through a series.
 */
data class AdjacentEpisode(
    val id: String,
    val title: String,
    /** The show, which is what an episode's header leads with. */
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /**
     * This episode's own resume point. Skipping into one that was left half-watched lands where it
     * was left, the same rule the detail screen's Play button follows — usually zero, because the
     * episode after the one playing has usually never been started.
     */
    val startPositionTicks: Long,
)

/**
 * What the player can skip to from where it is. Both are null for a film, and one is null at either
 * end of a series.
 */
data class EpisodeNeighbours(
    val previous: AdjacentEpisode? = null,
    val next: AdjacentEpisode? = null,
)

/**
 * What the chosen media source is, before the server does anything to it.
 *
 * Two readers, both of which want the *source* rather than the delivered stream: the quality ladder
 * is filtered by the file's own resolution, and the debug overlay reports what was negotiated
 * against what exists. Every field is nullable — these come from the server's probe of the file,
 * and a source it could not fully read still plays.
 */
data class StreamInfo(
    val container: String? = null,
    val videoCodec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Bits per second of the source, as the server measured it. */
    val bitrate: Int? = null,
)

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
    /**
     * The server's own label, e.g. "English - Dolby Digital - 5.1 - Default", which is why it is
     * [UiText.Raw]. Only the fallback for a stream that carries no label at all is ours to word.
     */
    val label: UiText,
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
