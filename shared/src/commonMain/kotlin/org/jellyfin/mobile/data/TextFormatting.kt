package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.episode_numbering
import org.jellyfin.mobile.resources.episode_numbering_episode_only
import org.jellyfin.mobile.resources.episode_numbering_season_only
import org.jellyfin.mobile.resources.runtime_hours_minutes
import org.jellyfin.mobile.resources.runtime_minutes

/**
 * The bits of text the mappers compose out of numbers.
 *
 * They produce [UiText] rather than `String` because a mapper is neither composable nor suspending,
 * so it cannot read a string itself — see [UiText]. Gathered here because all three mappers need
 * episode numbering and two of them need a runtime, and the three had a copy each.
 */

private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

/** Ticks are 100-nanosecond units. Renders as "2h 15m", or "45m" under an hour. */
internal fun formatRuntime(ticks: Long): UiText? {
    val totalMinutes = (ticks / TICKS_PER_SECOND / SECONDS_PER_MINUTE).toInt()
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        UiText.Resource(Res.string.runtime_hours_minutes, listOf(hours.toString(), minutes.toString()))
    } else {
        UiText.Resource(Res.string.runtime_minutes, listOf(minutes.toString()))
    }
}

/**
 * "S5:E14", tolerating either number being absent — specials frequently have no season, and a
 * scraper that failed on a filename can leave an episode without a number of its own.
 */
internal fun BaseItemDto.episodeNumbering(): UiText? {
    val season = parentIndexNumber
    val episode = indexNumber
    return when {
        season != null && episode != null ->
            UiText.Resource(Res.string.episode_numbering, listOf(season.toString(), episode.toString()))

        season != null ->
            UiText.Resource(Res.string.episode_numbering_season_only, listOf(season.toString()))

        episode != null ->
            UiText.Resource(Res.string.episode_numbering_episode_only, listOf(episode.toString()))

        else -> null
    }
}
