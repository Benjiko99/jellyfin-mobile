package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlaybackHardware(): PlaybackHardware = remember { DesktopPlaybackHardware() }

/**
 * Neither, on this platform.
 *
 * **Brightness** belongs to the operating system's display settings and there is no JVM API for it —
 * every route (Windows' `SetMonitorBrightness`, macOS' `IODisplaySetFloatParameter`, DDC/CI over the
 * cable to an external monitor) is a native call, and on a desktop it would dim the whole display
 * rather than one app's window, which is not what the Android gesture does.
 *
 * **Volume** is the system mixer, equally out of reach. The thing worth adjusting here is the
 * engine's own gain, which is what a desktop libVLC binding would expose — so this becomes real
 * alongside the engine (see [DesktopPlayerEngine]) rather than before it, and as a per-player volume
 * rather than the system's.
 *
 * Both flags are false, so the player leaves the two drag halves as plain tap targets and the client
 * settings screen drops the section that configures them.
 */
private class DesktopPlaybackHardware : PlaybackHardware {
    override val canSetBrightness: Boolean get() = false

    override val canSetVolume: Boolean get() = false

    /** Full, which is the honest reading of a display this app does not dim. */
    override fun brightness(): Float = 1f

    override fun setBrightness(value: Float) = Unit

    override fun volume(): Float = 0f

    override fun setVolume(value: Float) = Unit
}
