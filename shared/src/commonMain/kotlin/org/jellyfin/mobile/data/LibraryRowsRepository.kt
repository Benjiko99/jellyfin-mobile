package org.jellyfin.mobile.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.EpisodeArtwork
import org.jellyfin.mobile.domain.LibraryRow
import org.jellyfin.mobile.domain.LibraryRowTarget
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.RecommendationDto
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.date_day_month_year
import org.jellyfin.mobile.resources.date_to_be_announced
import org.jellyfin.mobile.resources.month_april
import org.jellyfin.mobile.resources.month_august
import org.jellyfin.mobile.resources.month_december
import org.jellyfin.mobile.resources.month_february
import org.jellyfin.mobile.resources.month_january
import org.jellyfin.mobile.resources.month_july
import org.jellyfin.mobile.resources.month_june
import org.jellyfin.mobile.resources.month_march
import org.jellyfin.mobile.resources.month_may
import org.jellyfin.mobile.resources.month_november
import org.jellyfin.mobile.resources.month_october
import org.jellyfin.mobile.resources.month_september
import org.jellyfin.mobile.resources.recommendation_actors_you_like
import org.jellyfin.mobile.resources.recommendation_because_you_liked
import org.jellyfin.mobile.resources.recommendation_because_you_watched
import org.jellyfin.mobile.resources.recommendation_directed_by
import org.jellyfin.mobile.resources.recommendation_directors_you_like
import org.jellyfin.mobile.resources.recommendation_more_like_liked
import org.jellyfin.mobile.resources.recommendation_more_like_watched
import org.jellyfin.mobile.resources.recommendation_same_cast
import org.jellyfin.mobile.resources.recommendation_same_director
import org.jellyfin.mobile.resources.recommendation_starring
import org.jellyfin.mobile.resources.recommendation_suggested_by
import org.jellyfin.mobile.resources.recommendation_suggested_for_you
import org.jellyfin.mobile.resources.section_continue_watching
import org.jellyfin.mobile.resources.section_next_up
import org.jellyfin.mobile.resources.section_recently_added
import org.jetbrains.compose.resources.StringResource

/** Genres and networks per page. Each costs a request for its preview row, so pages stay small. */
const val LIBRARY_ROWS_PAGE_SIZE = 12

/** Items shown in one row before it runs off the side of the screen. */
private const val ROW_PREVIEW_LIMIT = 12

/** Upcoming episodes per page — days, not rows, so this is a good few rows' worth. */
private const val UPCOMING_PAGE_SIZE = 40

data class LibraryRowsPage(
    val rows: List<LibraryRow>,
    val endReached: Boolean,
)

/**
 * The tabs that group rather than list: Suggestions, Upcoming, Genres and TV Networks.
 *
 * Each is a screenful of horizontal rows, but no two are built the same way, which is exactly why
 * they are not tabs of [LibraryRepository]'s grid. Suggestions fans out over three unrelated
 * endpoints, Upcoming groups one endpoint's results by air date, and Genres and Networks page a
 * list and then fetch a preview for every entry on the page.
 */
class LibraryRowsRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun loadRows(
        libraryId: String,
        tab: LibraryTab,
        startIndex: Int,
    ): LibraryRowsPage = when (tab) {
        LibraryTab.SuggestionsMovies -> movieSuggestions(libraryId)
        LibraryTab.SuggestionsShows -> showSuggestions(libraryId)
        LibraryTab.Upcoming -> upcoming(libraryId, startIndex)
        LibraryTab.MovieGenres, LibraryTab.ShowGenres -> genres(libraryId, tab, startIndex)
        LibraryTab.Networks -> networks(libraryId, startIndex)
        else -> error("${tab.name} is a grid, not a rows tab")
    }

    /**
     * Continue Watching, Recently Added, then the server's own recommendations.
     *
     * Rows fail independently, like the home screen's: a server that will not answer
     * `/Movies/Recommendations` — it is built from viewing history, so a fresh account gets an
     * empty answer rather than an error — still shows the two rows either side of it.
     */
    private suspend fun movieSuggestions(libraryId: String): LibraryRowsPage = coroutineScope {
        val serverUrl = session.requireServerUrl()

        val resume = async {
            runCatching { api.resumeItems(limit = ROW_PREVIEW_LIMIT, parentId = libraryId).items }
        }
        val latest = async {
            runCatching {
                api.latestItems(parentId = libraryId, limit = ROW_PREVIEW_LIMIT, groupItems = false)
            }
        }
        val recommendations = async {
            runCatching { api.movieRecommendations(parentId = libraryId, itemLimit = ROW_PREVIEW_LIMIT) }
        }

        val rows = buildList {
            row(
                id = "suggestions-resume",
                title = UiText.Resource(Res.string.section_continue_watching),
                items = resume.await().items(),
                shape = CardShape.Thumb,
                serverUrl = serverUrl,
                // Same row as the home screen's, and drawn the same way. A movie library holds no
                // episodes for this to apply to, but a mislabelled library might.
                artwork = EpisodeArtwork.Series,
            )?.let(::add)
            row(
                id = "suggestions-latest",
                title = UiText.Resource(Res.string.section_recently_added),
                items = latest.await().items(),
                shape = CardShape.Poster,
                serverUrl = serverUrl,
            )?.let(::add)

            recommendations.await().getOrNull().orEmpty().forEachIndexed { index, category ->
                row(
                    id = category.categoryId ?: "recommendation-$index",
                    title = category.title(),
                    items = category.items,
                    shape = CardShape.Poster,
                    serverUrl = serverUrl,
                )?.let(::add)
            }
        }

        // Not paged: every row here comes from an endpoint with no meaningful second page.
        LibraryRowsPage(rows = rows, endReached = true)
    }

    /** A TV library has no recommendations endpoint, so Next Up stands in for one. */
    private suspend fun showSuggestions(libraryId: String): LibraryRowsPage = coroutineScope {
        val serverUrl = session.requireServerUrl()

        val resume = async {
            runCatching { api.resumeItems(limit = ROW_PREVIEW_LIMIT, parentId = libraryId).items }
        }
        val nextUp = async {
            runCatching { api.nextUp(limit = ROW_PREVIEW_LIMIT, parentId = libraryId).items }
        }
        val latest = async {
            runCatching {
                api.latestItems(parentId = libraryId, limit = ROW_PREVIEW_LIMIT, groupItems = true)
            }
        }

        val rows = buildList {
            // Every row here is a row of shows: two rows of episodes standing in for the series
            // they belong to, and a "Recently Added" built with `groupItems`, which returns the
            // episode rather than the series it grouped it under.
            row(
                id = "suggestions-resume",
                title = UiText.Resource(Res.string.section_continue_watching),
                items = resume.await().items(),
                shape = CardShape.Thumb,
                serverUrl = serverUrl,
                artwork = EpisodeArtwork.Series,
            )?.let(::add)
            row(
                id = "suggestions-next-up",
                title = UiText.Resource(Res.string.section_next_up),
                items = nextUp.await().items(),
                shape = CardShape.Thumb,
                serverUrl = serverUrl,
                artwork = EpisodeArtwork.Series,
            )?.let(::add)
            row(
                id = "suggestions-latest",
                title = UiText.Resource(Res.string.section_recently_added),
                items = latest.await().items(),
                shape = CardShape.Poster,
                serverUrl = serverUrl,
                artwork = EpisodeArtwork.Series,
            )?.let(::add)
        }

        LibraryRowsPage(rows = rows, endReached = true)
    }

    /**
     * Episodes that have not aired, one row per day.
     *
     * `/Shows/Upcoming` returns a flat list in air-date order, so the grouping is ours to do. Paging
     * is by episode rather than by day, which means a day straddling a page boundary arrives as two
     * rows with the same heading — [LibraryRow.id] carries the page so the two do not collide, and
     * the view model does not merge them: doing so would mean holding a page back until the day
     * after it started was known to be complete.
     */
    private suspend fun upcoming(libraryId: String, startIndex: Int): LibraryRowsPage {
        val serverUrl = session.requireServerUrl()
        val result = api.upcoming(
            parentId = libraryId,
            startIndex = startIndex,
            limit = UPCOMING_PAGE_SIZE,
        )

        val rows = result.items
            .groupBy { it.premiereDate?.take(DATE_LENGTH) }
            .map { (date, episodes) ->
                LibraryRow(
                    id = "upcoming-$startIndex-${date ?: "unknown"}",
                    title = date?.let(::formatAirDate)
                        ?: UiText.Resource(Res.string.date_to_be_announced),
                    // An episode that has not aired has no still frame to show, and the card is
                    // answering "which show has something on Thursday" anyway.
                    items = episodes.map {
                        it.toMediaItem(serverUrl, CardShape.Thumb, EpisodeArtwork.Series)
                    },
                    cardShape = CardShape.Thumb,
                )
            }

        return LibraryRowsPage(rows = rows, endReached = result.items.size < UPCOMING_PAGE_SIZE)
    }

    private suspend fun genres(libraryId: String, tab: LibraryTab, startIndex: Int): LibraryRowsPage {
        val result = api.genres(
            parentId = libraryId,
            includeItemTypes = listOfNotNull(tab.itemKind?.wireType),
            startIndex = startIndex,
            limit = LIBRARY_ROWS_PAGE_SIZE,
        )

        return previewRows(
            entries = result.items,
            endReached = result.items.size < LIBRARY_ROWS_PAGE_SIZE,
            shape = tab.cardShape,
            idPrefix = "genre",
            target = { LibraryRowTarget.Genre(it.name.orEmpty()) },
        ) { genre ->
            api.items(
                parentId = libraryId,
                includeItemTypes = listOfNotNull(tab.itemKind?.wireType),
                genres = listOf(genre.name.orEmpty()),
                sortBy = SORT_BY_NAME,
                limit = ROW_PREVIEW_LIMIT,
            ).items
        }
    }

    private suspend fun networks(libraryId: String, startIndex: Int): LibraryRowsPage {
        val itemType = listOfNotNull(LibraryTab.Networks.itemKind?.wireType)
        val result = api.studios(
            parentId = libraryId,
            includeItemTypes = itemType,
            startIndex = startIndex,
            limit = LIBRARY_ROWS_PAGE_SIZE,
        )

        return previewRows(
            entries = result.items,
            endReached = result.items.size < LIBRARY_ROWS_PAGE_SIZE,
            shape = LibraryTab.Networks.cardShape,
            idPrefix = "studio",
            target = { LibraryRowTarget.Studio(it.id, it.name.orEmpty()) },
        ) { studio ->
            api.items(
                parentId = libraryId,
                includeItemTypes = itemType,
                studioIds = listOf(studio.id),
                sortBy = SORT_BY_NAME,
                limit = ROW_PREVIEW_LIMIT,
            ).items
        }
    }

    /**
     * Turns a page of genres or studios into rows, fetching each one's preview concurrently.
     *
     * One request per entry, which is why a page is [LIBRARY_ROWS_PAGE_SIZE] and not the whole
     * list. A preview that fails drops its row rather than the page: an empty row would claim the
     * genre holds nothing, which is a different and wrong statement.
     */
    private suspend fun previewRows(
        entries: List<BaseItemDto>,
        endReached: Boolean,
        shape: CardShape,
        idPrefix: String,
        target: (BaseItemDto) -> LibraryRowTarget,
        preview: suspend (BaseItemDto) -> List<BaseItemDto>,
    ): LibraryRowsPage = coroutineScope {
        val serverUrl = session.requireServerUrl()

        val previews: List<Deferred<Result<List<BaseItemDto>>>> = entries.map { entry ->
            async { runCatching { preview(entry) } }
        }

        val rows = entries.zip(previews.map { it.await() }).mapNotNull { (entry, items) ->
            val name = entry.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            row(
                id = "$idPrefix-${entry.id}",
                // A genre or a studio is named by the server, not by us.
                title = UiText.Raw(name),
                items = items.getOrNull().orEmpty(),
                shape = shape,
                serverUrl = serverUrl,
                target = target(entry),
            )
        }

        LibraryRowsPage(rows = rows, endReached = endReached)
    }

    /** A row with nothing in it is not a row; the caller drops it. */
    @Suppress("LongParameterList")
    private fun row(
        id: String,
        title: UiText,
        items: List<BaseItemDto>,
        shape: CardShape,
        serverUrl: String,
        target: LibraryRowTarget? = null,
        artwork: EpisodeArtwork = EpisodeArtwork.Own,
    ): LibraryRow? = items
        .takeIf { it.isNotEmpty() }
        ?.let {
            LibraryRow(
                id = id,
                title = title,
                items = it.map { dto -> dto.toMediaItem(serverUrl, shape, artwork) },
                cardShape = shape,
                target = target,
            )
        }
}

