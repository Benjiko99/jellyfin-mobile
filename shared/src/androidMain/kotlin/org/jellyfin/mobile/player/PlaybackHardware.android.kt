package org.jellyfin.mobile.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService

@Composable
actual fun rememberPlaybackHardware(): PlaybackHardware {
    val context = LocalContext.current
    val activity = context.findActivity()
    val hardware = remember(activity) { AndroidPlaybackHardware(context, activity) }

    // Hands brightness back to the system on the way out. Without this the override outlives the
    // player: the window attribute is per-window, but the activity's window is the whole app, so a
    // dimmed player would leave the library dimmed behind it.
    DisposableEffect(hardware) {
        onDispose { hardware.releaseBrightness() }
    }
    return hardware
}

private class AndroidPlaybackHardware(
    context: Context,
    private val activity: Activity?,
) : PlaybackHardware {
    private val audioManager = context.getSystemService<AudioManager>()
    private val contentResolver = context.contentResolver

    /**
     * The brightness override is a window attribute, so it needs the activity that owns the window —
     * absent under `@Preview` and in a Compose test, which is the same "nothing to set" the audio
     * manager's absence means below.
     */
    override val canSetBrightness: Boolean get() = activity != null

    override val canSetVolume: Boolean get() = audioManager != null

    /**
     * The window's override if one is set, otherwise the system's own setting.
     *
     * `screenBrightness` starts at `BRIGHTNESS_OVERRIDE_NONE` (-1), meaning "whatever the system is
     * doing". Returning that as a level would make the first drag jump from nowhere, so the system
     * value is read instead — which is what the user is actually looking at.
     */
    override fun brightness(): Float {
        val override = activity?.window?.attributes?.screenBrightness ?: BRIGHTNESS_OVERRIDE_NONE
        if (override >= 0f) return override.coerceIn(0f, 1f)

        val maximum = runCatching {
            Settings.System.getInt(contentResolver, SCREEN_BRIGHTNESS_MAXIMUM)
        }.getOrNull()?.takeIf { it > 0 } ?: DEFAULT_BRIGHTNESS_MAXIMUM
        val current = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull() ?: return DEFAULT_BRIGHTNESS
        return (current.toFloat() / maximum).coerceIn(0f, 1f)
    }

    /**
     * Sets the window's own brightness, which needs no permission.
     *
     * The alternative — writing `Settings.System.SCREEN_BRIGHTNESS` — changes it device-wide and
     * requires WRITE_SETTINGS, a permission the user has to grant in a system screen. A per-window
     * override is both less intrusive and what every other video player does.
     *
     * Floored just above zero: a true 0 is a black screen with no way back other than by feel.
     */
    override fun setBrightness(value: Float) {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply {
            screenBrightness = value.coerceIn(MINIMUM_BRIGHTNESS, 1f)
        }
    }

    fun releaseBrightness() {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply {
            screenBrightness = BRIGHTNESS_OVERRIDE_NONE
        }
    }

    override fun volume(): Float {
        val manager = audioManager ?: return 0f
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: return 0f
        return manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maximum
    }

    /**
     * Rounds to the nearest step the device actually has.
     *
     * Media volume is an integer 0..max — commonly 15, sometimes 7 — so a continuous drag has to be
     * quantised somewhere. Doing it here means the overlay shows the level that was really set
     * rather than the one the finger asked for.
     */
    override fun setVolume(value: Float) {
        val manager = audioManager ?: return
        val maximum = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).takeIf { it > 0 } ?: return
        val step = (value.coerceIn(0f, 1f) * maximum).roundToNearestInt()
        // No FLAG_SHOW_UI: our own overlay is already saying this, and the system panel over the
        // top of it would be two answers to the same question.
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
    }

    private fun Float.roundToNearestInt(): Int = (this + 0.5f).toInt()

    private companion object {
        const val BRIGHTNESS_OVERRIDE_NONE = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        /** Dark, but still legible enough to find the control that put it there. */
        const val MINIMUM_BRIGHTNESS = 0.01f

        /** Assumed when the system will not say what it is doing. Mid-scale, so a drag has room either way. */
        const val DEFAULT_BRIGHTNESS = 0.5f

        /**
         * `Settings.System.SCREEN_BRIGHTNESS` is raw and its ceiling is not published as a constant;
         * `screen_brightness_maximum` exists on most devices but not all, and 255 is the historical
         * default the framework itself assumes.
         */
        const val SCREEN_BRIGHTNESS_MAXIMUM = "screen_brightness_maximum"
        const val DEFAULT_BRIGHTNESS_MAXIMUM = 255
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
