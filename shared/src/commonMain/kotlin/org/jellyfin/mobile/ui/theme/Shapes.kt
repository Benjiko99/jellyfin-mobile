package org.jellyfin.mobile.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corner scale.
 *
 * Read off what the app had already converged on rather than invented: every rounded corner in the
 * codebase was one of these five radii before this file existed, which is a good sign the steps are
 * the right ones — but they were written out at each call site, so nothing held them together and
 * Material's own components were rounding to a different scale entirely.
 *
 * Only [Shapes.extraSmall] departs from Material's baseline (4.dp). Small artwork — an episode still
 * in a list, a credit thumbnail — is where it lands, and at that size 4.dp reads as an unrounded
 * rectangle. Six is what the app was already using there. The rest are Material's.
 *
 * Pills are not in this scale. `CircleShape` (and its `RoundedCornerShape(50)` spelling) is a
 * different idea — a shape that follows the height of whatever it wraps — and Material keeps it out
 * of `Shapes` for the same reason.
 */
internal val AppShapes = Shapes(
    /** Small artwork: episode stills, credit thumbnails. */
    extraSmall = RoundedCornerShape(6.dp),
    /** Cards and posters — the most common shape in the app by a wide margin. */
    small = RoundedCornerShape(8.dp),
    /** Transient overlays, such as the player's brightness and volume indicator. */
    medium = RoundedCornerShape(12.dp),
    /** Panels that sit over content, such as the player's track menus. */
    large = RoundedCornerShape(16.dp),
    /** Sheets and dialogs, such as the user menu. */
    extraLarge = RoundedCornerShape(28.dp),
)
