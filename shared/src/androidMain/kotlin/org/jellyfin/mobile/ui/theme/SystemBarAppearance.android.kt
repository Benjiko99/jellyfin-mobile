package org.jellyfin.mobile.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Re-runs `enableEdgeToEdge` with the app's scheme rather than the device's.
 *
 * `MainActivity` calls it once with no arguments, which is what makes the bars transparent in the
 * first place; the argument-less form defaults to `SystemBarStyle.auto`, whose `detectDarkMode`
 * reads `Configuration.UI_MODE_NIGHT_MASK`. That is the system setting, so a phone in light mode
 * running our dark app got dark status-bar icons on a dark background. Passing our own predicate is
 * the sanctioned way to override it — and it also fixes the navigation bar's contrast scrim, which
 * `isAppearanceLightNavigationBars` alone would not have.
 */
@Composable
actual fun SystemBarAppearance(darkTheme: Boolean) {
    // Null under `@Preview` and in a Compose test, neither of which has a window to configure.
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val view = LocalView.current

    DisposableEffect(activity, darkTheme) {
        // Read back rather than assumed, so that nested calls unwind: the player forces dark bars
        // over its black video whatever the app's scheme is, and leaving it has to restore the
        // scheme rather than whatever the enum's default happens to be.
        val insets = WindowCompat.getInsetsController(activity.window, view)
        val wasDark = !insets.isAppearanceLightStatusBars

        activity.applyBars(darkTheme)
        onDispose { activity.applyBars(wasDark) }
    }
}

private fun ComponentActivity.applyBars(darkTheme: Boolean) {
    enableEdgeToEdge(
        // Transparent both ways: the status bar takes its contrast from the icons alone, and a
        // scrim there would sit over the top of a poster backdrop.
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
        // The scrims androidx documents, used on the API levels that cannot draw their own contrast
        // behind three-button navigation.
        navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
    )
}

private val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
