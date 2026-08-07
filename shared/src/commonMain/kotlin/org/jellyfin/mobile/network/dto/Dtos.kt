package org.jellyfin.mobile.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire models. A deliberately small subset of the server's schemas — see
 * `api-spec/jellyfin-openapi-12.0.0.json` for the full definitions.
 *
 * Enum-valued fields ([BaseItemDto.type], [BaseItemDto.collectionType], …) are typed as `String`
 * on purpose: the server adds enum members between releases and plugins introduce their own, so
 * decoding into a Kotlin enum would fail on data we could otherwise display. They are converted to
 * typed domain values at the repository boundary.
 */
@Serializable
data class BaseItemDto(
    val id: String = "",
    val name: String? = null,
    val originalTitle: String? = null,
    val type: String? = null,
    val mediaType: String? = null,
    val collectionType: String? = null,
    val overview: String? = null,
    val taglines: List<String>? = null,
    val genres: List<String>? = null,
    val studios: List<NameGuidPair>? = null,
    val people: List<BaseItemPerson>? = null,
    /**
     * Jellyfin has no IMDb- or Rotten-Tomatoes-specific rating field. [communityRating] is a 0-10
     * score and [criticRating] a 0-100 percentage; which service populates each depends entirely on
     * the metadata providers configured on the server (with the OMDb plugin they are the IMDb
     * rating and the Rotten Tomatoes critic score respectively).
     */
    val communityRating: Float? = null,
    val criticRating: Float? = null,
    /** Age/content certification such as "PG-13" — unrelated to [communityRating]. */
    val officialRating: String? = null,
    /** External database ids, e.g. `{"Imdb": "tt0816692", "Tmdb": "157336"}`. */
    val providerIds: Map<String, String?>? = null,
    /**
     * Ready-made links the server generated for whichever metadata providers it has configured —
     * IMDb, TMDb, TheTVDB and so on. Preferable to assembling URLs from [providerIds] ourselves,
     * since the server knows which providers actually supplied this item.
     */
    val externalUrls: List<ExternalUrl>? = null,
    val remoteTrailers: List<MediaUrl>? = null,
    val localTrailerCount: Int? = null,
    val childCount: Int? = null,
    val status: String? = null,
    /** ISO-8601. On a Person this is the date of birth. */
    val premiereDate: String? = null,
    /** On a Person this is the birthplace. */
    val productionLocations: List<String>? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonId: String? = null,
    val seasonName: String? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val productionYear: Int? = null,
    val runTimeTicks: Long? = null,
    val primaryImageAspectRatio: Double? = null,
    val userData: UserItemDataDto? = null,
    /** Image type -> tag, e.g. `{"Primary": "a1b2…"}`. The tag is a cache key for the image URL. */
    val imageTags: Map<String, String>? = null,
    val backdropImageTags: List<String>? = null,
    val parentBackdropImageTags: List<String>? = null,
    val parentBackdropItemId: String? = null,
    val parentThumbItemId: String? = null,
    val parentThumbImageTag: String? = null,
    val seriesPrimaryImageTag: String? = null,
)

@Serializable
data class BaseItemPerson(
    val id: String = "",
    val name: String? = null,
    /** Character name for actors; job title for crew. */
    val role: String? = null,
    /** `PersonKind`: Actor, Director, Writer, GuestStar, Producer, Composer, … */
    val type: String? = null,
    val primaryImageTag: String? = null,
)

@Serializable
data class NameGuidPair(
    val id: String = "",
    val name: String? = null,
)

@Serializable
data class ExternalUrl(
    val name: String? = null,
    val url: String? = null,
)

@Serializable
data class MediaUrl(
    val url: String? = null,
    val name: String? = null,
)

@Serializable
data class UserItemDataDto(
    val playedPercentage: Double? = null,
    val playbackPositionTicks: Long? = null,
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val unplayedItemCount: Int? = null,
)

/** Standard envelope for list endpoints. Note `/Items/Latest` does *not* use it. */
@Serializable
data class BaseItemDtoQueryResult(
    val items: List<BaseItemDto> = emptyList(),
    val totalRecordCount: Int = 0,
    val startIndex: Int = 0,
)

@Serializable
data class AuthenticateUserByName(
    val username: String,
    val pw: String,
)

@Serializable
data class AuthenticationResult(
    val user: UserDto? = null,
    val accessToken: String? = null,
    val serverId: String? = null,
)

@Serializable
data class UserDto(
    val id: String = "",
    val name: String? = null,
    val serverId: String? = null,
    /**
     * Cache key for the user's profile picture, and the only way to know they have one: `/UserImage`
     * 404s for a user who has not set one, and a null tag lets us show the fallback without asking.
     */
    val primaryImageTag: String? = null,
)

@Serializable
data class PublicSystemInfo(
    val id: String? = null,
    val serverName: String? = null,
    val version: String? = null,
    val productName: String? = null,
)

/**
 * `GET /Items/Filters` — what a library can be filtered by.
 *
 * The legacy route's shape. Its successor `/Items/Filters2` returns richer genres but no
 * `OfficialRatings` or `Years`; see [org.jellyfin.mobile.network.JellyfinApi.itemFilters].
 */
@Serializable
data class QueryFiltersLegacy(
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val officialRatings: List<String>? = null,
    val years: List<Int>? = null,
)
