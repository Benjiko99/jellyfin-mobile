package org.jellyfin.mobile.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.jellyfin.mobile.network.dto.AuthenticateUserByName
import org.jellyfin.mobile.network.dto.AuthenticationResult
import org.jellyfin.mobile.network.dto.BaseItemDto
import org.jellyfin.mobile.network.dto.BaseItemDtoQueryResult
import org.jellyfin.mobile.network.dto.DeviceProfile
import org.jellyfin.mobile.network.dto.PlaybackInfoDto
import org.jellyfin.mobile.network.dto.PlaybackInfoResponse
import org.jellyfin.mobile.network.dto.PlaybackProgressInfo
import org.jellyfin.mobile.network.dto.PlaybackStopInfo
import org.jellyfin.mobile.network.dto.PublicSystemInfo
import org.jellyfin.mobile.network.dto.UserItemDataDto

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
    private val deviceInfo: DeviceInfo = platformDeviceInfo(),
) {
    private fun serverUrl(): String =
        session.requireServerUrl()

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
        startIndex: Int? = null,
        enableTotalRecordCount: Boolean = false,
        mediaTypes: List<String> = listOf("Video"),
        fields: List<String> = DEFAULT_FIELDS,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB),
    ): BaseItemDtoQueryResult = http.get {
        path("/UserItems/Resume")
        parameter("userId", userId())
        parameter("limit", limit)
        parameter("imageTypeLimit", 1)
        parameter("enableTotalRecordCount", enableTotalRecordCount)
        if (startIndex != null) parameter("startIndex", startIndex)
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
        startIndex: Int? = null,
        enableTotalRecordCount: Boolean = false,
        enableResumable: Boolean = false,
        fields: List<String> = DEFAULT_FIELDS,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY, ImageType.BACKDROP, ImageType.THUMB),
    ): BaseItemDtoQueryResult = http.get {
        path("/Shows/NextUp")
        parameter("userId", userId())
        parameter("limit", limit)
        parameter("imageTypeLimit", 1)
        parameter("enableTotalRecordCount", enableTotalRecordCount)
        parameter("enableResumable", enableResumable)
        if (startIndex != null) parameter("startIndex", startIndex)
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

    /**
     * Full details for one item. Unlike the list endpoints this takes no `fields` parameter — the
     * server returns everything, including people, ratings, genres and trailer URLs.
     */
    suspend fun item(itemId: String): BaseItemDto = http.get {
        path("/Items/$itemId")
        parameter("userId", userId())
    }.body()

    /**
     * The general item query.
     *
     * Used for a person's filmography via [personIds]. Note there is also a `GET /Persons/{name}`
     * endpoint, but its path parameter is the person's *name*, so it breaks on duplicates and on
     * names containing path characters — people are items, so [item] with the person's id is the
     * stable way to fetch one.
     */
    @Suppress("LongParameterList")
    suspend fun items(
        personIds: List<String> = emptyList(),
        includeItemTypes: List<String> = emptyList(),
        /** Restricts the query to one library or folder. */
        parentId: String? = null,
        recursive: Boolean = true,
        sortBy: List<String> = emptyList(),
        sortOrder: List<String> = emptyList(),
        startIndex: Int? = null,
        limit: Int? = null,
        /** Null leaves favourites out of the query entirely rather than filtering on `false`. */
        isFavorite: Boolean? = null,
        /** Costs the server a full count, so only ask when the UI actually shows a total. */
        enableTotalRecordCount: Boolean = false,
        fields: List<String> = DEFAULT_FIELDS,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY, ImageType.THUMB),
    ): BaseItemDtoQueryResult = http.get {
        path("/Items")
        parameter("userId", userId())
        parameter("recursive", recursive)
        parameter("imageTypeLimit", 1)
        parameter("enableTotalRecordCount", enableTotalRecordCount)
        if (startIndex != null) parameter("startIndex", startIndex)
        if (limit != null) parameter("limit", limit)
        if (isFavorite != null) parameter("isFavorite", isFavorite)
        if (parentId != null) parameter("parentId", parentId)
        listParameter("personIds", personIds)
        listParameter("includeItemTypes", includeItemTypes)
        listParameter("sortBy", sortBy)
        listParameter("sortOrder", sortOrder)
        listParameter("fields", fields)
        listParameter("enableImageTypes", enableImageTypes)
    }.body()

    /**
     * People, which [items] cannot return.
     *
     * People are `BaseItemKind.Person` but do not live inside a library folder, so a recursive
     * `/Items` query never reaches them however it is filtered. `/Persons` is the only route that
     * does, and it carries its own `isFavorite` filter.
     */
    suspend fun persons(
        isFavorite: Boolean? = null,
        limit: Int? = null,
        startIndex: Int? = null,
        enableImageTypes: List<String> = listOf(ImageType.PRIMARY),
    ): BaseItemDtoQueryResult = http.get {
        path("/Persons")
        parameter("userId", userId())
        parameter("imageTypeLimit", 1)
        if (isFavorite != null) parameter("isFavorite", isFavorite)
        if (limit != null) parameter("limit", limit)
        // `/Persons` has no enableTotalRecordCount, so paging it relies on a short page instead.
        if (startIndex != null) parameter("startIndex", startIndex)
        listParameter("enableImageTypes", enableImageTypes)
    }.body()

    /**
     * Seasons of a series.
     *
     * `isMissing = false` matters: the server knows about episodes and seasons from metadata that
     * are not actually in the library, and listing them produces entries that cannot be played.
     */
    suspend fun seasons(seriesId: String): BaseItemDtoQueryResult = http.get {
        path("/Shows/$seriesId/Seasons")
        parameter("userId", userId())
        parameter("isMissing", false)
        parameter("imageTypeLimit", 1)
        listParameter("fields", DEFAULT_FIELDS)
        listParameter("enableImageTypes", listOf(ImageType.PRIMARY))
    }.body()

    /** Episodes of a series, optionally narrowed to one season. */
    suspend fun episodes(seriesId: String, seasonId: String? = null): BaseItemDtoQueryResult = http.get {
        path("/Shows/$seriesId/Episodes")
        parameter("userId", userId())
        if (seasonId != null) parameter("seasonId", seasonId)
        parameter("isMissing", false)
        parameter("imageTypeLimit", 1)
        listParameter("fields", EPISODE_FIELDS)
        listParameter("enableImageTypes", listOf(ImageType.PRIMARY, ImageType.THUMB))
    }.body()

    /** Returns the server's resulting user data, which we use instead of assuming the toggle applied. */
    suspend fun setFavorite(itemId: String, favorite: Boolean): UserItemDataDto {
        val request: suspend (HttpRequestBuilder.() -> Unit) -> HttpResponse =
            if (favorite) http::post else http::delete
        return request {
            path("/UserFavoriteItems/$itemId")
            parameter("userId", userId())
        }.body()
    }

    /**
     * Marking a series or season played cascades to its children server-side, which is what makes
     * "mark all as seen" a single call.
     */
    suspend fun setPlayed(itemId: String, played: Boolean): UserItemDataDto {
        val request: suspend (HttpRequestBuilder.() -> Unit) -> HttpResponse =
            if (played) http::post else http::delete
        return request {
            path("/UserPlayedItems/$itemId")
            parameter("userId", userId())
        }.body()
    }

    /**
     * Asks the server how this item can be played, given what we can decode.
     *
     * The response is the server's verdict — direct play, direct stream, or a transcode it has
     * already begun preparing — so this is the one call that must happen before playback and cannot
     * be cached across items or profile changes.
     *
     * The `playSessionId` it returns has to be carried on the stream URL and every progress report,
     * otherwise the server never learns the transcode was abandoned and keeps encoding.
     */
    suspend fun playbackInfo(
        itemId: String,
        deviceProfile: DeviceProfile,
        mediaSourceId: String? = null,
        maxStreamingBitrate: Int? = null,
        startTimeTicks: Long? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        autoOpenLiveStream: Boolean = true,
    ): PlaybackInfoResponse = http.post {
        path("/Items/$itemId/PlaybackInfo")
        parameter("userId", userId())
        contentType(ContentType.Application.Json)
        setBody(
            PlaybackInfoDto(
                userId = userId(),
                deviceProfile = deviceProfile,
                // The server matches media source ids with the dashes stripped, and silently ignores
                // our stream indices if we omit the id entirely.
                // https://github.com/jellyfin/jellyfin/blob/9a35fd6/Jellyfin.Api/Helpers/MediaInfoHelper.cs#L196-L201
                mediaSourceId = mediaSourceId ?: itemId.replace("-", ""),
                maxStreamingBitrate = maxStreamingBitrate,
                startTimeTicks = startTimeTicks,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                autoOpenLiveStream = autoOpenLiveStream,
            ),
        )
    }.body()

    /**
     * Playback reporting. These drive the resume point, the "now playing" entry in the server's
     * dashboard, and — critically — the teardown of a transcode when the user stops.
     *
     * All three return no body.
     */
    suspend fun reportPlaybackStart(info: PlaybackProgressInfo) {
        http.post {
            path("/Sessions/Playing")
            contentType(ContentType.Application.Json)
            setBody(info)
        }
    }

    suspend fun reportPlaybackProgress(info: PlaybackProgressInfo) {
        http.post {
            path("/Sessions/Playing/Progress")
            contentType(ContentType.Application.Json)
            setBody(info)
        }
    }

    suspend fun reportPlaybackStopped(info: PlaybackStopInfo) {
        http.post {
            path("/Sessions/Playing/Stopped")
            contentType(ContentType.Application.Json)
            setBody(info)
        }
    }

    /**
     * The original file, streamed untouched.
     *
     * `static=true` is what tells the server not to remux — without it this route re-containerises.
     */
    fun directPlayUrl(itemId: String, playSessionId: String, mediaSourceId: String): String =
        "${serverUrl()}/Videos/$itemId/stream" +
            "?static=true&playSessionId=$playSessionId&mediaSourceId=$mediaSourceId&deviceId=${deviceInfo.id}"

    /** The original streams, remuxed into a container we can open. */
    fun directStreamUrl(
        itemId: String,
        container: String,
        playSessionId: String,
        mediaSourceId: String,
    ): String =
        "${serverUrl()}/Videos/$itemId/stream.$container" +
            "?playSessionId=$playSessionId&mediaSourceId=$mediaSourceId&deviceId=${deviceInfo.id}"

    /**
     * Resolves a server-relative path — a `transcodingUrl` or a subtitle `deliveryUrl` — against the
     * current server.
     *
     * These arrive with their query string already built by the server, so they are used verbatim.
     */
    fun absoluteUrl(serverRelativePath: String): String =
        "${serverUrl()}/${serverRelativePath.trimStart('/')}"

    companion object {
        /**
         * Extra fields to hydrate. Empty by default — each one costs the server work and is paid
         * on every item of every page. Only [episodes] renders anything beyond the card fields, so
         * only it asks for more.
         */
        val DEFAULT_FIELDS = emptyList<String>()

        /** Episode rows show a synopsis; nothing else in a list does. */
        private val EPISODE_FIELDS = listOf("Overview")
    }
}
