package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.EpisodeArtwork
import org.jellyfin.mobile.domain.SectionKind
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

    /**
     * An episode arriving from `/UserItems/Resume` or `/Shows/NextUp` with everything the server can
     * send: its own still frame, and the series' Thumb and Backdrop through the parent tags.
     */
    private fun episode(
        ownThumb: String? = null,
        ownPrimary: String? = "still",
        ownBackdrop: String? = null,
        seriesThumb: String? = "series-thumb",
        seriesBackdrop: String? = "series-backdrop",
        artwork: EpisodeArtwork = EpisodeArtwork.Series,
    ) = BaseItemDto(
        id = "episode-1",
        name = "Episode",
        type = "Episode",
        seriesId = "series-1",
        seriesName = "Series",
        imageTags = buildMap {
            ownThumb?.let { put("Thumb", it) }
            ownPrimary?.let { put("Primary", it) }
        },
        backdropImageTags = listOfNotNull(ownBackdrop),
        parentThumbItemId = seriesThumb?.let { "series-1" },
        parentThumbImageTag = seriesThumb,
        parentBackdropItemId = seriesBackdrop?.let { "series-1" },
        parentBackdropImageTags = listOfNotNull(seriesBackdrop),
    ).toMediaItem(SERVER, CardShape.Thumb, artwork)

    @Test
    fun `shows the series artwork on an episode card rather than the episode still`() {
        val mapped = episode()

        assertEquals(
            "$SERVER/Items/series-1/Images/Thumb?tag=series-thumb&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `falls back to the series backdrop when the series has no thumb`() {
        val mapped = episode(seriesThumb = null)

        assertEquals(
            "$SERVER/Items/series-1/Images/Backdrop?tag=series-backdrop&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `uses the episode still only when the series has no landscape art at all`() {
        val mapped = episode(seriesThumb = null, seriesBackdrop = null)

        assertEquals(
            "$SERVER/Items/episode-1/Images/Primary?tag=still&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    /**
     * The Favorites tab and search results: the episode *is* the result, so it keeps its own still
     * even though the series has artwork that would fit the card.
     */
    @Test
    fun `keeps the episode still where the episode is the item, not a stand-in`() {
        val mapped = episode(artwork = EpisodeArtwork.Own)

        assertEquals(
            "$SERVER/Items/episode-1/Images/Primary?tag=still&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `prefers a real episode thumb over the still where the episode is the item`() {
        val mapped = episode(ownThumb = "episode-thumb", artwork = EpisodeArtwork.Own)

        assertEquals(
            "$SERVER/Items/episode-1/Images/Thumb?tag=episode-thumb&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `still falls back to the series when a standalone episode has no artwork of its own`() {
        val mapped = episode(ownPrimary = null, artwork = EpisodeArtwork.Own)

        assertEquals(
            "$SERVER/Items/series-1/Images/Thumb?tag=series-thumb&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `the rows that stand in for a series are exactly the ones that say so`() {
        // Pinned as a table: getting one of these wrong is invisible until someone looks at the
        // screen, since every value here produces a working image URL.
        assertEquals(
            listOf(
                SectionKind.Resume,
                SectionKind.NextUp,
                SectionKind.LatestInLibrary,
            ),
            SectionKind.entries.filter { it.episodeArtwork == EpisodeArtwork.Series },
        )
    }

    @Test
    fun `keeps a movie's own artwork ahead of its library folder's`() {
        // `parentThumbItemId` on a movie is the library folder, whose artwork is not the film's.
        val mapped = BaseItemDto(
            id = "movie-1",
            name = "Movie",
            type = "Movie",
            imageTags = mapOf("Primary" to "poster"),
            parentThumbItemId = "library-1",
            parentThumbImageTag = "library-thumb",
        ).toMediaItem(SERVER, CardShape.Thumb)

        assertEquals(
            "$SERVER/Items/movie-1/Images/Primary?tag=poster&maxHeight=280&quality=90",
            mapped.imageUrl,
        )
    }

    /**
     * "Recently Added in <TV library>": `/Items/Latest` groups episodes under their series but
     * still returns the episode, so a poster card here holds an Episode.
     */
    @Test
    fun `shows the series poster for a grouped episode on a poster card`() {
        val mapped = BaseItemDto(
            id = "episode-1",
            name = "Episode",
            type = "Episode",
            seriesId = "series-1",
            seriesName = "Series",
            imageTags = mapOf("Primary" to "still"),
            seriesPrimaryImageTag = "series-poster",
        ).toMediaItem(SERVER, CardShape.Poster, EpisodeArtwork.Series)

        assertEquals(
            "$SERVER/Items/series-1/Images/Primary?tag=series-poster&maxHeight=480&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `falls back to the episode still when the series has no poster`() {
        val mapped = BaseItemDto(
            id = "episode-1",
            name = "Episode",
            type = "Episode",
            seriesId = "series-1",
            imageTags = mapOf("Primary" to "still"),
        ).toMediaItem(SERVER, CardShape.Poster, EpisodeArtwork.Series)

        assertEquals(
            "$SERVER/Items/episode-1/Images/Primary?tag=still&maxHeight=480&quality=90",
            mapped.imageUrl,
        )
    }

    @Test
    fun `leaves a poster card on the item's own primary image`() {
        val mapped = BaseItemDto(
            id = "series-1",
            name = "Series",
            type = "Series",
            imageTags = mapOf("Primary" to "poster"),
        ).toMediaItem(SERVER, CardShape.Poster)

        assertEquals(
            "$SERVER/Items/series-1/Images/Primary?tag=poster&maxHeight=480&quality=90",
            mapped.imageUrl,
        )
    }
}
