package org.jellyfin.mobile.network

/**
 * Builds the `Authorization` header every Jellyfin request carries:
 *
 * ```
 * MediaBrowser Client="Jellyfin Mobile", Device="Pixel 8", DeviceId="…", Version="0.1.0", Token="…"
 * ```
 *
 * The scheme is `MediaBrowser`; parameters are quoted and comma-separated. `Token` is omitted
 * before authentication.
 */
fun buildAuthorizationHeader(
    client: ClientInfo,
    device: DeviceInfo,
    accessToken: String?,
): String = buildString {
    append("MediaBrowser ")
    val parameters = buildList {
        add("Client" to client.name)
        add("Device" to device.name)
        add("DeviceId" to device.id)
        add("Version" to client.version)
        if (accessToken != null) add("Token" to accessToken)
    }
    parameters.joinTo(this, separator = ", ") { (key, value) ->
        "$key=\"${encodeHeaderValue(value)}\""
    }
}

/**
 * Header values must stay inside quoted-string syntax and within ISO-8859-1, which device names
 * routinely violate (a phone named "Ben's iPhone 📱" is normal). Escape the quoting characters and
 * replace anything non-ASCII rather than letting the HTTP client reject the request.
 */
private fun encodeHeaderValue(value: String): String = buildString {
    for (char in value) {
        when {
            char == '"' || char == '\\' -> append('\\').append(char)
            char.code in 0x20..0x7E -> append(char)
            else -> append('?')
        }
    }
}
