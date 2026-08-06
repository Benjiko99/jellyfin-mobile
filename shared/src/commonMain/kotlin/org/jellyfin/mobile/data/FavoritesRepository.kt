package org.jellyfin.mobile.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.mobile.domain.CardShape
import org.jellyfin.mobile.domain.HomeSection
import org.jellyfin.mobile.domain.ItemKind
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult

/**
 * Rows are horizontal scrollers, so this is a display cap rather than a page size — a user with
 * more favourites than this sees the first [FAVORITES_LIMIT] by name. Paging them would need a
 * "More" screen, which nothing asks for yet.
 */
private const val FAVORITES_LIMIT = 60

/** Alphabetical. Favourites have no natural order the way "recently added" does. */
private val SORT_BY_NAME = listOf("SortName")

/**
 * The Favorites tab: one row per kind of thing the user has starred.
 *
 * Deliberately produces the same [HomeSection] list the home tab does, so both render through the
 * same row and card composables.
 *
 * Like [HomeRepository], rows are fetched concurrently and fail independently — a server that
 * errors on one type still shows the rest — but if *every* query fails the first error propagates
 * so the UI shows a real error rather than an empty tab.
 */
class FavoritesRepository(
    private val api: JellyfinApi,
    private val session: JellyfinSession,
) {
    suspend fun loadFavorites(): List<HomeSection> = coroutineScope {
        val serverUrl = requireNotNull(session.serverUrl) { "No server configured" }

        val rows = listOf(
            FavoriteRow("favorite-movies", "Movies", ItemKind.Movie, CardShape.Poster),
            FavoriteRow("favorite-series", "TV Shows", ItemKind.Series, CardShape.Poster),
            FavoriteRow("favorite-episodes", "Episodes", ItemKind.Episode, CardShape.Thumb),
            FavoriteRow("favorite-collections", "Collections", ItemKind.BoxSet, CardShape.Poster),
        )

        val itemQueries: List<Deferred<Result<BaseItemDtoQueryResult>>> = rows.map { row ->
            async {
                runCatching {
                    api.items(
                        includeItemTypes = listOfNotNull(row.kind.wireType),
                        isFavorite = true,
                        limit = FAVORITES_LIMIT,
                        sortBy = SORT_BY_NAME,
                    )
                }
            }
        }
        val peopleQuery = async {
            runCatching { api.persons(isFavorite = true, limit = FAVORITES_LIMIT) }
        }

        val itemResults = itemQueries.map { it.await() }
        val people = peopleQuery.await()

        if (itemResults.all { it.isFailure } && people.isFailure) {
            throw itemResults.firstNotNullOf { it.exceptionOrNull() }
        }

        buildList {
            rows.zip(itemResults).forEach { (row, result) ->
                val items = result.getOrNull()?.items.orEmpty()
                if (items.isEmpty()) return@forEach
                add(
                    HomeSection(
                        id = row.id,
                        title = row.title,
                        items = items.map { it.toMediaItem(serverUrl, row.shape) },
                        cardShape = row.shape,
                    ),
                )
            }

            people.getOrNull()?.items.orEmpty().takeIf { it.isNotEmpty() }?.let { items ->
                add(
                    HomeSection(
                        id = "favorite-people",
                        title = "People",
                        // `/Persons` does not always set `Type` on its results, so the kind is
                        // asserted here rather than inferred — getting it wrong would send a tap to
                        // the item detail screen instead of the person's page.
                        items = items.map {
                            it.toMediaItem(serverUrl, CardShape.Poster).copy(kind = ItemKind.Person)
                        },
                        cardShape = CardShape.Poster,
                    ),
                )
            }
        }
    }
}

private data class FavoriteRow(
    val id: String,
    val title: String,
    val kind: ItemKind,
    val shape: CardShape,
)
