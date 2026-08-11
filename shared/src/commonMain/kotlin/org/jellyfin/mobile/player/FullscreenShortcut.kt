package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/**
 * Listens for the keyboard's way of asking for fullscreen, for as long as the player is on screen.
 *
 * A platform hook because a keyboard is a desktop's, and only the desktop actual does anything: a
 * phone has no F to press, and the same request there is a tap on the control. Scoped to the
 * composition rather than installed once, so the key means this and nothing else only while there is
 * a picture to make fullscreen — and means whatever the rest of the app wants the moment there is not.
 *
 * @param onToggle the same thing the fullscreen control calls, so the two cannot drift apart.
 */
@Composable
expect fun FullscreenShortcut(onToggle: () -> Unit)
