package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.MediaTrack
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.dto.MediaStream
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_track_unnamed

private const val TYPE_AUDIO = "Audio"
private const val TYPE_SUBTITLE = "Subtitle"

internal fun List<MediaStream>.audioTracks(): List<MediaTrack> =
    filter { it.type == TYPE_AUDIO }.map { it.toTrack(resolveUrl = null) }

/**
 * Every subtitle stream, including ones the server is currently burning into the video.
 *
 * Delivery method deliberately does not filter this list. It describes how *this* negotiation is
 * delivering a subtitle, not whether the track can be chosen — switching re-negotiates, so a
 * burned-in subtitle is as selectable as any other. Filtering on it also hid the active track from
 * its own menu whenever the server was transcoding, since that is precisely when it burns in.
 *
 * @param resolveUrl turns a server-relative `deliveryUrl` into an absolute one. Passed in rather
 * than resolved here so this file stays free of the API client.
 */
internal fun List<MediaStream>.subtitleTracks(resolveUrl: (String) -> String): List<MediaTrack> =
    filter { it.type == TYPE_SUBTITLE }.map { it.toTrack(resolveUrl) }

private fun MediaStream.toTrack(resolveUrl: ((String) -> String)?) = MediaTrack(
    index = index,
    // displayTitle is the server's composed label ("English - Dolby Digital - 5.1"). Falling back
    // through title and language beats showing a bare stream number — and all three are the
    // server's own words, so only the last resort is a string of ours.
    label = (displayTitle ?: title ?: language)?.let(UiText::Raw)
        ?: UiText.Resource(Res.string.player_track_unnamed, listOf(index.toString())),
    language = language,
    codec = codec,
    deliveryUrl = deliveryUrl?.let { resolveUrl?.invoke(it) },
)
