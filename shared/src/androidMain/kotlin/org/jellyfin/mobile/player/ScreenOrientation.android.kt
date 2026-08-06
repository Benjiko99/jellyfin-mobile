package org.jellyfin.mobile.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberOrientationController(): OrientationController {
    val activity = LocalContext.current.findActivity()
    val controller = remember(activity) { AndroidOrientationController(activity) }

    DisposableEffect(controller) {
        onDispose { controller.request(ScreenOrientation.Auto) }
    }
    return controller
}

private class AndroidOrientationController(private val activity: Activity?) : OrientationController {
    override fun request(orientation: ScreenOrientation) {
        activity?.requestedOrientation = when (orientation) {
            // UNSPECIFIED, not SENSOR: it hands control back to the user's own rotation-lock
            // setting rather than overriding it in the other direction.
            ScreenOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            // The SENSOR_ variants let the device still flip 180°, so a user holding the phone the
            // other way up is not stuck with an upside-down picture.
            ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
}

/**
 * Compose's context is often a `ContextThemeWrapper` rather than the activity itself, so it has to
 * be unwrapped rather than cast.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
