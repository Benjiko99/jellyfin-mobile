package org.jellyfin.mobile.ui.theme

import androidx.compose.runtime.Composable

/**
 * No-op. There are no system bars over a desktop window: the app draws every pixel inside its frame,
 * and the furniture outside it — the title bar, the taskbar, the menu bar — belongs to the desktop
 * and follows the desktop's own light/dark setting rather than ours.
 *
 * A window whose title bar could be told to go dark with the app is a per-platform native call
 * (`DwmSetWindowAttribute` on Windows, `NSAppearance` on macOS) and is a cosmetic question about the
 * frame, not the readability problem this exists to solve on Android and iOS.
 */
@Composable
actual fun SystemBarAppearance(darkTheme: Boolean) = Unit
