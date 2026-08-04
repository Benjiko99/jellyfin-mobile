package org.jellyfin.mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.jellyfin.mobile.network.dto.AuthenticateUserByName
import org.jellyfin.mobile.network.dto.AuthenticationResult
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult
import org.jellyfin.mobile.network.dto.PublicSystemInfo

/**
 * Typed access to the endpoints we use. Paths and parameter names come from
 * `api-spec/jellyfin-openapi-12.0.0.json` — see AGENTS.md before adding to this class.
 *
 * Query parameters are camelCase (response bodies are PascalCase); array parameters are
 * comma-separated.
 */
class JellyfinApi(
    private val http: HttpClient,
    private val session: JellyfinSession,
) {
    private fun serverUrl(): String =
        requireNotNull(session.serverUrl) { "No server configured" }.trimEnd('/')

    private fun userId(): String =
        requireNotNull(session.userId) { "Not authenticated" }

    private fun HttpRequestBuilder.path(path: String) = url("${serverUrl()}$path")

    /** Comma-joins array query parameters, omitting the parameter entirely when the list is empty. */
    private fun HttpRequestBuilder.listParameter(name: String, values: List<String>) {
        if (values.isNotEmpty()) parameter(name, values.joinToString(","))
    }

    /** Unauthenticated — used to validate a server URL before login. */
    suspend fun publicSystemInfo(serverUrl: String): PublicSystemInfo =
        http.get { url("${serverUrl.trimEnd('/')}/System/Info/Public") }.body()

    suspend fun authenticateByName(
        serverUrl: String,
        username: String,
        password: String,
    ): AuthenticationResult {
        // The login request itself needs a server URL before a Session exists.
        session.pendingServerUrl = serverUrl.trimEnd('/')
        return http.post {
            path("/Users/AuthenticateByName")
            contentType(ContentType.Application.Json)
            setBody(AuthenticateUserByName(username = username, pw = password))
        }.body()
    }

    /**
     * "Continue Watching" — partially played items.
     *
     * `GET /UserItems/Resume`. The pre-10.9 `/Users/{userId}/Items/Resume` route no longer exists;
     * `userId` is now a query parameter.
     */
    suspend fun resumeItems(
        limit: Int,
        mediaTypes: List<String> = listOf("Video"),
        fields: List<String> = DEFAULT_FIELDS,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB),
    ): BaseItemDtoQueryResult = http.get {
        path("/UserItems/Resume")
        parameter("userId", userId())
        parameter("limit", limit)
        parameter("imageTypeLimit", 1)
        parameter("enableTotalRecordCount", false)
        listParameter("mediaTypes", mediaTypes)
        listParameter("fields", fields)
        listParameter("enableImageTypes", enableImageTypes)
    }.body()

    /**
     * "Next Up" — the next unwatched episode of each series in progress.
     *
     * [enableResumable] is `false` so partially watched episodes stay in Continue Watching instead
     * of appearing in both rows, matching the web client.
     */
    suspend fun nextUp(
        limit: Int,
        enableResumable: Boolean = false,
        fields: List<String> = DEFAULT_FIELDS,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB),
    ): BaseItemDtoQueryResult = http.get {
        path("/Shows/NextUp")
        parameter("userId", userId())
        parameter("limit", limit)
        parameter("imageTypeLimit", 1)
        parameter("enableTotalRecordCount", false)
        parameter("enableResumable", enableResumable)
        listParameter("fields", fields)
        listParameter("enableImageTypes", enableImageTypes)
    }.body()

    /** The user's libraries, used to build one "Recently Added" row per library. */
    suspend fun userViews(): BaseItemDtoQueryResult = http.get {
        path("/UserViews")
        parameter("userId", userId())
    }.body()

    /**
     * "Recently Added in <library>".
     *
     * Unlike every other list endpoint here, `GET /Items/Latest` returns a **bare JSON array**
     * rather than a `BaseItemDtoQueryResult`.
     *
     * [groupItems] rolls episodes up into their series, which is what you want for a TV library
     * and not what you want for movies.
     */
    suspend fun latestItems(
        parentId: String,
        limit: Int,
        groupItems: Boolean,
        fields: List<String> = DEFAULT_FIELDS,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB),
    ): List<BaseItemDto> = http.get {
        path("/Items/Latest")
        parameter("userId", userId())
        parameter("parentId", parentId)
        parameter("limit", limit)
        parameter("imageTypeLimit", 1)
        parameter("groupItems", groupItems)
        listParameter("fields", fields)
        listParameter("enableImageTypes", enableImageTypes)
    }.body()

    companion object {
        /** Extra fields to hydrate. Keep this short — each one costs the server work. */
        val DEFAULT_FIELDS = listOf("PrimaryImageAspectRatio", "Overview")
    }
}
