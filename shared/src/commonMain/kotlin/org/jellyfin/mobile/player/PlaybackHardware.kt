package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/**
 * The two things outside the app that the player's drag gestures reach: how bright the screen is
 * and how loud the media stream is.
 *
 * Both are read live rather than cached. A gesture is not the only way either changes — the volume
 * keys and the system's own brightness control are still there — so a value read once when the
 * player opened would be stale by the time someone dragged.
 *
 * Values are 0f..1f at this boundary. Neither platform expresses them that way natively (Android
 * counts volume in integer steps, iOS scales brightness per display), so converting here keeps the
 * gesture code from having to care.
 */
interface PlaybackHardware {
    /** Screen brightness, 0f..1f. */
    fun brightness(): Float

    fun setBrightness(value: Float)

    /** Media volume, 0f..1f. */
    fun volume(): Float

    fun setVolume(value: Float)

    /**
     * Whether [setBrightness] does anything. False on desktop, where the display's brightness is the
     * operating system's business and the JVM has no way to ask.
     */
    val canSetBrightness: Boolean

    /**
     * Whether [setVolume] does anything. False on iOS, which offers no public way to set the
     * system volume — the right-hand gesture is hidden rather than left to do nothing visibly.
     */
    val canSetVolume: Boolean
}

/**
 * Returns a controller scoped to the current composition.
 *
 * A composition rather than a singleton because the brightness override belongs to the window
 * showing the player: leaving the screen has to hand brightness back to the system, which is the
 * same lifetime [rememberOrientationController] needs and for the same reason.
 */
@Composable
expect fun rememberPlaybackHardware(): PlaybackHardware
