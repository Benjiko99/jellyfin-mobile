package org.jellyfin.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Forces the app's windows into the scheme Compose is drawing.
 *
 * iOS has no status-bar-icon API to set directly: the clock and the battery follow the view
 * controller's `preferredStatusBarStyle`, whose default resolves against the window's trait
 * collection. Overriding the trait is therefore the way to say "this app is dark" to a device that
 * is in light mode — and it lines up any UIKit the app shows alongside Compose (a share sheet, an
 * alert, the keyboard) with the same choice, for free.
 *
 * Every window in every scene, rather than the key window: iPadOS can have several.
 */
@Composable
actual fun SystemBarAppearance(darkTheme: Boolean) {
    DisposableEffect(darkTheme) {
        // Captured before the override so that nested calls unwind — see the Android actual.
        val previous = windows().map { window -> window to window.overrideUserInterfaceStyle }
        val style = if (darkTheme) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        windows().forEach { window -> window.setOverrideUserInterfaceStyle(style) }

        onDispose {
            previous.forEach { (window, style) -> window.setOverrideUserInterfaceStyle(style) }
        }
    }
}

private fun windows(): List<UIWindow> = UIApplication.sharedApplication.connectedScenes
    .filterIsInstance<UIWindowScene>()
    .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
