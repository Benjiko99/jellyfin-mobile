package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.BaseItemPerson
import org.jellyfin.mobile.network.dto.ExternalUrl
import org.jellyfin.mobile.network.dto.MediaUrl
import org.jellyfin.mobile.network.dto.NameGuidPair
import org.jellyfin.mobile.network.dto.UserItemDataDto
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.episode_numbering
import org.jellyfin.mobile.resources.episode_numbering_episode_only
import org.jellyfin.mobile.resources.runtime_hours_minutes
import org.jellyfin.mobile.resources.runtime_minutes
import org.jellyfin.mobile.ui.detail.oneDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SERVER = "http://jellyfin.test"

class DetailMapperTest {
    private fun movie() = BaseItemDto(
        id = "item-1",
        name = "Interstellar",
        originalTitle = "Interstellar",
        type = "Movie",
        overview = "A team travels through a wormhole.",
        taglines = listOf("Mankind was born on Earth."),
        genres = listOf("Adventure", "Drama", "Science Fiction"),
        studios = listOf(NameGuidPair(id = "s1", name = "Legendary Pictures")),
        communityRating = 8.44f,
        criticRating = 72f,
        officialRating = "PG-13",
        productionYear = 2014,
        runTimeTicks = 101_520_000_000L,
        providerIds = mapOf("Imdb" to "tt0816692", "Tmdb" to "157336"),
        remoteTrailers = listOf(MediaUrl(url = "https://youtube.com/watch?v=abc", name = "Trailer")),
        imageTags = mapOf("Primary" to "poster-tag"),
        backdropImageTags = listOf("backdrop-tag"),
        userData = UserItemDataDto(isFavorite = true, played = false, playedPercentage = 35.0),
        people = listOf(
            BaseItemPerson(id = "p1", name = "Christopher Nolan", type = "Director"),
            BaseItemPerson(id = "p2", name = "Jonathan Nolan", type = "Writer"),
            BaseItemPerson(id = "p3", name = "Christopher Nolan", type = "Writer"),
            BaseItemPerson(
                id = "p4",
                name = "Matthew McConaughey",
                role = "Cooper",
                type = "Actor",
                primaryImageTag = "t4",
            ),
            BaseItemPerson(id = "p5", name = "Anne Hathaway", role = "Brand", type = "Actor"),
            BaseItemPerson(id = "p6", name = "Hans Zimmer", type = "Composer"),
        ),
    )

    @Test
    fun `groups people by their role`() {
        val detail = movie().toItemDetail(SERVER)

        assertEquals(listOf("Christopher Nolan"), detail.directors)
        assertEquals(listOf("Jonathan Nolan", "Christopher Nolan"), detail.writers)
        assertEquals(listOf("Matthew McConaughey", "Anne Hathaway"), detail.cast.map { it.name })
        assertEquals("Cooper", detail.cast.first().role)
        // Composers and other crew are not cast and must not leak into the row.
        assertFalse(detail.cast.any { it.name == "Hans Zimmer" })
    }

    @Test
    fun `deduplicates a person credited twice in the same role`() {
        // Writers are frequently listed once per writing credit; showing the name twice looks broken.
        val people = listOf(
            BaseItemPerson(id = "p1", name = "Jonathan Nolan", type = "Writer"),
            BaseItemPerson(id = "p2", name = "Jonathan Nolan", type = "Writer"),
        )
        val detail = movie().copy(people = people).toItemDetail(SERVER)

        assertEquals(listOf("Jonathan Nolan"), detail.writers)
    }

    @Test
    fun `maps ratings without inventing a source`() {
        val detail = movie().toItemDetail(SERVER)

        assertEquals(8.44f, detail.ratings.community)
        assertEquals(72, detail.ratings.critic)
        assertEquals("PG-13", detail.ratings.official)
        assertTrue(detail.ratings.criticIsFresh)
    }

    @Test
    fun `critic score below sixty is rotten`() {
        val detail = movie().copy(criticRating = 59f).toItemDetail(SERVER)

        assertFalse(detail.ratings.criticIsFresh)
    }

