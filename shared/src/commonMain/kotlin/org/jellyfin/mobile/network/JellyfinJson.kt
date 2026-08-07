package org.jellyfin.mobile.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * The Jellyfin API serialises every schema property in PascalCase (`Id`, `RunTimeTicks`,
 * `PrimaryImageAspectRatio`) while *query parameters* are camelCase. Rather than annotating every
 * field with `@SerialName`, we derive the JSON name from the Kotlin name.
 *
 * Note this applies to bodies only — query parameters are written by hand in [JellyfinApi].
 */
@OptIn(ExperimentalSerializationApi::class)
private val PascalCase = JsonNamingStrategy { _, _, serialName ->
    serialName.replaceFirstChar { it.uppercaseChar() }
}

@OptIn(ExperimentalSerializationApi::class)
val JellyfinJson: Json = Json {
    namingStrategy = PascalCase
    // Servers add fields between versions and enable plugins that extend responses; never fail on them.
    ignoreUnknownKeys = true
    // Absent property == null, and we don't send nulls we didn't set.
    explicitNulls = false
    isLenient = true
}

/**
 * For the web client's `config.json`, which is camelCase.
 *
 * A second [Json] rather than `@SerialName` on the fields of
 * [org.jellyfin.mobile.network.dto.WebConfig]: a naming strategy is applied to the serial name
 * whether or not an annotation set it, so `@SerialName("menuLinks")` would still be PascalCased on
 * its way out and the annotation would look like it worked while doing nothing.
 */
val WebConfigJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
