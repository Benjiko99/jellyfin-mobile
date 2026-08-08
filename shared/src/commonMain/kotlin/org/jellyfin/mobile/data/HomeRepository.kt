package org.jellyfin.mobile.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.domain.SectionKind
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.dto.BaseItemDto

/**
 * Rows preview this many items; the rest live behind "More".
 *
 * One extra is requested so a full row can be told from a row that happens to hold exactly
 * [SECTION_PREVIEW_LIMIT] — asking the server for a total record count just to answer that
 * yes/no question costs it a full count of every match.
 */
internal const val SECTION_PREVIEW_LIMIT = 10
internal const val PREVIEW_PROBE_LIMIT = SECTION_PREVIEW_LIMIT + 1

/** `CollectionType` values that get a "Recently Added" row. */
internal const val COLLECTION_MOVIES = "movies"
internal const val COLLECTION_TV = "tvshows"

/**
 * Builds the home screen: Continue Watching, Next Up, then one "Recently Added in …" row per
 * movie/TV library.
 *
 * Rows are fetched concurrently and failures are per-row: one library erroring (or a server
 * without a TV library) leaves the rest of the screen intact. If *every* request fails — expired
 * token, server down — the first error propagates so the UI can show a real error instead of a
 * blank screen.
 */
class HomeRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun loadHome(): List<HomeSection> = coroutineScope {
        val serverUrl = session.requireServerUrl()

        val resumeDeferred = async { runCatching { api.resumeItems(limit = PREVIEW_PROBE_LIMIT) } }
        val nextUpDeferred = async { runCatching { api.nextUp(limit = PREVIEW_PROBE_LIMIT) } }
        val viewsDeferred = async { runCatching { api.userViews() } }

        val resume = resumeDeferred.await()
        val nextUp = nextUpDeferred.await()
        val views = viewsDeferred.await()

        // Libraries keep the server's ordering, which is the order the user arranged them in.
        val libraries = views.getOrNull()?.items.orEmpty()
            .filter { it.collectionType == COLLECTION_MOVIES || it.collectionType == COLLECTION_TV }

        val latest = libraries.map { library ->
            async {
                library to runCatching {
                    api.latestItems(
                        parentId = library.id,
                        limit = PREVIEW_PROBE_LIMIT,
                        // Roll episodes up into their series for TV; movies are already top-level.
                        groupItems = library.collectionType == COLLECTION_TV,
                    )
                }
            }
        }.awaitAll()

        val allFailed = resume.isFailure && nextUp.isFailure && views.isFailure
        if (allFailed) {
            throw resume.exceptionOrNull() ?: nextUp.exceptionOrNull() ?: views.exceptionOrNull()!!
        }

        buildList {
            previewSection(
                id = "resume",
                kind = SectionKind.Resume,
                items = resume.getOrNull()?.items.orEmpty(),
                serverUrl = serverUrl,
            )?.let(::add)

            previewSection(
                id = "nextup",
                kind = SectionKind.NextUp,
                items = nextUp.getOrNull()?.items.orEmpty(),
                serverUrl = serverUrl,
            )?.let(::add)

            latest.forEach { (library, result) ->
                previewSection(
                    id = "latest-${library.id}",
                    kind = SectionKind.LatestInLibrary,
                    items = result.getOrNull().orEmpty(),
                    serverUrl = serverUrl,
                    parentId = library.id,
                    // The heading is built from this rather than here, so the "More" screen behind
                    // the row — which is reached through a route carrying the same two values —
                    // cannot end up worded differently.
                    libraryName = library.name.orEmpty(),
                    // Carried so the "More" screen does not have to re-fetch the library to learn
                    // what it holds. `/Items/Latest` groups episodes under their series for TV;
                    // `/Items` does not, so the full list has to constrain the type to match.
                    libraryItemKind = when (library.collectionType) {
                        COLLECTION_TV -> ItemKind.Series
                        else -> ItemKind.Movie
                    },
                )?.let(::add)
            }
        }
    }
}

/**
 * Trims a probe result down to the preview and records whether anything was left over.
 *
 * Returns null for an empty row, which is how a server without a TV library — or a user who has
 * started nothing — ends up with that row absent rather than blank.
 */
internal fun previewSection(
    id: String,
    kind: SectionKind,
    items: List<BaseItemDto>,
    serverUrl: String,
    parentId: String? = null,
    libraryName: String? = null,
    searchTerm: String? = null,
    libraryItemKind: ItemKind? = null,
): HomeSection? {
    if (items.isEmpty()) return null
    return HomeSection(
        id = id,
        items = items.take(SECTION_PREVIEW_LIMIT).map {
            it.toMediaItem(serverUrl, kind.cardShape, kind.episodeArtwork)
        },
        kind = kind,
        parentId = parentId,
        libraryName = libraryName,
        searchTerm = searchTerm,
        libraryItemKind = libraryItemKind,
        hasMore = items.size > SECTION_PREVIEW_LIMIT,
    )
}
