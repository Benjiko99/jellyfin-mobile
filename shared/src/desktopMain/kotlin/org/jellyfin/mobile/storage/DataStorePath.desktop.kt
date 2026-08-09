package org.jellyfin.mobile.storage

import java.io.File

/**
 * Where this operating system expects an application to keep a user's data.
 *
 * Three conventions rather than one cross-platform guess, because putting a dot-directory in a
 * Windows or macOS home directory is the kind of thing users notice and dislike:
 *
 * - Windows: `%APPDATA%\Jellyfin` — the roaming profile, so preferences follow a domain account.
 * - macOS: `~/Library/Application Support/Jellyfin`.
 * - Everything else: `$XDG_DATA_HOME/jellyfin`, falling back to the `~/.local/share` the XDG base
 *   directory specification defines when the variable is unset. Lower case there, and capitalised on
 *   the other two, is each platform's own convention rather than an inconsistency.
 *
 * Unlike Android's `filesDir` and iOS's documents directory, nothing has created this for us.
 */
fun dataStoreDirectory(): String {
    val home = System.getProperty("user.home").orEmpty()
    val osName = System.getProperty("os.name").orEmpty().lowercase()

    val directory = when {
        osName.startsWith("windows") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "Jellyfin")
        osName.startsWith("mac") -> File(home, "Library/Application Support/Jellyfin")
        else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "jellyfin")
    }

    // `mkdirs` answers false both for "already there" and for "could not", so the two are told apart
    // before failing — and this fails here rather than letting DataStore fail on first write, where
    // the reason would be a missing file rather than a directory nobody could create.
    check(directory.isDirectory || directory.mkdirs()) {
        "Could not create the application data directory at $directory"
    }
    return directory.absolutePath
}
