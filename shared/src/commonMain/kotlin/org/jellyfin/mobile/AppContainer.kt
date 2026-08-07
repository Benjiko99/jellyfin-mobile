package org.jellyfin.mobile

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.data.DetailRepository
import org.jellyfin.mobile.data.FavoritesRepository
import org.jellyfin.mobile.data.HomeRepository
import org.jellyfin.mobile.data.LibrariesRepository
import org.jellyfin.mobile.data.LibraryRepository
import org.jellyfin.mobile.data.LibraryRowsRepository
import org.jellyfin.mobile.data.MenuLinksRepository
import org.jellyfin.mobile.data.PersonRepository
import org.jellyfin.mobile.data.PlaybackRepository
import org.jellyfin.mobile.data.SearchRepository
import org.jellyfin.mobile.data.SectionRepository
import org.jellyfin.mobile.network.ClientInfo
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.StreamAuthorizer
import org.jellyfin.mobile.network.createHttpClient
import org.jellyfin.mobile.network.platformDeviceInfo
import org.jellyfin.mobile.player.platformDecoderCapabilities
import org.jellyfin.mobile.storage.SessionStore
import org.jellyfin.mobile.storage.createSessionDataStore

/**
 * Hand-rolled dependency container.
 *
 * Deliberately not a DI framework yet — there are a handful of objects and no scoping
 * requirements. Swap for Koin when the graph justifies it (PLAN.md lists Koin 4 as the intended
 * choice).
 */
class AppContainer(sessionFilePath: String) {
    /** Outlives any screen; used for writes that must finish even if the UI goes away. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val sessionStore = SessionStore(createSessionDataStore(sessionFilePath))

    val session: JellyfinSession = JellyfinSession(sessionStore, applicationScope)

    private val clientInfo = ClientInfo()
    private val deviceInfo = platformDeviceInfo()

    val httpClient: HttpClient = createHttpClient(
        session = session,
        clientInfo = clientInfo,
        deviceInfo = deviceInfo,
    )

    val api: JellyfinApi = JellyfinApi(httpClient, session, deviceInfo)

    /** Lets the playback engine authenticate stream requests without leaking the token off-server. */
    val streamAuthorizer: StreamAuthorizer = StreamAuthorizer(session, clientInfo, deviceInfo)

    val homeRepository: HomeRepository = HomeRepository(api, session)

    val favoritesRepository: FavoritesRepository = FavoritesRepository(api, session)

    val searchRepository: SearchRepository = SearchRepository(api, session)

    /** The navigation drawer's server-configured links. */
    val menuLinksRepository: MenuLinksRepository = MenuLinksRepository(api)

    /** The navigation drawer's "Media" section. */
    val librariesRepository: LibrariesRepository = LibrariesRepository(api)

    /** Backs the library browse screen behind each of those. */
    val libraryRepository: LibraryRepository = LibraryRepository(api, session)

    /** Its tabs that group rather than list: suggestions, air dates, genres, networks. */
    val libraryRowsRepository: LibraryRowsRepository = LibraryRowsRepository(api, session)

    /** Backs the paged "More" screen behind each row. */
    val sectionRepository: SectionRepository = SectionRepository(api, session)

    val detailRepository: DetailRepository = DetailRepository(api, session)

    val personRepository: PersonRepository = PersonRepository(api, session)

    /**
     * Lazy because constructing it enumerates every decoder on the device — worth doing once, but
     * not on the startup path for a user who may never press play this session.
     */
    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(api, platformDecoderCapabilities(), clientInfo.name)
    }
}
