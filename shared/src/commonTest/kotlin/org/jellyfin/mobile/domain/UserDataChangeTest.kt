package org.jellyfin.mobile.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun change(
    itemId: String,
    played: Boolean = true,
    isFavorite: Boolean = false,
    progress: Float? = null,
    unplayedItemCount: Int? = null,
) = UserDataChange(
    itemId = itemId,
    played = played,
    isFavorite = isFavorite,
    progress = progress,
    playbackPositionTicks = 0,
    unplayedItemCount = unplayedItemCount,
)

class UserDataChangeTest {
    private val episode = Episode(
        id = "episode-1",
        title = "The Branch Line",
        indexNumber = 1,
        overview = null,
        runtime = null,
        imageUrl = null,
        isPlayed = false,
        progress = 0.4f,
    )

    private val card = MediaItem(
        id = "series-1",
        title = "Northern Line",
        subtitle = null,
        imageUrl = null,
        progress = 0.4f,
        watched = false,
        unwatchedCount = 6,
        kind = ItemKind.Series,
    )

    @Test
    fun `an episode follows a change about itself`() {
        val updated = episode.applying(change("episode-1", played = true, progress = null))

        assertTrue(updated.isPlayed)
        // Marking watched clears the resume point server-side, so the bar goes with it.
        assertNull(updated.progress)
    }

    @Test
    fun `a change about something else is not a change at all`() {
        // Every screen is handed every change, so the no-match case is the common one — and it has
        // to be free of side effects, not merely harmless.
        assertSame(episode, episode.applying(change("episode-2")))
        assertSame(card, card.applying(change("movie-9")))
    }

    @Test
    fun `a card takes the new unwatched count, which it cannot work out for itself`() {
        val updated = card.applying(change("series-1", played = false, unplayedItemCount = 5))

        assertEquals(5, updated.unwatchedCount)
        assertFalse(updated.watched)
        assertEquals(WatchBadge.Unwatched(5), updated.watchBadge)
    }

    @Test
    fun `a fully watched series is badged with a tick rather than a zero`() {
        val updated = card.applying(change("series-1", played = true, unplayedItemCount = 0))

        assertEquals(WatchBadge.Watched, updated.watchBadge)
    }

    @Test
    fun `a change reaches the items inside a row without the caller unpacking it`() {
        val section = HomeSection(
            id = "resume",
            items = listOf(card, card.copy(id = "series-2")),
            kind = SectionKind.Resume,
        )

        val updated = section.applying(change("series-2", played = true))

        assertFalse(updated.items[0].watched)
        assertTrue(updated.items[1].watched)
    }

    @Test
    fun `a person only has a favourite state to change`() {
        val person = PersonDetail(
            id = "person-1",
            name = "A Name",
            biography = null,
            imageUrl = null,
            imageFullUrl = null,
            birthYear = null,
            birthPlace = null,
            isFavorite = false,
        )

        assertTrue(person.applying(change("person-1", isFavorite = true)).isFavorite)
    }
}
