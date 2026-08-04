package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.network.ImageType
import org.jellyfin.mobile.network.buildImageUrl
import org.jellyfin.mobile.network.dto.BaseItemDto

/** `BaseItemKind` values we special-case. */
private const val TYPE_EPISODE = "Episode"

private const val POSTER_MAX_HEIGHT = 480
private const val THUMB_MAX_HEIGHT = 280

fun BaseItemDto.toMediaItem(serverUrl: String, shape: CardShape): MediaItem = MediaItem(
    id = id,
    title = displayTitle(),
    subtitle = displaySubtitle(),
    imageUrl = imageUrl(serverUrl, shape),
    progress = resumeProgress(),
    watched = userData?.played == true,
)

/**
 * Episodes lead with the series name — a "Continue Watching" row full of episode titles with no
 * series context is unusable.
 */
private fun BaseItemDto.displayTitle(): String = when (type) {
    TYPE_EPISODE -> seriesName ?: name.orEmpty()
    else -> name.orEmpty()
}

private fun BaseItemDto.displaySubtitle(): String? = when (type) {
    TYPE_EPISODE -> {
        val episodeNumber = listOfNotNull(
            parentIndexNumber?.let { "S$it" },
            indexNumber?.let { "E$it" },
        ).joinToString(":").takeIf { it.isNotEmpty() }
        listOfNotNull(episodeNumber, name).joinToString(" · ").takeIf { it.isNotEmpty() }
    }
    else -> productionYear?.toString()
}

/**
 * Picks the best available artwork for [shape], falling back through the parent's images.
 *
 * An item only has an image of a given type if the server returned a *tag* for it; requesting a
 * type with no tag yields a 404 (or a placeholder), so the tag drives the choice.
 */
private fun BaseItemDto.imageUrl(serverUrl: String, shape: CardShape): String? {
    val candidates: List<Triple<String, String, String?>> = when (shape) {
        CardShape.Poster -> listOfNotNull(
            imageTags?.get(ImageType.PRIMARY)?.let { Triple(id, ImageType.PRIMARY, it) },
            seriesId?.let { series -> seriesPrimaryImageTag?.let { Triple(series, ImageType.PRIMARY, it) } },
        )
        CardShape.Thumb -> listOfNotNull(
            imageTags?.get(ImageType.THUMB)?.let { Triple(id, ImageType.THUMB, it) },
            // For an episode the Primary image is the still frame, which is the right landscape art.
            imageTags?.get(ImageType.PRIMARY)?.let { Triple(id, ImageType.PRIMARY, it) },
            parentThumbItemId?.let { parent -> parentThumbImageTag?.let { Triple(parent, ImageType.THUMB, it) } },
            backdropImageTags?.firstOrNull()?.let { Triple(id, ImageType.BACKDROP, it) },
            parentBackdropItemId?.let { parent ->
                parentBackdropImageTags?.firstOrNull()?.let { Triple(parent, ImageType.BACKDROP, it) }
            },
        )
    }

    val (itemId, imageType, tag) = candidates.firstOrNull() ?: return null
    return buildImageUrl(
        serverUrl = serverUrl,
        itemId = itemId,
        imageType = imageType,
        tag = tag,
        maxHeight = if (shape == CardShape.Poster) POSTER_MAX_HEIGHT else THUMB_MAX_HEIGHT,
    )
}

private fun BaseItemDto.resumeProgress(): Float? {
    val percentage = userData?.playedPercentage ?: return null
    return (percentage / 100.0).toFloat().takeIf { it > 0f }
}
