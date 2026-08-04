package org.jellyfin.mobile

import io.ktor.client.HttpClient
import org.jellyfin.mobile.data.HomeRepository
import org.jellyfin.mobile.network.ClientInfo
import org.jellyfin.mobile.network.JellyfinApi
import org.jellyfin.mobile.network.JellyfinSession
import org.jellyfin.mobile.network.createHttpClient
import org.jellyfin.mobile.network.platformDeviceInfo

/**
 * Hand-rolled dependency container.
 *
 * Deliberately not a DI framework yet — there are five objects and no scoping requirements. Swap
 * for Koin when the graph justifies it (PLAN.md lists Koin 4 as the intended choice).
 */
class AppContainer {
    val session: JellyfinSession = JellyfinSession()

    private val clientInfo = ClientInfo()
    private val deviceInfo = platformDeviceInfo()

    val httpClient: HttpClient = createHttpClient(
        session = session,
        clientInfo = clientInfo,
        deviceInfo = deviceInfo,
    )

    val api: JellyfinApi = JellyfinApi(httpClient, session)

    val homeRepository: HomeRepository = HomeRepository(api, session)
}
