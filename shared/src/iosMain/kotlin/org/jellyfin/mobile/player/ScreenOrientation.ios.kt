package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * No-op until the iOS player exists.
 *
 * iOS has no per-screen equivalent of Android's `requestedOrientation`. A lock has to be published
 * by the hosting `UIViewController` through `supportedInterfaceOrientations`, then applied with
 * `setNeedsUpdateOfSupportedInterfaceOrientations` — which means real work in `iosApp/`, not here.
 * Implementing it alongside the VLCKit engine keeps that in one place.
 */
@Composable
actual fun rememberOrientationController(): OrientationController =
    remember { OrientationController { } }

private fun OrientationController(onRequest: (ScreenOrientation) -> Unit) = object : OrientationController {
    override fun request(orientation: ScreenOrientation) = onRequest(orientation)
}
