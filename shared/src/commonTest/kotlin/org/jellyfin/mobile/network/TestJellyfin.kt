package org.jellyfin.mobile.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jellyfin.mobile.storage.InMemoryPreferencesDataStore
import org.jellyfin.mobile.storage.SessionStore

internal const val TEST_SERVER_URL = "http://jellyfin.test"
internal const val TEST_USER_ID = "user-1"

/** A [MockEngine] that answers every request with [body] as JSON. */
internal fun jsonEngine(body: String) = MockEngine {
    respond(
        content = body,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}

internal fun testSession(): JellyfinSession = JellyfinSession(
    store = SessionStore(InMemoryPreferencesDataStore()),
    scope = CoroutineScope(Dispatchers.Unconfined),
).apply {
    authenticated(
        Session(
            serverUrl = TEST_SERVER_URL,
            accessToken = "token-abc",
            userId = TEST_USER_ID,
            userName = "ben",
        ),
    )
}

internal fun testApi(engine: MockEngine, session: JellyfinSession = testSession()): JellyfinApi =
    JellyfinApi(
        createHttpClient(
            session = session,
            clientInfo = ClientInfo(name = "Test Client", version = "1.2.3"),
            deviceInfo = DeviceInfo(name = "Test Device", id = "device-id"),
            engine = engine,
        ),
        session,
    )
