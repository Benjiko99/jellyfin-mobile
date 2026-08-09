package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/**
 * No-op. The JVM cannot ask a desktop not to sleep.
 *
 * Each platform has an answer and none of them are portable: `SetThreadExecutionState` with
 * `ES_DISPLAY_REQUIRED` on Windows, an `IOPMAssertionCreateWithName` power assertion on macOS, and
 * `org.freedesktop.ScreenSaver.Inhibit` over D-Bus on Linux. All three need native calls, which is a
 * dependency worth taking for a player that plays and not for one that cannot — see
 * [DesktopPlayerEngine].
 *
 * Nudging the mouse pointer with `java.awt.Robot` is the usual workaround and is deliberately not
 * done here: it moves a pointer the user is holding, defeats screen locks the user's employer may be
 * relying on, and keeps running if the app stops without unwinding.
 */
@Composable
actual fun KeepScreenOn(enabled: Boolean) = Unit
