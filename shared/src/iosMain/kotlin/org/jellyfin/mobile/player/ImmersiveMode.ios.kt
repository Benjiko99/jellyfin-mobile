package org.jellyfin.mobile.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * No-op until the iOS player exists, for the same reason as [rememberOrientationController] and in
 * the same place.
 *
 * Neither of the two things to hide can be asked for from here. The status bar follows the hosting
 * controller's `prefersStatusBarHidden` and the home indicator its `prefersHomeIndicatorAutoHidden`
 * — both read-only properties a `UIViewController` *overrides*, so they cannot be set on a
 * controller from outside it. `UIApplication.setStatusBarHidden` was the way to do this from
 * anywhere, and has done nothing in a scene-based app since iOS 13.
 *
 * So this needs a `UIViewController` subclass in `iosApp/` that publishes both and re-reads them on
 * `setNeedsUpdateOfHomeIndicatorAutoHidden` — the same shape of work the orientation lock needs,
 * against the same controller, which is why the two are worth doing together with the VLCKit engine.
 */
@Composable
actual fun ImmersiveMode(enabled: Boolean) = Unit

/**
 * Plain `safeDrawing`, which is already the right answer here: nothing on iOS hides the status bar
 * or the home indicator yet, so there is no visibility to ignore. It becomes a real distinction at
 * the same moment [ImmersiveMode] does, and the safe-area insets iOS reports do not shrink when the
 * indicator auto-hides in any case.
 */
@Composable
actual fun safeDrawingIgnoringVisibility(): WindowInsets = WindowInsets.safeDrawing
