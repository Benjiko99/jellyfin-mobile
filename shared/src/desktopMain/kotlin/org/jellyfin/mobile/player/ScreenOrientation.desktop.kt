package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * No-op, and permanently so — unlike iOS, where the same shape is waiting on work in `iosApp/`.
 *
 * A desktop window has no orientation to lock. What the player is really asking for when it requests
 * landscape is "give the picture the screen", and on desktop that is [ImmersiveMode], which is
 * implemented.
 */
@Composable
actual fun rememberOrientationController(): OrientationController =
    remember { OrientationController { } }

private fun OrientationController(onRequest: (ScreenOrientation) -> Unit): OrientationController =
    object : OrientationController {
        override fun request(orientation: ScreenOrientation) = onRequest(orientation)
    }