    @Test
    fun `uses the provider links the server generated`() {
        val detail = movie().copy(
            externalUrls = listOf(
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/title/tt0816692"),
                ExternalUrl(name = "TMDb", url = "https://www.themoviedb.org/movie/157336"),
                ExternalUrl(name = "Trakt", url = "https://trakt.tv/movies/interstellar-2014"),
            ),
        ).toItemDetail(SERVER)

        assertEquals(listOf("IMDb", "TMDb", "Trakt"), detail.links.map { it.name })
    }

    @Test
    fun `has no links when the server generated none`() {
        // Which providers appear is the server's business; we render nothing rather than
        // fabricating a URL from a provider id that may not correspond to a real page.
        assertEquals(emptyList(), movie().copy(externalUrls = null).toItemDetail(SERVER).links)
    }

    @Test
    fun `hides the original title when it matches the title`() {
        assertNull(movie().toItemDetail(SERVER).originalTitle)
        assertEquals(
            "Le Fabuleux Destin d'Amélie Poulain",
            movie().copy(name = "Amélie", originalTitle = "Le Fabuleux Destin d'Amélie Poulain")
                .toItemDetail(SERVER).originalTitle,
        )
    }

    @Test
    fun `carries favourite, watched and resume state`() {
        val detail = movie().toItemDetail(SERVER)

        assertTrue(detail.isFavorite)
        assertFalse(detail.isPlayed)
        assertEquals(0.35f, detail.progress)
        assertFalse(detail.isContainer)
    }

    @Test
    fun `treats a series as a container so watched means everything inside`() {
        val detail = movie().copy(type = "Series", childCount = 3).toItemDetail(SERVER)

        assertTrue(detail.isContainer)
        assertEquals(3, detail.childCount)
    }

    @Test
    fun `a series lists its own episodes and a season lists its parent's`() {
        val series = movie().copy(id = "series-1", type = "Series").toItemDetail(SERVER)
        assertEquals("series-1", series.episodeListSeriesId)

        val season = movie().copy(id = "season-1", type = "Season", seriesId = "series-1").toItemDetail(SERVER)
        assertEquals("series-1", season.episodeListSeriesId)

        // Movies and episodes have no episode list of their own.
        assertNull(movie().toItemDetail(SERVER).episodeListSeriesId)
        assertNull(movie().copy(type = "Episode", seriesId = "series-1").toItemDetail(SERVER).episodeListSeriesId)
    }

    @Test
    fun `an episode links up to its series`() {
        val episode = movie().copy(
            id = "ep-1",
            name = "Ozymandias",
            type = "Episode",
            seriesId = "series-1",
            seriesName = "Breaking Bad",
            parentIndexNumber = 5,
            indexNumber = 14,
        ).toItemDetail(SERVER)

        assertEquals("series-1", episode.seriesLink?.id)
        assertEquals("Breaking Bad", episode.seriesLink?.label)
        assertEquals(
            UiText.Resource(Res.string.episode_numbering, listOf("5", "14")),
            episode.episodeNumbering,
        )
    }

    @Test
    fun `a season links up to its series`() {
        val season = movie().copy(
            id = "season-1",
            name = "Season 5",
            type = "Season",
            seriesId = "series-1",
            seriesName = "Breaking Bad",
        ).toItemDetail(SERVER)

        assertEquals("series-1", season.seriesLink?.id)
        // A season has no episode numbering of its own.
        assertNull(season.episodeNumbering)
    }

    @Test
    fun `a series does not link to itself and a movie has no series link`() {
        // A self-link would navigate to a second copy of the page the user is already on.
        val series = movie().copy(id = "series-1", type = "Series", seriesName = "Breaking Bad")
            .toItemDetail(SERVER)
        assertNull(series.seriesLink)

        assertNull(movie().toItemDetail(SERVER).seriesLink)
    }

    @Test
    fun `no series link when the server did not supply the series name`() {
        // Without a label there is nothing to render, so the link must not appear at all.
        val episode = movie().copy(type = "Episode", seriesId = "series-1", seriesName = null)
            .toItemDetail(SERVER)

        assertNull(episode.seriesLink)
    }

