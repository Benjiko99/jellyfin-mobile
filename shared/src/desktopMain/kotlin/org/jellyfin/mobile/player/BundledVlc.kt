package org.jellyfin.mobile.player

import java.io.File

/**
 * Points VLCJ at the libVLC packaged with the app, ahead of any VLC the user happens to have.
 *
 * `:desktopApp` puts the libraries and the plugin directory under `vlc/` in the application
 * resources, which Compose Desktop makes findable through `compose.application.resources.dir` —
 * set both for a packaged build and for `:desktopApp:run`. Announcing that directory on
 * `jna.library.path` is all VLCJ needs: `JnaLibraryPathDirectoryProvider` reads exactly that
 * property, and the platform discovery strategy that finds libvlc there sets `VLC_PLUGIN_PATH`
 * alongside it, which is why the layout has to mirror an installation.
 *
 * Prepended rather than appended so the packaged copy wins. The two are not interchangeable: a
 * machine may have any VLC at all, including one older than the API VLCJ is built against.
 *
 * Does nothing when there is no bundle — running from a source build on macOS or Linux, where
 * VideoLAN publishes nothing we can unpack — and discovery then finds the system's VLC as before.
 */
internal fun useBundledVlc() {
    val directory = bundledVlcDirectory() ?: return
    val existing = System.getProperty(JNA_LIBRARY_PATH)

    System.setProperty(
        JNA_LIBRARY_PATH,
        listOfNotNull(directory.absolutePath, existing).joinToString(File.pathSeparator),
    )
}

private const val JNA_LIBRARY_PATH = "jna.library.path"

private fun bundledVlcDirectory(): File? {
    val resources = System.getProperty("compose.application.resources.dir") ?: return null
    return File(resources, "vlc").takeIf { it.isDirectory }
}
