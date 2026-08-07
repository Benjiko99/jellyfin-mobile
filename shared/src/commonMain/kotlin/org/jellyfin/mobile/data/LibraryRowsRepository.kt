package org.jellyfin.mobile.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.LibraryRow
import org.jellyfin.mobile.domain.LibraryRowTarget
import org.jellyfin.mobile.domain.LibraryTab
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.dto.BaseItemDto

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
            row("suggestions-resume", "Continue Watching", resume.await().items(), CardShape.Thumb, serverUrl)
                ?.let(::add)
            row("suggestions-latest", "Recently Added", latest.await().items(), CardShape.Poster, serverUrl)
                ?.let(::add)

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
            row("suggestions-resume", "Continue Watching", resume.await().items(), CardShape.Thumb, serverUrl)
                ?.let(::add)
            row("suggestions-next-up", "Next Up", nextUp.await().items(), CardShape.Thumb, serverUrl)
                ?.let(::add)
            row("suggestions-latest", "Recently Added", latest.await().items(), CardShape.Poster, serverUrl)
                ?.let(::add)
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
                    title = date?.let(::formatAirDate) ?: "Date to be announced",
                    items = episodes.map { it.toMediaItem(serverUrl, CardShape.Thumb) },
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
                title = name,
                items = items.getOrNull().orEmpty(),
                shape = shape,
                serverUrl = serverUrl,
                target = target(entry),
            )
        }

        LibraryRowsPage(rows = rows, endReached = endReached)
    }

    /** A row with nothing in it is not a row; the caller drops it. */
    private fun row(
        id: String,
        title: String,
        items: List<BaseItemDto>,
        shape: CardShape,
        serverUrl: String,
        target: LibraryRowTarget? = null,
    ): LibraryRow? = items
        .takeIf { it.isNotEmpty() }
        ?.let {
            LibraryRow(
                id = id,
                title = title,
                items = it.map { dto -> dto.toMediaItem(serverUrl, shape) },
                cardShape = shape,
                target = target,
            )
        }
}

private fun Result<List<BaseItemDto>>.items(): List<BaseItemDto> = getOrNull().orEmpty()

/** `2026-08-14T00:00:00.0000000Z` — the date is the first ten characters. */
private const val DATE_LENGTH = 10

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * "14 August 2026" from an ISO date.
 *
 * Formatted by hand rather than with a date library: this is the only date the app renders as a
 * heading, and `kotlinx-datetime` would be a dependency on every target for one string. Anything
 * that does not parse is passed through — a heading of raw ISO is poor, but it is the server's
 * answer rather than a guess.
 */
private fun formatAirDate(isoDate: String): String {
    val parts = isoDate.split('-')
    if (parts.size != 3) return isoDate
    val year = parts[0].toIntOrNull() ?: return isoDate
    val month = parts[1].toIntOrNull()?.takeIf { it in 1..MONTHS.size } ?: return isoDate
    val day = parts[2].toIntOrNull() ?: return isoDate
    return "$day ${MONTHS[month - 1]} $year"
}

/** Titles for `/Movies/Recommendations`, which sends the reason and the film but not the heading. */
private fun org.jellyfin.mobile.network.dto.RecommendationDto.title(): String {
    val baseline = baselineItemName
    return when (recommendationType) {
        "SimilarToRecentlyPlayed" -> baseline?.let { "Because you watched $it" } ?: "More like what you watched"
        "SimilarToLikedItem" -> baseline?.let { "Because you liked $it" } ?: "More like what you liked"
        "HasDirectorFromRecentlyPlayed" -> baseline?.let { "Directed by $it" } ?: "From the same director"
        "HasLikedDirector" -> baseline?.let { "Directed by $it" } ?: "Directors you like"
        "HasActorFromRecentlyPlayed" -> baseline?.let { "Starring $it" } ?: "With the same cast"
        "HasLikedActor" -> baseline?.let { "Starring $it" } ?: "Actors you like"
        // A recommendation type we have no wording for is still a usable row, so it keeps the film
        // it was built from rather than being dropped.
        else -> baseline?.let { "Suggested by $it" } ?: "Suggested for you"
    }
}
