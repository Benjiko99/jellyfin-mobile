package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.UIKit.UIScreen

@Composable
actual fun rememberPlaybackHardware(): PlaybackHardware {
    val hardware = remember { IosPlaybackHardware() }

    DisposableEffect(hardware) {
        onDispose { hardware.restoreBrightness() }
    }
    return hardware
}

/**
 * Brightness only.
 *
 * `UIScreen.brightness` is writable and takes effect immediately, so the left-hand gesture works
 * the same as it does on Android. **Volume does not**: iOS has no public API to set the system
 * volume, and the usual workaround — reaching into a hidden `MPVolumeView`'s slider — drives a
 * private subview and is the kind of thing App Review rejects. [canSetVolume] is false, and the
 * player hides the right-hand gesture rather than letting a drag move an overlay that changes
 * nothing.
 *
 * The honest replacement, when the VLCKit engine lands, is `VLCMediaPlayer.audio.volume` — a
 * per-player gain rather than the system volume. That is a different thing from what Android
 * adjusts, so it is a decision to take with the player rather than to smuggle in here.
 *
 * Unlike Android's per-window override, `UIScreen.brightness` is device-wide and persists after the
 * app exits, so the value read on the way in is put back on the way out.
 */
private class IosPlaybackHardware : PlaybackHardware {
    private val screen = UIScreen.mainScreen
    private val brightnessOnEntry = screen.brightness
    private var changed = false

    override val canSetVolume: Boolean get() = false

    override fun brightness(): Float = screen.brightness.toFloat().coerceIn(0f, 1f)

    override fun setBrightness(value: Float) {
        changed = true
        screen.brightness = value.coerceIn(MINIMUM_BRIGHTNESS, 1f).toDouble()
    }

    /**
     * Never read, because [canSetVolume] is false and the player drops the gesture that would ask.
     * `AVAudioSession.outputVolume` is the real answer and is public to read — it is simply not
     * bound in Kotlin/Native's AVFAudio interop, so wire it up alongside a volume control that can
     * actually do something rather than for a reading nothing displays.
     */
    override fun volume(): Float = 0f

    override fun setVolume(value: Float) = Unit

    fun restoreBrightness() {
        if (changed) screen.brightness = brightnessOnEntry
    }

    private companion object {
        /** Dark, but still legible enough to find the control that put it there. */
        const val MINIMUM_BRIGHTNESS = 0.01f
    }
}