    @Test
    fun `the poster comes at two sizes, the larger one for the fullscreen viewer`() {
        val detail = movie().toItemDetail(SERVER)

        // Same image, same tag — only the cap differs, and the enlarged one has to be the larger.
        assertTrue(detail.posterUrl!!.startsWith("$SERVER/Items/item-1/Images/Primary"))
        assertTrue("tag=poster-tag" in detail.posterFullUrl!!)
        assertTrue("maxHeight=600" in detail.posterUrl!!)
        assertTrue("maxHeight=1600" in detail.posterFullUrl!!)
        assertEquals(detail.posterFullUrl, detail.coverImageUrl)
    }

    @Test
    fun `an episode with no still of its own enlarges the series backdrop instead`() {
        val episode = movie().copy(type = "Episode", imageTags = null)
            .toItemDetail(SERVER)

        // Which is what the page is showing in its hero, so the tap enlarges what was tapped.
        assertNull(episode.posterFullUrl)
        assertEquals(episode.backdropUrl, episode.coverImageUrl)
    }

    @Test
    fun `nothing to enlarge when the server sent no artwork`() {
        val bare = movie().copy(imageTags = null, backdropImageTags = null)

        // Null on both paths through the fallback, so neither page offers a tap that does nothing.
        assertNull(bare.toItemDetail(SERVER).coverImageUrl)
        assertNull(bare.copy(type = "Episode").toItemDetail(SERVER).coverImageUrl)
    }

    @Test
    fun `numbering tolerates a special with no season number`() {
        val special = movie().copy(type = "Episode", parentIndexNumber = null, indexNumber = 3)
            .toItemDetail(SERVER)

        assertEquals(
            UiText.Resource(Res.string.episode_numbering_episode_only, listOf("3")),
            special.episodeNumbering,
        )
    }

    @Test
    fun `maps a season`() {
        val season = BaseItemDto(
            id = "season-1",
            name = "Season 2",
            type = "Season",
            indexNumber = 2,
            imageTags = mapOf("Primary" to "season-tag"),
        ).toSeason(SERVER)

        assertEquals("Season 2", season.name)
        assertEquals(2, season.indexNumber)
        assertTrue(season.imageUrl!!.startsWith("$SERVER/Items/season-1/Images/Primary"))
        assertTrue("tag=season-tag" in season.imageUrl!!)
    }

    @Test
    fun `maps an episode with its watched and resume state`() {
        val episode = BaseItemDto(
            id = "ep-1",
            name = "Pilot",
            type = "Episode",
            indexNumber = 1,
            parentIndexNumber = 1,
            overview = "It begins.",
            runTimeTicks = 34_200_000_000L,
            imageTags = mapOf("Primary" to "still-tag"),
            userData = UserItemDataDto(played = true, playedPercentage = 100.0),
        ).toEpisode(SERVER)

        assertEquals("Pilot", episode.title)
        assertEquals(1, episode.indexNumber)
        assertEquals(UiText.Resource(Res.string.runtime_minutes, listOf("57")), episode.runtime)
        assertTrue(episode.isPlayed)
        // An episode's Primary image is the still frame.
        assertTrue(episode.imageUrl!!.startsWith("$SERVER/Items/ep-1/Images/Primary"))
    }

    @Test
    fun `an unwatched episode has no progress bar`() {
        val episode = BaseItemDto(id = "ep-2", name = "Next", type = "Episode").toEpisode(SERVER)

        assertNull(episode.progress)
        assertFalse(episode.isPlayed)
        assertNull(episode.imageUrl)
    }

    @Test
    fun `formats runtime`() {
        assertEquals(
            UiText.Resource(Res.string.runtime_hours_minutes, listOf("2", "49")),
            formatRuntime(101_520_000_000L),
        )
        assertEquals(
            UiText.Resource(Res.string.runtime_minutes, listOf("45")),
            formatRuntime(27_000_000_000L),
        )
        // A zero or missing runtime should render nothing rather than "0m".
        assertNull(formatRuntime(0))
    }

    @Test
    fun `formats ratings to one decimal`() {
        assertEquals("8.4", 8.44f.oneDecimal())
        assertEquals("8.5", 8.45f.oneDecimal())
        assertEquals("7.0", 7f.oneDecimal())
        assertEquals("10.0", 10f.oneDecimal())
    }
}
