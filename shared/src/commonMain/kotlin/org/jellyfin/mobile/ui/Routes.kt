package org.jellyfin.mobile.ui

import kotlinx.serialization.Serializable
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.PlaybackQueue
import org.jellyfin.mobile.domain.PlaylistEntry
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.player_title_episode
import org.jellyfin.mobile.resources.player_title_episode_unnumbered
import org.jellyfin.mobile.resources.player_title_year

/** Type-safe navigation routes. Serializable so Navigation Compose can encode them into the graph. */
@Serializable
data object HomeRoute

@Serializable
data object SearchRoute

/**
 * This client's own preferences.
 *
 * The only settings screen that exists; the other four entries in the account menu are still
 * placeholders, so there is no shared settings host to hang it off yet.
 */
@Serializable
data object ClientSettingsRoute

/**
 * One library, browsed.
 *
 * [collectionType] is the raw `CollectionType` string rather than a resolved
 * [org.jellyfin.mobile.domain.LibraryKind], so an unrecognised type survives the round trip and is
 * resolved once, at the screen. [title] rides along because it is the library's administrator-given
 * name, which the drawer already knew and the screen would otherwise refetch to draw its header.
 */
@Serializable
data class LibraryRoute(
    val libraryId: String,
    val collectionType: String?,
    val title: String,
    /**
     * Set when the screen was opened from a genre or network row, which narrows it to that one
     * thing: [narrowedTab] is the tab to show — the only one, since the tabs either side of it lead
     * back out — and [genre] or [studioId] is what narrows it.
     *
     * A genre is matched by name and a studio by id, which is what each of their endpoints returns
     * and what `/Items` accepts back.
     */
    val narrowedTab: String? = null,
    val genre: String? = null,
    val studioId: String? = null,
)

@Serializable
data class DetailRoute(val itemId: String)

@Serializable
data class PersonRoute(val personId: String)

/**
 * The full, paged list behind a row's "More" action.
 *
 * [kind] is a [org.jellyfin.mobile.domain.SectionKind] name and identifies the query to re-run.
 * Neither the card shape nor the heading is carried — both are derived from the kind, which keeps
 * the row and this screen from disagreeing, and a heading could not ride along in any case: it is a
 * [org.jellyfin.mobile.domain.UiText] and this route has to serialize. [libraryName] is the one
 * thing the heading needs beyond the kind, and only for `LatestInLibrary`. [libraryItemKind] saves
 * the "More" screen a request to discover what a library holds. [searchTerm] is what the `Search*`
 * kinds need in place of a [parentId].
 */
@Serializable
data class SectionRoute(
    val kind: String,
    val parentId: String? = null,
    val libraryName: String? = null,
    val searchTerm: String? = null,
    val libraryItemKind: String? = null,
)

/**
 * [title] and [startPositionTicks] ride along so the player can render its header and resume at the
 * right frame without waiting on a second fetch of an item the detail screen already loaded.
 *
 * The header itself is not carried: it is a [org.jellyfin.mobile.domain.UiText] and this route has
 * to serialize. The route carries the parts and [PlayerRoute.header] writes them out, the same split
 * as [SectionRoute] and `SectionKind.title()`.
 */
@Serializable
data class PlayerRoute(
    val itemId: String,
    val title: String,
    val startPositionTicks: Long,
    /** Set when [title] is an episode's: the show it belongs to. Null for a movie. */
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /**
     * The show's id, which is what the player asks for the episodes either side of this one. Set
     * whenever [seriesName] is; carried separately because it is an id the header never prints.
     */
    val seriesId: String? = null,
    /** Set when playback started from a playlist: the list the skip buttons then move along. */
    val playlistId: String? = null,
    /** Year of release, which is what distinguishes a film from its remake. */
    val year: Int? = null,
)

/**
 * Playing an item from its own page.
 *
 * An episode carries its series, so the player can offer the episodes either side of it — the show
 * is the queue whenever nothing more deliberate was chosen.
 */
