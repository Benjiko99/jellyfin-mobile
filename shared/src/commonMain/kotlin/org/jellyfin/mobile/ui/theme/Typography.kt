package org.jellyfin.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale.
 *
 * **No font is shipped.** Every style below leaves `fontFamily` unset and so resolves to the
 * platform's own — Roboto on Android, San Francisco on iOS. That is a decision rather than an
 * omission: a bundled font costs the same bytes twice (APK and framework), and picking Jellyfin's
 * brand faces means answering a licensing question and a "which weights" question that nobody has
 * asked yet. Two platform fonts that each look native beat one that looks imported on both.
 *
 * Sizes and line heights are Material's baseline scale, unchanged — they are well tuned and moving
 * a line height is how text starts clipping. Three things do differ, all in the same direction:
 *
 * 1. **The heading family is semi-bold**, where Material has display and `titleLarge` at regular
 *    and the other titles at medium. Headings in this app sit directly above dense grids of
 *    artwork; at regular weight a section title competes with the poster titles underneath it
 *    rather than introducing them.
 * 2. **Headings track tighter**, by a quarter of a point. It is what stops a semi-bold heading from
 *    also reading as a wider one.
 * 3. **`labelSmall` is medium weight.** It is the app's most-used label — badges, runtimes, years —
 *    and much of it is drawn over artwork rather than a flat surface.
 *
 * Body and the remaining label styles are Material's, untouched. They are read at length, and
 * nothing about a media browser makes the baseline wrong for them.
 */
private val HeadingWeight = FontWeight.SemiBold

/** See point 2 above. */
private val HeadingTracking = (-0.25).sp

internal val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = HeadingTracking,
    ),
    displaySmall = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = HeadingTracking,
    ),
    headlineLarge = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = HeadingTracking,
    ),
    headlineMedium = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = HeadingTracking,
    ),
    headlineSmall = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = HeadingTracking,
    ),
    titleLarge = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = HeadingTracking,
    ),
    titleMedium = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = HeadingWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
