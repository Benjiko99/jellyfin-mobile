package org.jellyfin.mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json

private const val CONNECT_TIMEOUT_MS = 15_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

/**
 * The single [HttpClient] for the app. Both [JellyfinApi] and the Coil image loader use it, so
 * images are authenticated by the same header as API calls rather than by putting the access token
 * in image URLs.
 *
 * [engine] is only supplied by tests (Ktor's `MockEngine`); in the app the platform engine — OkHttp
 * on Android, Darwin on iOS — is resolved from the classpath.
 */
fun createHttpClient(
    session: JellyfinSession,
    clientInfo: ClientInfo,
    deviceInfo: DeviceInfo,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        // Ktor defaults this to false, which would hand a 401's error body to the JSON decoder and
        // surface an authentication problem as an unrelated deserialization failure.
        expectSuccess = true

        install(ContentNegotiation) {
            json(JellyfinJson)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        defaultRequest {
            // Evaluated per request, so the header picks up the access token as soon as login completes.
            header(HttpHeaders.Authorization, buildAuthorizationHeader(clientInfo, deviceInfo, session.accessToken))
            accept(ContentType.Application.Json)
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, _ ->
                val status = (cause as? ResponseException)?.response?.status
                if (status == HttpStatusCode.Unauthorized) throw SessionExpiredException(cause)
            }
        }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}