private fun Result<List<BaseItemDto>>.items(): List<BaseItemDto> = getOrNull().orEmpty()

/** `2026-08-14T00:00:00.0000000Z` — the date is the first ten characters. */
private const val DATE_LENGTH = 10

private val MONTHS: List<StringResource> = listOf(
    Res.string.month_january, Res.string.month_february, Res.string.month_march,
    Res.string.month_april, Res.string.month_may, Res.string.month_june,
    Res.string.month_july, Res.string.month_august, Res.string.month_september,
    Res.string.month_october, Res.string.month_november, Res.string.month_december,
)

/**
 * "14 August 2026" from an ISO date.
 *
 * Formatted by hand rather than with a date library: this is the only date the app renders as a
 * heading, and `kotlinx-datetime` would be a dependency on every target for one string. The day,
 * month and year go into a pattern of their own so a locale that orders them differently can say
 * so. Anything that does not parse is passed through as the server sent it — a heading of raw ISO
 * is poor, but it is an answer rather than a guess.
 */
private fun formatAirDate(isoDate: String): UiText {
    val parts = isoDate.split('-')
    if (parts.size != 3) return UiText.Raw(isoDate)
    val year = parts[0].toIntOrNull() ?: return UiText.Raw(isoDate)
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..MONTHS.size } ?: return UiText.Raw(isoDate)
    val day = parts[2].toIntOrNull() ?: return UiText.Raw(isoDate)
    return UiText.Resource(
        Res.string.date_day_month_year,
        listOf(day.toString(), UiText.Resource(MONTHS[month - 1]), year.toString()),
    )
}

