package org.jellyfin.mobile.data

import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.BaseItemPerson
import org.jellyfin.mobile.network.dto.MediaUrl
import org.jellyfin.mobile.network.dto.NameGuidPair
import org.jellyfin.mobile.network.dto.UserItemDataDto
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
            BaseItemPerson(id = "p4", name = "Matthew McConaughey", role = "Cooper", type = "Actor", primaryImageTag = "t4"),
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
    fun `builds an IMDb link from provider ids`() {
        assertEquals("https://www.imdb.com/title/tt0816692/", movie().toItemDetail(SERVER).imdbUrl)
    }

    @Test
    fun `has no IMDb link when the server has no IMDb id`() {
        val detail = movie().copy(providerIds = mapOf("Tmdb" to "157336")).toItemDetail(SERVER)

        assertNull(detail.imdbUrl)
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
    fun `formats runtime`() {
        assertEquals("2h 49m", formatRuntime(101_520_000_000L))
        assertEquals("45m", formatRuntime(27_000_000_000L))
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
