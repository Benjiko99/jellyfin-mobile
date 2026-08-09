package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/**
 * Holds the display awake while [enabled].
 *
 * Watching a film is the one thing a phone cannot tell apart from being ignored: nothing is touching
 * the screen, so the idle timer dims it and locks it a minute in. Both platforms offer a way to say
 * "this window is showing something", and neither needs a permission to do it — Android's
 * `WAKE_LOCK` is for the `PowerManager` locks a *service* takes, not for this.
 *
 * A composable rather than a controller because it is state, not a command: it follows [enabled]
 * down as well as up, and lets the screen sleep again when the composition leaves. That last part is
 * the one that matters — a lock the player forgot to drop would keep the whole app awake behind it.
 *
 * A platform hook because the two do it in different places: Android marks the view, iOS sets a flag
 * on the application.
 */
@Composable
expect fun KeepScreenOn(enabled: Boolean)
