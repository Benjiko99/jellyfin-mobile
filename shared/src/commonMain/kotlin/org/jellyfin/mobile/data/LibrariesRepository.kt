package org.jellyfin.mobile.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.domain.LibraryView
import org.jellyfin.mobile.network.JellyfinApi

/**
 * The user's libraries, for the navigation drawer.
 *
 * `/UserViews` in the server's own order, which is the order the user arranged their libraries in
 * on this server. Not filtered by type: a music or photo library still belongs in the drawer even
 * though [LibraryKind.Other] has no tailored browse view for it yet.
 *
 * Like [MenuLinksRepository] this answers with an empty list rather than failing. The drawer is
 * reachable from a home screen that has already reported any real connection problem, and a second
 * error message inside it would be describing the same outage twice.
 */
class LibrariesRepository(
    private val api: JellyfinApi,
) {
    suspend fun loadLibraries(): List<LibraryView> {
        val views = runCatching { api.userViews() }

        currentCoroutineContext().ensureActive()

        return views.getOrNull()?.items.orEmpty().mapNotNull { dto ->
            val name = dto.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            LibraryView(
                id = dto.id,
                name = name,
                kind = LibraryKind.from(dto.collectionType),
            )
        }
    }
}
