package org.jellyfin.mobile.player

import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the bars through the insets controller, which is the same object `SystemBarAppearance`
 * colours — so the two have to agree, and do: this decides whether the bars are there, that decides
 * what they look like when they are.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` is the sticky-immersive behaviour. A swipe from the edge
 * brings the bars back *over* the picture for a few seconds and then lets them go again, which is
 * what someone reaching for the time wants. The alternative, `BEHAVIOR_DEFAULT`, treats the same
 * swipe as "show these permanently" and would drop the player out of immersive for good on a gesture
 * that is easy to make by accident while scrubbing.
 *
 * Transient bars are drawn over the picture rather than laid out, so they report themselves as
 * invisible and nothing on screen moves when one appears.
 */
@Composable
actual fun ImmersiveMode(enabled: Boolean) {
    // Null under `@Preview` and in a Compose test, neither of which has a window to configure.
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val controller = remember(activity, view) {
        WindowCompat.getInsetsController(activity.window, view)
    }

    // Split from the effect below so the restore captures what the app had before the player rather
    // than what we set ourselves — the same reason `KeepScreenOn` needs two.
    DisposableEffect(activity, controller) {
        val window = activity.window
        val previousBehavior = controller.systemBarsBehavior
        val previousScrim = window.navigationBarScrim
        val previousContrast = window.navigationBarContrastEnforced

        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.navigationBarScrim = Color.TRANSPARENT
        window.navigationBarContrastEnforced = false

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
            window.navigationBarScrim = previousScrim
            window.navigationBarContrastEnforced = previousContrast
        }
    }

    // Keyed rather than a `SideEffect`: the player recomposes twice a second as the position ticks,
    // and this is a call across to the window rather than a field on a view.
    DisposableEffect(controller, enabled) {
        val bars = WindowInsetsCompat.Type.systemBars()
        if (enabled) controller.hide(bars) else controller.show(bars)
        onDispose { }
    }
}

/*
 * The other half of "the bars must not eat the picture": stopping the platform painting a band
 * behind the navigation bar where it does appear.
 *
 * `SystemBarAppearance` gets the app edge to edge with `SystemBarStyle.auto`, which asks for a
 * contrast scrim behind three-button navigation. That is right over a library — a list scrolling
 * under an unshaded bar is unreadable — and wrong over a film, where it is a grey band across the
 * bottom of the picture that no other player has. So the player turns it off for its own lifetime
 * and hands it back on the way out.
 *
 * Two properties because they cover different API levels. `isNavigationBarContrastEnforced` is the
 * live one from 29 up, including 35+ where it is the only lever left; below 29 the scrim arrives as
 * the bar's own background colour instead, which `auto` sets to `DarkScrim` for a dark app.
 */

/**
 * `navigationBarColor` is deprecated from API 35, where the platform ignores it — every app is edge
 * to edge and the bar is transparent by then. It is still the only way to clear the scrim on 26..34,
 * which is most of what we support, so the deprecation is suppressed rather than worked around.
 */
private var Window.navigationBarScrim: Int
    @Suppress("DEPRECATION")
    get() = navigationBarColor

    @Suppress("DEPRECATION")
    set(value) {
        navigationBarColor = value
    }

/** False below API 29, which has no such thing — and nothing to restore, which is the same answer. */
private var Window.navigationBarContrastEnforced: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isNavigationBarContrastEnforced
    set(value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isNavigationBarContrastEnforced = value
    }

/**
 * `systemBarsIgnoringVisibility` is the Compose reading of `getInsetsIgnoringVisibility`: the space
 * the bars take when they are up, reported even while they are down. It is empty on a device that
 * has no such bar at all, so this reserves nothing that does not exist.
 *
 * `displayCutout` is deliberately *not* unioned in — see the expect declaration. The window already
 * lays out through the cutout, because `enableEdgeToEdge` asks for
 * `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`; this is what lets the controls follow the picture into it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun barInsetsIgnoringVisibility(): WindowInsets = WindowInsets.systemBarsIgnoringVisibility
