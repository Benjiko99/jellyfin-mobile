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
 * The list the player's next and previous move through.
 *
 * A playlist is an order somebody arranged deliberately, so it wins over the series an episode
 * happens to belong to: someone who started an episode from a playlist means the playlist. Nothing
 * else is a queue — a film opened from its own page has nothing after it.
 */
sealed interface PlaybackQueue {
    /** Every episode of a show, in air order, across its season boundaries. */
    data class Series(val seriesId: String) : PlaybackQueue

    data class Playlist(val playlistId: String) : PlaybackQueue
}

/**
 * An item either side of the one playing, and what the player needs to start it.
 *
 * The name parts are carried rather than a finished header because the header is a `UiText` written
 * from them, in the one place that writes it — `playerHeader` — and the player has to be able to
 * rewrite it as it moves along a queue. A playlist can hold films as readily as episodes, which is
 * why both an episode's numbers and a film's [year] are here and why all of them are nullable.
 */
data class AdjacentItem(
    val id: String,
    val title: String,
    /** The show, which is what an episode's header leads with. Null on anything that is not one. */
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** What dates a film's header, and what separates it from the remake it shares a title with. */
    val year: Int? = null,
    /**
     * This item's own resume point. Skipping into something left half-watched lands where it was
     * left, the same rule the detail screen's Play button follows — usually zero, because whatever
     * comes after what is playing has usually never been started.
     */
    val startPositionTicks: Long = 0,
)

/**
 * What the player can skip to from where it is. Both are null outside a queue, and one is null at
 * either end of one.
 */
data class Neighbours(
    val previous: AdjacentItem? = null,
    val next: AdjacentItem? = null,
)

/**
 * One entry of a playlist: how the row draws it, and what starting it needs.
 *
 * Two models rather than one because they answer different questions and neither contains the
 * other. [MediaItem] is what every list in this app renders, and it has already folded an episode
 * into a title and a subtitle — "Northern Line", then "S2:E4 · The Undertow" — which is the right
 * thing to read and impossible to take apart again. [playback] is those pieces still separate,
 * alongside this entry's own resume point, which is what a route into the player carries and what
 * a card has no reason to know.
 */
data class PlaylistEntry(
    val item: MediaItem,
    val playback: AdjacentItem,
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
