package org.jellyfin.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.domain.UserDataChange
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.UserItemDataDto

/**
 * Slack for the broadcast below, so a toggle is not held up waiting for every screen on the back
 * stack to apply it. Changes come from taps, so a handful in flight at once is already generous.
 */
private const val CHANGE_BUFFER = 16

/**
 * Every change to watched or favourite state, and everyone who needs to hear about it.
 *
 * The same item is on several screens at once, and the ones underneath the current screen stay
 * composed and keep their view models — so marking an episode watched from its own page has to
 * reach the series page it was opened from, the Continue Watching row behind that, and the actor
 * page that lists it as a credit. Without a single broadcast point each of those goes stale until
 * something re-fetches it.
 *
 * This is deliberately the *only* place these two writes happen. The repositories beside it are
 * read-only; a second write path is how the app ends up with a change that reaches some screens and
 * not others.
 */
class UserDataStore(
    private val api: JellyfinApi,
    /**
     * App-scoped. A refresh must finish and be broadcast even when the screen that triggered it has
     * already been popped — which is exactly what happens when the user toggles and goes back.
     */
    private val scope: CoroutineScope,
) {
    private val _changes = MutableSharedFlow<UserDataChange>(extraBufferCapacity = CHANGE_BUFFER)
    val changes: SharedFlow<UserDataChange> = _changes.asSharedFlow()

    /** Returns the server's resulting state, which is not necessarily what was asked for. */
    suspend fun setFavorite(itemId: String, favorite: Boolean): Boolean {
        val data = api.setFavorite(itemId, favorite)
        publish(itemId, data)
        return data.isFavorite
    }

    /**
     * @param cascadesToChildren true for a series, season or collection: the server marks everything
     * inside it too, which no screen can work out for itself.
     * @param ancestorIds the season and series whose unplayed counts this moves. Passed in rather
     * than looked up because the caller already knows them — see [refresh] for the case that does
     * not.
     */
    suspend fun setPlayed(
        itemId: String,
        played: Boolean,
        cascadesToChildren: Boolean = false,
        ancestorIds: List<String> = emptyList(),
    ): Boolean {
        val data = api.setPlayed(itemId, played)
        publish(itemId, data, cascadedToChildren = cascadesToChildren)
        refresh(ancestorIds)
        return data.played
    }

    /**
     * Re-reads these items from the server and broadcasts what it says about them.
     *
     * For values we cannot derive: an episode's season and series both roll up their children's
     * played state, and only the server knows the new counts. It also chases one level of ancestor
     * off the response, so a caller that knows an item id but not where it sits in a show — the
     * player, once playback stops — gets the whole chain updated from the one id it has.
     *
     * Fire and forget, on the app scope: a failed refresh costs a stale badge, which is not worth
     * failing the toggle the user actually made.
     */
    fun refresh(itemIds: List<String>) {
        val ids = itemIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        scope.launch { runCatching { fetchAndPublish(ids) } }
    }

    private suspend fun fetchAndPublish(ids: List<String>) {
        val items = userDataFor(ids)
        items.forEach { publish(it.id, it.userData) }

        val ancestors = items
            .flatMap { listOfNotNull(it.seasonId, it.seriesId) }
            .distinct()
            .filterNot { it in ids || items.any { item -> item.id == it } }
        if (ancestors.isEmpty()) return

        userDataFor(ancestors).forEach { publish(it.id, it.userData) }
    }

    /**
     * Only the user data is wanted, so the artwork and the extra fields every other caller asks for
     * are turned off — this runs behind a tap the user has already seen the result of.
     */
    private suspend fun userDataFor(ids: List<String>): List<BaseItemDto> = api.items(
        ids = ids,
        fields = emptyList(),
        enableImageTypes = emptyList(),
    ).items

    private suspend fun publish(
        itemId: String,
        data: UserItemDataDto?,
        cascadedToChildren: Boolean = false,
    ) {
        _changes.emit(
            UserDataChange(
                itemId = itemId,
                played = data?.played == true,
                isFavorite = data?.isFavorite == true,
                progress = data?.playedPercentage?.let { (it / 100.0).toFloat() }?.takeIf { it > 0f },
                playbackPositionTicks = data?.playbackPositionTicks ?: 0,
                unplayedItemCount = data?.unplayedItemCount,
                cascadedToChildren = cascadedToChildren,
            ),
        )
    }
}
