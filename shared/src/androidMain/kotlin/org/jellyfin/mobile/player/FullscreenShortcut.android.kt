package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/**
 * No-op. A keyboard is not how anyone asks a phone for anything, and the one place it would apply —
 * a tablet in a keyboard case — is not worth a shortcut nothing on screen advertises.
 */
@Composable
actual fun FullscreenShortcut(onToggle: () -> Unit) = Unit
