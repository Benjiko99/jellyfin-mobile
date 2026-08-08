package org.jellyfin.mobile.ui.theme

import androidx.compose.runtime.Composable

/**
 * Tells the platform which way round the status and navigation bars should be drawn.
 *
 * The bars are the system's, not ours: we draw underneath them and it draws the clock, the battery
 * and the gesture handle on top. Both platforms decide the colour of that furniture from the
 * *system's* light/dark setting unless told otherwise, which is wrong the moment the app's scheme is
 * a setting of its own — a phone in light mode over our dark app gives dark-on-dark, and an
 * unreadable status bar.
 *
 * @param darkTheme what the app is drawing, so the platform can pick the contrasting furniture.
 * Note this is the app's scheme and not the device's.
 */
@Composable
expect fun SystemBarAppearance(darkTheme: Boolean)