fun ItemDetail.playerRoute(): PlayerRoute = PlayerRoute(
    itemId = id,
    title = title,
    startPositionTicks = playbackPositionTicks,
    // Null on anything that is not an episode, which is what leaves the player showing a film's
    // title on its own.
    seriesName = seriesLink?.label,
    seasonNumber = seasonNumber,
    episodeNumber = episodeNumber,
    seriesId = seriesId,
    year = year,
)

/**
 * Playing an entry from inside a playlist.
 *
 * The series is deliberately not carried even when the entry is an episode: the user pressed play
 * in a playlist, so the playlist is what Next should follow. Sending both would only make
 * [PlayerRoute.queue] pick between them again.
 */
fun PlaylistEntry.playerRoute(playlistId: String): PlayerRoute = PlayerRoute(
    itemId = playback.id,
    title = playback.title,
    startPositionTicks = playback.startPositionTicks,
    seriesName = playback.seriesName,
    seasonNumber = playback.seasonNumber,
    episodeNumber = playback.episodeNumber,
    playlistId = playlistId,
    year = playback.year,
)

/**
 * The list the player's skip buttons move along, or null when this item was opened on its own.
 *
 * A playlist beats the series an episode belongs to. Someone who pressed play inside a playlist
 * means the playlist, even where the next entry is the next episode anyway — and the two orders
 * diverge the moment a playlist is arranged into anything but air order.
 */
fun PlayerRoute.queue(): PlaybackQueue? = when {
    playlistId != null -> PlaybackQueue.Playlist(playlistId)
    seriesId != null -> PlaybackQueue.Series(seriesId)
    else -> null
}

/**
 * What the player's header reads.
 *
 * An episode is named after its show — "Northern Line · The Undertow (S02E04)" — because its own
 * title says nothing about what is playing, and the numbers are what tell two similarly named
 * episodes apart. Padded to two digits, the form every episode file name uses. An episode the
 * scraper left unnumbered drops the bracket rather than printing a half of one.
 *
 * Everything else is named for itself and dated — "The Cartographer (2019)" — which is the one
 * thing that separates a film from the remake it shares a title with. Both parts can be missing:
 * without a series name an episode falls in with the films, and without a year any of them shows
 * its title alone.
 */
fun PlayerRoute.header(): UiText = playerHeader(title, seriesName, seasonNumber, episodeNumber, year)

/**
 * [PlayerRoute.header] with the parts passed in, for the player's own use.
 *
 * Skipping to the next episode changes the header without going through a route — the player keeps
 * its engine and swaps the item underneath it — and this is what stops that second caller wording
 * the same sentence its own way.
 */
fun playerHeader(
    title: String,
    seriesName: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    year: Int? = null,
): UiText {
    val series = seriesName?.takeIf { it.isNotBlank() } ?: return titleWithYear(title, year)
    return when {
        seasonNumber != null && episodeNumber != null -> UiText.Resource(
            Res.string.player_title_episode,
            listOf(series, title, seasonNumber.padded(), episodeNumber.padded()),
        )

        else -> UiText.Resource(Res.string.player_title_episode_unnumbered, listOf(series, title))
    }
}

private fun titleWithYear(title: String, year: Int?): UiText = when (year) {
    null -> UiText.Raw(title)
    else -> UiText.Resource(Res.string.player_title_year, listOf(title, year.toString()))
}

private const val NUMBER_DIGITS = 2

private fun Int.padded(): String = toString().padStart(NUMBER_DIGITS, '0')

/**
 * The full, paged list behind a "More" button.
 *
 * [personName] is carried in the route so the header renders immediately instead of blocking on a
 * second fetch of a person we have already loaded. [kind] is a [org.jellyfin.mobile.domain.CreditKind]
 * name.
 */
@Serializable
data class PersonCreditsRoute(
    val personId: String,
    val personName: String,
    val kind: String,
)
