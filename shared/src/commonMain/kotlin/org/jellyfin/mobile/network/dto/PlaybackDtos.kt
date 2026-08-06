package org.jellyfin.mobile.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Playback negotiation models — see `api-spec/jellyfin-openapi-12.0.0.json`.
 *
 * These split by direction of travel, and the two halves follow different conventions on purpose:
 *
 * - The profile types we **send** use real enums. We choose every value, so a typo should be a
 *   compile error rather than a silently mis-negotiated stream.
 * - [MediaSourceInfo] and [MediaStream], which we **receive**, keep the `String` convention
 *   [BaseItemDto] uses: the server gains enum members between releases, and failing to decode a
 *   whole playback response over one unrecognised value is far worse than carrying a string.
 *
 * NOTE: `JsonNamingStrategy` applies to property names only, never to enum entries, so every entry
 * below carries an explicit [SerialName]. `MediaStreamProtocol` in particular is lowercase on the
 * wire and would not round-trip without it.
 */
@Serializable
enum class DlnaProfileType {
    @SerialName("Audio")
    Audio,

    @SerialName("Video")
    Video,

    @SerialName("Photo")
    Photo,

    @SerialName("Subtitle")
    Subtitle,

    @SerialName("Lyric")
    Lyric,
}

@Serializable
enum class MediaStreamProtocol {
    @SerialName("http")
    Http,

    @SerialName("hls")
    Hls,
}

@Serializable
enum class SubtitleDeliveryMethod {
    /** Burned into the video by the server. The fallback when nothing else can render the format. */
    @SerialName("Encode")
    Encode,

    /** Muxed into the delivered container; the player decodes it as a track. */
    @SerialName("Embed")
    Embed,

    /** Fetched separately by the client as a sidecar file. */
    @SerialName("External")
    External,

    @SerialName("Hls")
    Hls,

    @SerialName("Drop")
    Drop,
}

@Serializable
enum class CodecType {
    @SerialName("Video")
    Video,

    @SerialName("VideoAudio")
    VideoAudio,

    @SerialName("Audio")
    Audio,
}

@Serializable
enum class ProfileConditionType {
    @SerialName("Equals")
    Equals,

    @SerialName("NotEquals")
    NotEquals,

    @SerialName("LessThanEqual")
    LessThanEqual,

    @SerialName("GreaterThanEqual")
    GreaterThanEqual,

    @SerialName("EqualsAny")
    EqualsAny,
}

/**
 * The subset of `ProfileConditionValue` we currently express conditions over. The server defines
 * roughly twenty more; add them here as the profile grows rather than switching to raw strings.
 */
@Serializable
enum class ProfileConditionValue {
    @SerialName("VideoProfile")
    VideoProfile,

    @SerialName("VideoLevel")
    VideoLevel,

    @SerialName("VideoBitDepth")
    VideoBitDepth,

    @SerialName("Width")
    Width,

    @SerialName("Height")
    Height,

    @SerialName("RefFrames")
    RefFrames,

    @SerialName("AudioChannels")
    AudioChannels,

    @SerialName("VideoFramerate")
    VideoFramerate,

    @SerialName("IsAnamorphic")
    IsAnamorphic,

    @SerialName("IsInterlaced")
    IsInterlaced,

    @SerialName("VideoRangeType")
    VideoRangeType,
}

/**
 * What this client can play, as the server understands it.
 *
 * The server compares a media file against this document to decide direct play (send the file
 * untouched), direct stream (remux the container, copy the streams) or transcode (re-encode). It is
 * the single biggest determinant of playback quality and of load on the user's server, which is why
 * it is assembled from real device capabilities rather than hardcoded.
 */
@Serializable
data class DeviceProfile(
    val name: String,
    val id: String? = null,
    val maxStreamingBitrate: Int? = null,
    val maxStaticBitrate: Int? = null,
    val musicStreamingTranscodingBitrate: Int? = null,
    val directPlayProfiles: List<DirectPlayProfile> = emptyList(),
    val transcodingProfiles: List<TranscodingProfile> = emptyList(),
    val containerProfiles: List<ContainerProfile> = emptyList(),
    val codecProfiles: List<CodecProfile> = emptyList(),
    val subtitleProfiles: List<SubtitleProfile> = emptyList(),
)

/** A container/codec combination playable as-is. [videoCodec] and [audioCodec] are comma-separated. */
@Serializable
data class DirectPlayProfile(
    val type: DlnaProfileType,
    val container: String,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
)

/** A container/codec combination the server may re-encode into when direct play is impossible. */
@Serializable
data class TranscodingProfile(
    val type: DlnaProfileType,
    val container: String,
    val videoCodec: String,
    val audioCodec: String,
    val protocol: MediaStreamProtocol,
    val conditions: List<ProfileCondition> = emptyList(),
)

@Serializable
data class ContainerProfile(
    val type: DlnaProfileType,
    val container: String,
    val conditions: List<ProfileCondition> = emptyList(),
)

