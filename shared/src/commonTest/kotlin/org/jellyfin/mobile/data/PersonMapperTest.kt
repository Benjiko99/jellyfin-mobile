package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.ExternalUrl
import org.jellyfin.mobile.network.dto.UserItemDataDto
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.episode_numbering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SERVER = "http://jellyfin.test"

class PersonMapperTest {
    @Test
    fun `maps a person, reading the biography from the overview`() {
        val person = BaseItemDto(
            id = "person-1",
            name = "Bryan Cranston",
            type = "Person",
            // Jellyfin has no dedicated biography field; it reuses Overview.
            overview = "An American actor and filmmaker.",
            premiereDate = "1956-03-07T00:00:00.0000000Z",
            productionLocations = listOf("Hollywood, California, USA"),
            imageTags = mapOf("Primary" to "portrait-tag"),
            userData = UserItemDataDto(isFavorite = true),
        ).toPersonDetail(SERVER)

        assertEquals("Bryan Cranston", person.name)
        assertEquals("An American actor and filmmaker.", person.biography)
        assertEquals(1956, person.birthYear)
        assertEquals("Hollywood, California, USA", person.birthPlace)
        assertTrue(person.isFavorite)
        assertTrue(person.imageUrl!!.startsWith("$SERVER/Items/person-1/Images/Primary"))
    }

    @Test
    fun `maps the provider links the server generated`() {
        val person = BaseItemDto(
            id = "person-1",
            name = "Bryan Cranston",
            type = "Person",
            externalUrls = listOf(
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/name/nm0186505"),
                ExternalUrl(name = "TMDb", url = "https://www.themoviedb.org/person/17419"),
            ),
        ).toPersonDetail(SERVER)

        assertEquals(listOf("IMDb", "TMDb"), person.links.map { it.name })
        assertEquals("https://www.imdb.com/name/nm0186505", person.links.first().url)
    }

    @Test
    fun `drops incomplete links and collapses duplicates`() {
        // A chip with no label, or one that goes nowhere, is worse than no chip.
        val person = BaseItemDto(
            id = "person-1",
            name = "Someone",
            type = "Person",
            externalUrls = listOf(
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/name/nm1"),
                ExternalUrl(name = null, url = "https://example.com/x"),
                ExternalUrl(name = "Broken", url = null),
                ExternalUrl(name = "  ", url = "https://example.com/y"),
                // Several providers enabled can report the same site twice.
                ExternalUrl(name = "IMDb", url = "https://www.imdb.com/name/nm1"),
            ),
        ).toPersonDetail(SERVER)

        assertEquals(listOf("IMDb"), person.links.map { it.name })
    }

    @Test
    fun `a person with no metadata still maps`() {
        // Sparsely-scraped libraries produce people with a name and nothing else.
        val person = BaseItemDto(id = "person-2", name = "Unknown", type = "Person").toPersonDetail(SERVER)

        assertNull(person.biography)
        assertNull(person.birthYear)
        assertNull(person.birthPlace)
        assertNull(person.imageUrl)
    }

    @Test
    fun `a film credit is titled with its year`() {
        val credit = BaseItemDto(
            id = "movie-1",
            name = "Drive",
            type = "Movie",
            productionYear = 2011,
            imageTags = mapOf("Primary" to "poster-tag"),
        ).toCredit(SERVER)

        assertEquals("Drive", credit.title)
        assertEquals(UiText.Raw("2011"), credit.subtitle)
    }

    @Test
    fun `an episode credit leads with the show it is from`() {
        // "Ozymandias" alone tells the user nothing about which credit this is.
        val credit = BaseItemDto(
            id = "ep-1",
            name = "Ozymandias",
            type = "Episode",
            seriesName = "Breaking Bad",
            parentIndexNumber = 5,
            indexNumber = 14,
        ).toCredit(SERVER)

        assertEquals("Ozymandias", credit.title)
        assertEquals(
            UiText.Joined(
                listOf(
                    UiText.Raw("Breaking Bad"),
                    UiText.Resource(Res.string.episode_numbering, listOf("5", "14")),
                ),
            ),
            credit.subtitle,
        )
    }

    @Test
    fun `an episode without its own still falls back to the show's poster`() {
        val credit = BaseItemDto(
            id = "ep-2",
            name = "Pilot",
            type = "Episode",
            seriesId = "series-1",
            seriesPrimaryImageTag = "series-poster",
        ).toCredit(SERVER)

        assertTrue(credit.imageUrl!!.startsWith("$SERVER/Items/series-1/Images/Primary"))
        assertTrue("tag=series-poster" in credit.imageUrl!!)
    }

    @Test
    fun `extracts the year from an ISO timestamp`() {
        assertEquals(1956, "1956-03-07T00:00:00.0000000Z".isoYear())
        assertNull("".isoYear())
        assertNull("not-a-date".isoYear())
    }
}
