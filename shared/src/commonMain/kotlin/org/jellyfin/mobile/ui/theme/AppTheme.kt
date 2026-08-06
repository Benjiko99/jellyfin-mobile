package org.jellyfin.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Jellyfin's brand purple. */
private val JellyfinPurple = Color(0xFF9B59D0)
private val JellyfinBlue = Color(0xFF00A4DC)

/**
 * Dark-only for now. Jellyfin's clients are dark by default and a media browser is the archetypal
 * case for it; a light scheme can follow once the design system work in Phase 3 starts.
 */
private val DarkColors = darkColorScheme(
    primary = JellyfinPurple,
    secondary = JellyfinBlue,
    background = Color(0xFF101010),
    surface = Color(0xFF161616),
    surfaceVariant = Color(0xFF242424),
)

/**
 * Card badge colours.
 *
 * Fixed rather than read off the colour scheme because they carry meaning of their own — blue is
 * "this much is left", green is "finished" — while `secondary` and `tertiary` are free to change
 * with the theme. [BadgeContent] is likewise not `onSecondary`, which in a dark scheme is dark
 * enough to be hard to read on either badge.
 */
internal val BadgeUnwatched = JellyfinBlue
internal val BadgeWatched = Color(0xFF3EA55F)
internal val BadgeContent = Color.White

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
