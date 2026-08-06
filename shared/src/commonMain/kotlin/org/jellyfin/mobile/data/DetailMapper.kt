package org.jellyfin.mobile.data

import org.jellyfin.mobile.domain.CastMember
import org.jellyfin.mobile.domain.Episode
import org.jellyfin.mobile.domain.ItemDetail
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.ParentLink
import org.jellyfin.mobile.domain.Ratings
import org.jellyfin.mobile.domain.Season
import org.jellyfin.mobile.network.ImageType
import org.jellyfin.mobile.network.buildImageUrl
import org.jellyfin.mobile.network.dto.BaseItemDto
import kotlin.math.roundToInt

/** `PersonKind` values we group by. */
private const val PERSON_DIRECTOR = "Director"
private const val PERSON_WRITER = "Writer"
private const val PERSON_ACTOR = "Actor"
private const val PERSON_GUEST_STAR = "GuestStar"

private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60

private const val POSTER_MAX_HEIGHT = 600
private const val BACKDROP_MAX_WIDTH = 1280
private const val CAST_IMAGE_MAX_HEIGHT = 180
private const val EPISODE_IMAGE_MAX_HEIGHT = 220

fun BaseItemDto.toItemDetail(serverUrl: String): ItemDetail {
    val people = people.orEmpty()

    return ItemDetail(
        id = id,
        title = name.orEmpty(),
        // Only worth showing when it actually differs, otherwise it is noise on every English title.
        originalTitle = originalTitle?.takeIf { it.isNotBlank() && it != name },
        tagline = taglines?.firstOrNull()?.takeIf { it.isNotBlank() },
        overview = overview?.takeIf { it.isNotBlank() },
        year = productionYear,
        runtime = runTimeTicks?.let(::formatRuntime),
        ratings = Ratings(
            community = communityRating,
            critic = criticRating?.roundToInt(),
            official = officialRating?.takeIf { it.isNotBlank() },
        ),
        genres = genres.orEmpty(),
        studios = studios.orEmpty().mapNotNull { it.name },
        directors = people.filter { it.type == PERSON_DIRECTOR }.mapNotNull { it.name },
        writers = people.filter { it.type == PERSON_WRITER }.mapNotNull { it.name }.distinct(),
        cast = people
            .filter { it.type == PERSON_ACTOR || it.type == PERSON_GUEST_STAR }
            .map { person ->
                CastMember(
                    id = person.id,
                    name = person.name.orEmpty(),
                    role = person.role?.takeIf { it.isNotBlank() },
                    imageUrl = person.primaryImageTag?.let { tag ->
                        buildImageUrl(
                            serverUrl = serverUrl,
                            itemId = person.id,
                            imageType = ImageType.PRIMARY,
                            tag = tag,
                            maxHeight = CAST_IMAGE_MAX_HEIGHT,
                        )
                    },
                )
            },
        posterUrl = imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            buildImageUrl(serverUrl, id, ImageType.PRIMARY, tag, maxHeight = POSTER_MAX_HEIGHT)
        },
        backdropUrl = backdropImageTags?.firstOrNull()?.let { tag ->
            buildImageUrl(serverUrl, id, ImageType.BACKDROP, tag, maxWidth = BACKDROP_MAX_WIDTH)
        } ?: parentBackdropItemId?.let { parent ->
            parentBackdropImageTags?.firstOrNull()?.let { tag ->
                buildImageUrl(serverUrl, parent, ImageType.BACKDROP, tag, maxWidth = BACKDROP_MAX_WIDTH)
            }
        },
        // Remote trailers are provider links (almost always YouTube), so they open externally
        // rather than in our player.
        trailerUrl = remoteTrailers?.firstOrNull { !it.url.isNullOrBlank() }?.url,
        links = externalLinks(),
        isFavorite = userData?.isFavorite == true,
        isPlayed = userData?.played == true,
        progress = userData?.playedPercentage?.let { (it / 100.0).toFloat() }?.takeIf { it > 0f },
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        kind = ItemKind.from(type),
        seriesId = seriesId,
        seriesLink = seriesLink(),
        episodeNumbering = episodeNumbering(),
        childCount = childCount,
    )
}

/**
 * Only for items *below* a series. A series must not link to itself, and a movie has no parent —
 * both would otherwise produce a link that navigates nowhere useful.
 */
private fun BaseItemDto.seriesLink(): ParentLink? {
    if (ItemKind.from(type) !in setOf(ItemKind.Episode, ItemKind.Season)) return null
    val id = seriesId?.takeIf { it.isNotBlank() } ?: return null
    val label = seriesName?.takeIf { it.isNotBlank() } ?: return null
    return ParentLink(id = id, label = label)
}

/** Season and episode numbers, tolerating either being absent (specials often have no season). */
private fun BaseItemDto.episodeNumbering(): String? {
    if (ItemKind.from(type) != ItemKind.Episode) return null
    return listOfNotNull(
        parentIndexNumber?.let { "S$it" },
        indexNumber?.let { "E$it" },
    ).joinToString(":").takeIf { it.isNotEmpty() }
}

fun BaseItemDto.toSeason(serverUrl: String): Season = Season(
    id = id,
    name = name.orEmpty(),
    indexNumber = indexNumber,
    imageUrl = imageTags?.get(ImageType.PRIMARY)?.let { tag ->
        buildImageUrl(serverUrl, id, ImageType.PRIMARY, tag, maxHeight = POSTER_MAX_HEIGHT)
    },
)

fun BaseItemDto.toEpisode(serverUrl: String): Episode = Episode(
    id = id,
    title = name.orEmpty(),
    indexNumber = indexNumber,
    overview = overview?.takeIf { it.isNotBlank() },
    runtime = runTimeTicks?.let(::formatRuntime),
    // An episode's Primary image is its still frame, which is the landscape art we want here.
    imageUrl = (imageTags?.get(ImageType.PRIMARY) ?: imageTags?.get(ImageType.THUMB))?.let { tag ->
        val type = if (imageTags?.containsKey(ImageType.PRIMARY) == true) ImageType.PRIMARY else ImageType.THUMB
        buildImageUrl(serverUrl, id, type, tag, maxHeight = EPISODE_IMAGE_MAX_HEIGHT)
    },
    isPlayed = userData?.played == true,
    progress = userData?.playedPercentage?.let { (it / 100.0).toFloat() }?.takeIf { it > 0f },
)

/** Ticks are 100-nanosecond units. Renders as "2h 15m", or "45m" under an hour. */
internal fun formatRuntime(ticks: Long): String? {
    val totalMinutes = (ticks / TICKS_PER_SECOND / SECONDS_PER_MINUTE).toInt()
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