/**
 * Titles for `/Movies/Recommendations`, which sends the reason and the film but not the heading.
 *
 * Every reason has two wordings: one naming the film it was drawn from, and one for when the server
 * sends no film with it — "Because you watched" on its own is not a heading.
 */
private fun RecommendationDto.title(): UiText {
    val (named, unnamed) = when (recommendationType) {
        "SimilarToRecentlyPlayed" ->
            Res.string.recommendation_because_you_watched to Res.string.recommendation_more_like_watched

        "SimilarToLikedItem" ->
            Res.string.recommendation_because_you_liked to Res.string.recommendation_more_like_liked

        "HasDirectorFromRecentlyPlayed" ->
            Res.string.recommendation_directed_by to Res.string.recommendation_same_director

        "HasLikedDirector" ->
            Res.string.recommendation_directed_by to Res.string.recommendation_directors_you_like

        "HasActorFromRecentlyPlayed" ->
            Res.string.recommendation_starring to Res.string.recommendation_same_cast

        "HasLikedActor" ->
            Res.string.recommendation_starring to Res.string.recommendation_actors_you_like

        // A recommendation type we have no wording for is still a usable row, so it keeps the film
        // it was built from rather than being dropped.
        else -> Res.string.recommendation_suggested_by to Res.string.recommendation_suggested_for_you
    }
    return baselineItemName
        ?.let { UiText.Resource(named, listOf(it)) }
        ?: UiText.Resource(unnamed)
}
