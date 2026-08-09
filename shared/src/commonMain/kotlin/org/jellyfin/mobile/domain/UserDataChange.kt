package org.jellyfin.mobile.domain

/**
 * One item's user-specific state, as the server reports it after something changed.
 *
 * Watched and favourite are per-user facts, and the same item is on several screens at once — an
 * episode is a row on its series page, a card in Continue Watching, a credit on an actor's page.
 * Marking it watched in one of those places has to move all of them, so every write is broadcast
 * through [org.jellyfin.mobile.data.UserDataStore] instead of being applied only where it was made.
 *
 * These are always the server's own values rather than the optimistic guess the screen showed while
 * the request was in flight: a toggle the server rejected, or applied further than we expected, must
 * not leave the rest of the app believing our version of it.
 */
data class UserDataChange(
    val itemId: String,
    val played: Boolean,
    val isFavorite: Boolean,
    /** Resume position as a 0..1 fraction, or null when the item is not partway through. */
    val progress: Float?,
    val playbackPositionTicks: Long,
    /**
     * Children still unplayed, which only the containers that roll up their children's played state
     * carry — series, seasons and collections. Null on everything else.
     */
    val unplayedItemCount: Int?,
    /**
     * Set when the server cascaded this to the item's children: marking a series, season or
     * collection watched marks everything inside it. The children's new values are *not* in this
     * change — there is nothing here saying what they even are — so a screen listing them has to
     * reload rather than patch.
     */
    val cascadedToChildren: Boolean = false,
)

/**
 * The item this change is about, with the change applied. Anything else is returned untouched, so a
 * screen can hand every change to everything it holds without first working out what matched.
 */
fun ItemDetail.applying(change: UserDataChange): ItemDetail = when (id) {
    change.itemId -> copy(
        isFavorite = change.isFavorite,
        isPlayed = change.played,
        progress = change.progress,
        playbackPositionTicks = change.playbackPositionTicks,
    )

    else -> this
}

/** People have no watched state; favouriting is the only thing that can change on one. */
fun PersonDetail.applying(change: UserDataChange): PersonDetail = when (id) {
    change.itemId -> copy(isFavorite = change.isFavorite)
    else -> this
}

fun Episode.applying(change: UserDataChange): Episode = when (id) {
    change.itemId -> copy(isPlayed = change.played, progress = change.progress)
    else -> this
}

fun Credit.applying(change: UserDataChange): Credit = when (id) {
    change.itemId -> copy(isPlayed = change.played)
    else -> this
}

fun MediaItem.applying(change: UserDataChange): MediaItem = when (id) {
    change.itemId -> copy(
        watched = change.played,
        progress = change.progress,
        unwatchedCount = change.unplayedItemCount,
    )

    else -> this
}

/**
 * Both halves move: the card's watched badge and progress bar, and the resume point tapping the row
 * would start from. Leaving the second behind is how a playlist entry watched to the end somewhere
 * else ends up restarting three seconds before its credits.
 */
fun PlaylistEntry.applying(change: UserDataChange): PlaylistEntry = when (item.id) {
    change.itemId -> copy(
        item = item.applying(change),
        playback = playback.copy(startPositionTicks = change.playbackPositionTicks),
    )

    else -> this
}

fun CreditList.applying(change: UserDataChange): CreditList =
    copy(credits = credits.map { it.applying(change) })

fun Filmography.applying(change: UserDataChange): Filmography = copy(
    movies = movies.applying(change),
    shows = shows.applying(change),
    episodes = episodes.applying(change),
)

fun HomeSection.applying(change: UserDataChange): HomeSection =
    copy(items = items.map { it.applying(change) })

fun LibraryRow.applying(change: UserDataChange): LibraryRow =
    copy(items = items.map { it.applying(change) })
