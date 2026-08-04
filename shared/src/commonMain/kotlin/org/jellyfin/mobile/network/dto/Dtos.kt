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
    val type: String? = null,
    val mediaType: String? = null,
    val collectionType: String? = null,
    val overview: String? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
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
)

@Serializable
data class PublicSystemInfo(
    val id: String? = null,
    val serverName: String? = null,
    val version: String? = null,
    val productName: String? = null,
)
