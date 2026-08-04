package org.jellyfin.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** Jellyfin's brand purple. */
private val JellyfinPurple = androidx.compose.ui.graphics.Color(0xFF9B59D0)
private val JellyfinBlue = androidx.compose.ui.graphics.Color(0xFF00A4DC)

/**
 * Dark-only for now. Jellyfin's clients are dark by default and a media browser is the archetypal
 * case for it; a light scheme can follow once the design system work in Phase 3 starts.
 */
private val DarkColors = darkColorScheme(
    primary = JellyfinPurple,
    secondary = JellyfinBlue,
    background = androidx.compose.ui.graphics.Color(0xFF101010),
    surface = androidx.compose.ui.graphics.Color(0xFF161616),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF242424),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
