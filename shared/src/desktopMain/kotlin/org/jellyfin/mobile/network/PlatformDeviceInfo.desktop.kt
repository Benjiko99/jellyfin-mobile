package org.jellyfin.mobile.network

import java.net.InetAddress

/**
 * The machine's own name, which is what every other desktop Jellyfin client reports and what an
 * administrator will recognise in the server's device list.
 *
 * Carries the same TODO as the Android actual: the id should be a persisted random value rather than
 * something derived from the environment. Two accounts on one machine currently share an id and the
 * server folds them into a single device entry.
 */
actual fun platformDeviceInfo(): DeviceInfo {
    val hostName = hostName()
    return DeviceInfo(
        name = hostName,
        id = "desktop-$hostName".lowercase().map { character ->
            if (character.isLetterOrDigit() || character == '-') character else '-'
        }.joinToString(""),
    )
}

/**
 * Environment first, because `InetAddress.getLocalHost()` resolves the host name through DNS and
 * blocks for as long as that takes — seconds, on a machine whose resolver is unreachable. Windows
 * always sets `COMPUTERNAME`; `HOSTNAME` is a shell variable that most Linux shells export and macOS
 * does not, so the lookup remains the answer there.
 */
private fun hostName(): String =
    System.getenv("COMPUTERNAME")
        ?: System.getenv("HOSTNAME")
        ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: "desktop"
