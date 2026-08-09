package org.jellyfin.mobile.player

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
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

    // Split from the effect below so the restore captures the behaviour the app had before the
    // player rather than the one we set ourselves — the same reason `KeepScreenOn` needs two.
    DisposableEffect(controller) {
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
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

/**
 * `systemBarsIgnoringVisibility` is the Compose reading of `getInsetsIgnoringVisibility`: the space
 * the bars take when they are up, reported even while they are down. It is empty on a device that
 * has no such bar at all, so this reserves nothing that does not exist.
 *
 * The cutout is unioned back in because it is not a bar and does not hide with them — a notch is
 * still a notch in immersive mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun safeDrawingIgnoringVisibility(): WindowInsets =
    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