/** Constrains a codec beyond mere presence — e.g. "h264, but only these profiles". */
@Serializable
data class CodecProfile(
    val type: CodecType,
    val codec: String,
    val container: String,
    val conditions: List<ProfileCondition> = emptyList(),
    val applyConditions: List<ProfileCondition> = emptyList(),
)

@Serializable
data class SubtitleProfile(
    val format: String,
    val method: SubtitleDeliveryMethod,
)

@Serializable
data class ProfileCondition(
    val condition: ProfileConditionType,
    val property: ProfileConditionValue,
    val value: String,
    val isRequired: Boolean = false,
)

/** Request body for `POST /Items/{itemId}/PlaybackInfo`. */
@Serializable
data class PlaybackInfoDto(
    val userId: String? = null,
    val deviceProfile: DeviceProfile? = null,
    val mediaSourceId: String? = null,
    val maxStreamingBitrate: Int? = null,
    val startTimeTicks: Long? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val maxAudioChannels: Int? = null,
    val liveStreamId: String? = null,
    val autoOpenLiveStream: Boolean? = null,
    val enableDirectPlay: Boolean? = null,
    val enableDirectStream: Boolean? = null,
    val enableTranscoding: Boolean? = null,
    val allowVideoStreamCopy: Boolean? = null,
    val allowAudioStreamCopy: Boolean? = null,
)

@Serializable
data class PlaybackInfoResponse(
    val mediaSources: List<MediaSourceInfo> = emptyList(),
    /**
     * Identifies this playback attempt. Every progress report and the stream URL itself must carry
     * it, or the server cannot tie them together — and will not clean up the transcode when we stop.
     */
    val playSessionId: String? = null,
    val errorCode: String? = null,
)

/**
 * One playable rendition of an item, after the server has applied our [DeviceProfile].
 *
 * The three `supports*` flags are the server's verdict, in descending order of preference. They are
 * not mutually exclusive, so read them in order.
 */
@Serializable
data class MediaSourceInfo(
    val id: String? = null,
    val name: String? = null,
    /** `MediaProtocol`: File, Http, Rtmp, Rtsp, Udp, Rtp, Ftp. */
    val protocol: String? = null,
    /**
     * For a `File` source this is a server-local filesystem path and is not fetchable by us. For an
     * `Http` source it is the URL to play.
     */
    val path: String? = null,
    val container: String? = null,
    val size: Long? = null,
    val bitrate: Int? = null,
    val runTimeTicks: Long? = null,
    val isRemote: Boolean = false,
    val supportsDirectPlay: Boolean = false,
    val supportsDirectStream: Boolean = false,
    val supportsTranscoding: Boolean = false,
    /** Server-relative path, present only when transcoding is the chosen method. */
    val transcodingUrl: String? = null,
    /** `MediaStreamProtocol` — `hls` in practice; anything else we cannot play. */
    val transcodingSubProtocol: String? = null,
    val transcodingContainer: String? = null,
    val mediaStreams: List<MediaStream> = emptyList(),
    val defaultAudioStreamIndex: Int? = null,
    val defaultSubtitleStreamIndex: Int? = null,
    val liveStreamId: String? = null,
    val requiresOpening: Boolean = false,
    val requiresClosing: Boolean = false,
    val openToken: String? = null,
)

/**
 * Progress reports. `PlaybackStartInfo` and `PlaybackProgressInfo` are the same shape server-side,
 * so one type covers both.
 *
 * [playSessionId] is what ties these to the negotiated stream. Without it the server cannot update
 * the resume point, and never learns a transcode was abandoned — so it keeps encoding.
 */
@Serializable
data class PlaybackProgressInfo(
    val itemId: String,
    val playSessionId: String,
    val mediaSourceId: String? = null,
    val positionTicks: Long = 0,
    val isPaused: Boolean = false,
    val canSeek: Boolean = true,
    /** [org.jellyfin.mobile.domain.PlayMethod] as the server spells it: DirectPlay, DirectStream, Transcode. */
    val playMethod: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)

@Serializable
data class PlaybackStopInfo(
    val itemId: String,
    val playSessionId: String,
    val mediaSourceId: String? = null,
    val positionTicks: Long = 0,
    val failed: Boolean = false,
)

@Serializable
data class MediaStream(
    val index: Int = 0,
    /** `MediaStreamType`: Video, Audio, Subtitle, EmbeddedImage, Data, Lyric. */
    val type: String? = null,
    val codec: String? = null,
    val language: String? = null,
    /** Server-composed label such as "English - Dolby Digital - 5.1 - Default". */
    val displayTitle: String? = null,
    val title: String? = null,
    val profile: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val isHearingImpaired: Boolean = false,
    /** `SubtitleDeliveryMethod`, set by the server on subtitle streams. */
    val deliveryMethod: String? = null,
    /** Server-relative path for an [isExternal] subtitle we must fetch ourselves. */
    val deliveryUrl: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val channels: Int? = null,
    val bitRate: Int? = null,
)
