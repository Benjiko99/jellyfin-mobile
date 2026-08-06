package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.WatchBadge
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.UserItemDataDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val SERVER = "http://jellyfin.test"

class ItemMapperTest {
    private fun item(
        type: String,
        userData: UserItemDataDto? = null,
        shape: CardShape = CardShape.Poster,
    ) = BaseItemDto(id = "item-1", name = "Item", type = type, userData = userData)
        .toMediaItem(SERVER, shape)

    @Test
    fun `badges a part-watched series with the number of episodes left`() {
        val badge = item("Series", UserItemDataDto(unplayedItemCount = 7)).watchBadge

        assertEquals(WatchBadge.Unwatched(7), badge)
    }

    @Test
    fun `badges a series nobody has started with its whole episode count`() {
        // The server reports every episode as unplayed here, which is the honest "left to watch".
        val badge = item("Series", UserItemDataDto(unplayedItemCount = 24, played = false)).watchBadge

        assertEquals(WatchBadge.Unwatched(24), badge)
    }

    @Test
    fun `badges a fully watched series with the tick`() {
        val badge = item("Series", UserItemDataDto(unplayedItemCount = 0, played = true)).watchBadge

        assertEquals(WatchBadge.Watched, badge)
    }

    @Test
    fun `badges collections and seasons the same way`() {
        val collection = item("BoxSet", UserItemDataDto(unplayedItemCount = 3))
        val season = item("Season", UserItemDataDto(unplayedItemCount = 0, played = true))

        assertEquals(WatchBadge.Unwatched(3), collection.watchBadge)
        assertEquals(WatchBadge.Watched, season.watchBadge)
    }

    @Test
    fun `leaves a container the server did not count unbadged`() {
        // No unplayedItemCount and not played: nothing truthful to show.
        assertNull(item("Series", UserItemDataDto()).watchBadge)
        assertNull(item("Series", userData = null).watchBadge)
    }

    @Test
    fun `ticks a watched movie`() {
        assertEquals(WatchBadge.Watched, item("Movie", UserItemDataDto(played = true)).watchBadge)
    }

    @Test
    fun `leaves an unwatched movie unbadged rather than counting it`() {
        // A movie has no children, so there is never a number to show — only the tick or nothing.
        assertNull(item("Movie", UserItemDataDto(played = false)).watchBadge)
        assertNull(item("Movie", UserItemDataDto(played = false, playedPercentage = 40.0)).watchBadge)
    }

    @Test
    fun `does not badge episodes or people`() {
        assertNull(item("Episode", UserItemDataDto(played = true), CardShape.Thumb).watchBadge)
        // A person is not library content at all.
        assertNull(item("Person", UserItemDataDto(played = true)).watchBadge)
    }

    @Test
    fun `carries the unplayed count through the mapper untouched`() {
        val mapped = item("Series", UserItemDataDto(unplayedItemCount = 12))

        assertEquals(12, mapped.unwatchedCount)
    }
}
