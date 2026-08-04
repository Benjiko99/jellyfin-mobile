package org.jellyfin.mobile.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession

private const val RESUME_LIMIT = 12
private const val NEXT_UP_LIMIT = 24
private const val LATEST_LIMIT = 16

/** `CollectionType` values that get a "Recently Added" row. */
private const val COLLECTION_MOVIES = "movies"
private const val COLLECTION_TV = "tvshows"

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
        val serverUrl = requireNotNull(session.serverUrl) { "No server configured" }

        val resumeDeferred = async { runCatching { api.resumeItems(limit = RESUME_LIMIT) } }
        val nextUpDeferred = async { runCatching { api.nextUp(limit = NEXT_UP_LIMIT) } }
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
                        limit = LATEST_LIMIT,
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
            resume.getOrNull()?.items.orEmpty().takeIf { it.isNotEmpty() }?.let { items ->
                add(
                    HomeSection(
                        id = "resume",
                        title = "Continue Watching",
                        items = items.map { it.toMediaItem(serverUrl, CardShape.Thumb) },
                        cardShape = CardShape.Thumb,
                    ),
                )
            }

            nextUp.getOrNull()?.items.orEmpty().takeIf { it.isNotEmpty() }?.let { items ->
                add(
                    HomeSection(
                        id = "nextup",
                        title = "Next Up",
                        items = items.map { it.toMediaItem(serverUrl, CardShape.Thumb) },
                        cardShape = CardShape.Thumb,
                    ),
                )
            }

            latest.forEach { (library, result) ->
                val items = result.getOrNull().orEmpty()
                if (items.isEmpty()) return@forEach
                add(
                    HomeSection(
                        id = "latest-${library.id}",
                        title = "Recently Added in ${library.name.orEmpty()}",
                        items = items.map { it.toMediaItem(serverUrl, CardShape.Poster) },
                        cardShape = CardShape.Poster,
                    ),
                )
            }
        }
    }
}
