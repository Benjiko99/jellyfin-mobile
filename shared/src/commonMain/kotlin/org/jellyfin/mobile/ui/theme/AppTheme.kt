package org.jellyfin.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jellyfin.mobile.storage.ThemePreference

/**
 * Whether [this] preference means a dark app *right now*.
 *
 * Composable because [ThemePreference.System] is a live reading: `isSystemInDarkTheme` recomposes
 * when the device flips, so following the system does not need a restart.
 */
@Composable
fun ThemePreference.isDark(): Boolean = when (this) {
    ThemePreference.System -> isSystemInDarkTheme()
    ThemePreference.Light -> false
    ThemePreference.Dark -> true
}

/**
 * The app's theme: colour, type and shape.
 *
 * All three are ours. Material fills in whatever is not passed, and a `MaterialTheme` given only a
 * colour scheme quietly keeps Material's baseline type and corners — which is how the app spent its
 * first months rounding cards to one scale at the call site while every `Card`, `TextField` and
 * dialog Material drew for it rounded to another.
 *
 * Only [darkTheme] varies between the two schemes. Type and shape are deliberately shared: a
 * heading does not want a different weight because the background went white, and a poster does not
 * change shape. Everything that legitimately differs is a colour, and it lives in
 * [DarkColors]/[LightColors].
 *
 * @param darkTheme resolved by the caller from [ThemePreference.isDark] rather than read from the
 * system here — the app's scheme is a setting, and a preview or a screenshot test needs to be able
 * to state it outright.
 */
@Composable
fun AppTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    // Before the content, so the status and navigation bars are already carrying the right icon
    // colour on the frame the new scheme first draws in.
    SystemBarAppearance(darkTheme)

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
