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
