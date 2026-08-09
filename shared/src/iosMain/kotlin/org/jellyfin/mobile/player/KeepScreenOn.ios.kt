package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication

/**
 * `idleTimerDisabled` is app-wide and survives the screen that set it, so — like the brightness
 * override in `PlaybackHardware.ios.kt` — what it was on the way in is put back on the way out.
 *
 * iOS clears it for us when the app leaves the foreground and restores it on the way back, so
 * backgrounding the player does not leave a device that will not sleep.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    // Read before written, and keyed on nothing, for the reason spelled out in the Android actual.
    DisposableEffect(Unit) {
        val application = UIApplication.sharedApplication
        val wasDisabled = application.idleTimerDisabled
        onDispose { application.idleTimerDisabled = wasDisabled }
    }
    SideEffect { UIApplication.sharedApplication.idleTimerDisabled = enabled }
}
