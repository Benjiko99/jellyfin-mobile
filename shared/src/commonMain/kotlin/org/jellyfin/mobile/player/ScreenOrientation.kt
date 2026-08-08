package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/**
 * The two states the fullscreen control moves between.
 *
 * There is no `Portrait` lock. The player fills the screen in either orientation, so locking
 * portrait would only stop the device rotating — which [Auto] already leaves to the user's own
 * rotation-lock setting, where that decision belongs.
 */
enum class ScreenOrientation {
    /** Follow the device's own rotation setting. */
    Auto,

    /** Held landscape whichever way the device is turned. What the fullscreen control asks for. */
    Landscape,
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
