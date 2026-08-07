package org.jellyfin.mobile.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.jellyfin.mobile.network.dto.AuthenticateUserByName
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult
import org.jellyfin.mobile.network.dto.WebConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The whole DTO layer relies on deriving PascalCase JSON names from camelCase Kotlin properties
 * instead of annotating each field. If that mapping is wrong every field silently decodes to null,
 * so it is worth pinning down against realistic payloads.
 */
class JellyfinJsonTest {
    @Test
    fun `decodes a resume episode`() {
        // Trimmed from a real /UserItems/Resume response.
        val json = """
            {
              "Items": [
                {
                  "Name": "Ozymandias",
                  "Id": "b1e4c0a2f8d94e6b9c3a7d5e2f108b64",
                  "SeriesName": "Breaking Bad",
                  "SeriesId": "9f2c1d8e4a5b46c7913e0f6d2a8b5c74",
                  "IndexNumber": 14,
                  "ParentIndexNumber": 5,
                  "RunTimeTicks": 28320000000,
                  "Type": "Episode",
                  "MediaType": "Video",
                  "PrimaryImageAspectRatio": 1.7777777777777777,
                  "UserData": {
                    "PlayedPercentage": 43.5,
                    "PlaybackPositionTicks": 12319200000,
                    "PlayCount": 1,
                    "IsFavorite": false,
                    "Played": false
                  },
                  "ImageTags": { "Primary": "3a7f2c9e1b4d" },
                  "BackdropImageTags": [],
                  "ParentBackdropItemId": "9f2c1d8e4a5b46c7913e0f6d2a8b5c74",
                  "ParentBackdropImageTags": ["d4e8b1a6c2f9"]
                }
              ],
              "TotalRecordCount": 1,
              "StartIndex": 0
            }
        """.trimIndent()

        val result = JellyfinJson.decodeFromString<BaseItemDtoQueryResult>(json)
        val item = result.items.single()

        assertEquals("Ozymandias", item.name)
        assertEquals("Breaking Bad", item.seriesName)
        assertEquals(14, item.indexNumber)
        assertEquals(5, item.parentIndexNumber)
        assertEquals("Episode", item.type)
        assertEquals(28_320_000_000L, item.runTimeTicks)
        assertEquals("3a7f2c9e1b4d", item.imageTags?.get("Primary"))
        assertEquals("d4e8b1a6c2f9", item.parentBackdropImageTags?.single())
        assertEquals(43.5, item.userData?.playedPercentage)
        assertEquals(false, item.userData?.played)
    }

    @Test
    fun `tolerates unknown fields and missing optionals`() {
        // Servers gain fields between releases and plugins add their own; neither may break decoding.
        val json = """
            { "Id": "abc", "Name": "Dune", "Type": "Movie", "SomeFutureField": { "nested": true } }
        """.trimIndent()

        val item = JellyfinJson.decodeFromString<BaseItemDto>(json)

        assertEquals("Dune", item.name)
        assertNull(item.userData)
        assertNull(item.imageTags)
    }

    @Test
    fun `encodes request bodies in PascalCase`() {
        val body = JellyfinJson.encodeToJsonElement(
            AuthenticateUserByName(username = "ben", pw = "hunter2"),
        ).jsonObject

        assertTrue("Username" in body, "expected PascalCase key, got ${body.keys}")
        assertTrue("Pw" in body, "expected PascalCase key, got ${body.keys}")
    }

    @Test
    fun `round trips through PascalCase`() {
        val original = BaseItemDto(id = "x", name = "Arrival", type = "Movie", productionYear = 2016)
        val encoded = JellyfinJson.encodeToJsonElement(original)
        assertEquals(original, JellyfinJson.decodeFromJsonElement<BaseItemDto>(encoded))
    }

    @Test
    fun `WebConfigJson leaves the web client's camelCase alone`() {
        val json = """{ "menuLinks": [{ "name": "Requests", "url": "https://requests.invalid/" }] }"""

        val config = WebConfigJson.decodeFromString<WebConfig>(json)

        assertEquals("Requests", config.menuLinks.single().name)
        assertEquals("https://requests.invalid/", config.menuLinks.single().url)
    }

    /**
     * Why [WebConfigJson] exists at all, rather than `@SerialName("menuLinks")` on [WebConfig].
     *
     * A naming strategy is applied to the serial name whether or not an annotation set it, so the
     * annotation would be PascalCased on its way out and would look like it had worked while doing
     * nothing. Pinned because the failure is silent: the field would just decode to its default.
     */
    @Test
    fun `a naming strategy rewrites even an explicit SerialName`() {
        val encoded = JellyfinJson.encodeToJsonElement(AnnotatedProbe(menuLinks = listOf("x"))).jsonObject

        assertTrue("MenuLinks" in encoded, "expected the strategy to win, got ${encoded.keys}")
    }
}

@Serializable
private data class AnnotatedProbe(
    @SerialName("menuLinks") val menuLinks: List<String> = emptyList(),
)
