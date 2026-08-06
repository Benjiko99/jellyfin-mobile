package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

enum class ScreenOrientation {
    /** Follow the device's own rotation setting. */
    Auto,
    Landscape,
    Portrait,
    ;

    fun next(): ScreenOrientation = when (this) {
        Auto -> Landscape
        Landscape -> Portrait
        Portrait -> Auto
    }
}

/**
 * Locks the screen orientation while the player is open.
 *
 * A platform hook rather than shared code: Android sets it per-activity at runtime, while iOS
 * resolves it from the view controller hierarchy.
 */
interface OrientationController {
    fun request(orientation: ScreenOrientation)
}

/**
 * Returns a controller scoped to the current composition. It restores [ScreenOrientation.Auto] when
 * the composition leaves, so a lock set inside the player does not follow the user back out of it.
 */
@Composable
expect fun rememberOrientationController(): OrientationController
