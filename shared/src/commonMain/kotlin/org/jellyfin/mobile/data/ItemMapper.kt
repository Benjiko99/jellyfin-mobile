package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.EpisodeArtwork
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.MediaItem
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.ImageType
import org.jellyfin.mobile.network.buildImageUrl
import org.jellyfin.mobile.network.dto.BaseItemDto

private const val POSTER_MAX_HEIGHT = 480
private const val THUMB_MAX_HEIGHT = 280

/**
 * @param artwork whether an episode shows its own still or its series' art. Defaults to the
 *   episode's own: a caller that has not thought about it is showing the item it asked for.
 */
fun BaseItemDto.toMediaItem(
    serverUrl: String,
    shape: CardShape,
    artwork: EpisodeArtwork = EpisodeArtwork.Own,
): MediaItem {
    val kind = ItemKind.from(type)
    return MediaItem(
        id = id,
        title = displayTitle(kind),
        subtitle = displaySubtitle(kind),
        imageUrl = imageUrl(serverUrl, shape, kind, artwork),
        progress = resumeProgress(),
        watched = userData?.played == true,
        // Populated by the server for folders that roll up their children's played state (series,
        // seasons, collections) and absent everywhere else. There is no `ItemFields` value for it, so
        // it costs nothing to ask for and cannot be requested if the server omits it.
        unwatchedCount = userData?.unplayedItemCount,
        kind = kind,
    )
}

/**
 * Episodes lead with the series name — a "Continue Watching" row full of episode titles with no
 * series context is unusable.
 */
private fun BaseItemDto.displayTitle(kind: ItemKind): String = when (kind) {
    ItemKind.Episode -> seriesName ?: name.orEmpty()
    else -> name.orEmpty()
}

private fun BaseItemDto.displaySubtitle(kind: ItemKind): UiText? = when (kind) {
    // The title above says the series, so this line says which episode of it.
    ItemKind.Episode -> listOfNotNull(episodeNumbering(), name?.let(UiText::Raw))
        .takeIf { it.isNotEmpty() }
        ?.let(UiText::Joined)

    // A person's "year" would be their birth year, which is not what this line is for.
    ItemKind.Person -> null
    else -> productionYear?.let { UiText.Raw(it.toString()) }
}

/**
 * Picks the best available artwork for [shape], falling back through the parent's images.
 *
 * An item only has an image of a given type if the server returned a *tag* for it; requesting a
 * type with no tag yields a 404 (or a placeholder), so the tag drives the choice.
 */
private fun BaseItemDto.imageUrl(
    serverUrl: String,
    shape: CardShape,
    kind: ItemKind,
    artwork: EpisodeArtwork,
): String? {
    val ownThumb = imageTags?.get(ImageType.THUMB)?.let { Triple(id, ImageType.THUMB, it) }
    // For an episode the Primary image is the still frame from that episode.
    val ownPrimary = imageTags?.get(ImageType.PRIMARY)?.let { Triple(id, ImageType.PRIMARY, it) }
    val ownBackdrop = backdropImageTags?.firstOrNull()?.let { Triple(id, ImageType.BACKDROP, it) }
    // `parentThumbItemId` is whichever ancestor actually carries the image — the series for an
    // episode, but a library folder for a movie, which is why only episodes lead with it.
    val parentThumb = parentThumbItemId?.let { parent ->
        parentThumbImageTag?.let { Triple(parent, ImageType.THUMB, it) }
    }
    val parentBackdrop = parentBackdropItemId?.let { parent ->
        parentBackdropImageTags?.firstOrNull()?.let { Triple(parent, ImageType.BACKDROP, it) }
    }

    val seriesPoster = seriesId?.let { series ->
        seriesPrimaryImageTag?.let { Triple(series, ImageType.PRIMARY, it) }
    }

    // An episode showing its series is the only case that reorders anything; everywhere else the
    // item's own artwork leads, and the parent's is a fallback for a sparsely scraped library.
    val standsInForSeries = kind == ItemKind.Episode && artwork == EpisodeArtwork.Series

    val candidates: List<Triple<String, String, String?>> = when {
        // The row is a row of shows, so the 2:3 frame wants the show's poster. This is "Recently
        // Added" in a TV library: `/Items/Latest` with `groupItems` returns the episode rather than
        // the series it grouped it under, and an episode's own Primary is a 16:9 still.
        shape == CardShape.Poster && standsInForSeries -> listOfNotNull(seriesPoster, ownPrimary)

        shape == CardShape.Poster -> listOfNotNull(ownPrimary, seriesPoster)

        // Continue Watching and Next Up: a row of still frames is a row of dark interchangeable
        // images with no clue which show each belongs to, so the series' landscape art comes first
        // and the still is what is left when a show has neither. jellyfin-web orders Next Up
        // the same way.
        standsInForSeries -> listOfNotNull(parentThumb, parentBackdrop, ownThumb, ownPrimary, ownBackdrop)

        else -> listOfNotNull(ownThumb, ownPrimary, parentThumb, ownBackdrop, parentBackdrop)
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
