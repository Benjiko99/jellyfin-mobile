package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.Credit
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.PersonDetail
import org.jellyfin.mobile.network.ImageType
import org.jellyfin.mobile.network.buildImageUrl
import org.jellyfin.mobile.network.dto.BaseItemDto

private const val PERSON_IMAGE_MAX_HEIGHT = 400
private const val CREDIT_IMAGE_MAX_HEIGHT = 300
private const val ISO_YEAR_LENGTH = 4

fun BaseItemDto.toPersonDetail(serverUrl: String): PersonDetail = PersonDetail(
    id = id,
    name = name.orEmpty(),
    // Jellyfin stores a person's biography in the ordinary overview field.
    biography = overview?.takeIf { it.isNotBlank() },
    imageUrl = imageTags?.get(ImageType.PRIMARY)?.let { tag ->
        buildImageUrl(serverUrl, id, ImageType.PRIMARY, tag, maxHeight = PERSON_IMAGE_MAX_HEIGHT)
    },
    birthYear = productionYear ?: premiereDate?.isoYear(),
    birthPlace = productionLocations?.firstOrNull()?.takeIf { it.isNotBlank() },
    isFavorite = userData?.isFavorite == true,
    links = externalLinks(),
)

fun BaseItemDto.toCredit(serverUrl: String): Credit = Credit(
    id = id,
    title = name.orEmpty(),
    subtitle = creditSubtitle(),
    imageUrl = imageTags?.get(ImageType.PRIMARY)?.let { tag ->
        buildImageUrl(serverUrl, id, ImageType.PRIMARY, tag, maxHeight = CREDIT_IMAGE_MAX_HEIGHT)
    } ?: seriesId?.let { series ->
        // Episodes frequently have no still of their own; the show's poster is a better
        // placeholder than an empty rectangle.
        seriesPrimaryImageTag?.let { tag ->
            buildImageUrl(serverUrl, series, ImageType.PRIMARY, tag, maxHeight = CREDIT_IMAGE_MAX_HEIGHT)
        }
    },
    isPlayed = userData?.played == true,
)

/**
 * An episode credit is meaningless without the show it belongs to, so that leads; films and shows
 * just get their year.
 */
private fun BaseItemDto.creditSubtitle(): String? =
    if (ItemKind.from(type) == ItemKind.Episode) {
        val numbering = listOfNotNull(
            parentIndexNumber?.let { "S$it" },
            indexNumber?.let { "E$it" },
        ).joinToString(":").takeIf { it.isNotEmpty() }
        listOfNotNull(seriesName, numbering).joinToString(" · ").takeIf { it.isNotEmpty() }
    } else {
        productionYear?.toString()
    }

/** Pulls the year out of an ISO-8601 timestamp without pulling in a date library. */
internal fun String.isoYear(): Int? = take(ISO_YEAR_LENGTH).toIntOrNull()
