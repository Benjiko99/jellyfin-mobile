package org.jellyfin.mobile.player

import androidx.compose.runtime.Composable

/** No-op, for the same reason as the Android actual. */
@Composable
actual fun FullscreenShortcut(onToggle: () -> Unit) = Unit
