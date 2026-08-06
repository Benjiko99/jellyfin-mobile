package org.jellyfin.mobile.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons drawn here rather than pulled in from `material-icons-core`, for the same reason as
 * [BackButton]'s arrow: that artifact is deprecated upstream and is no longer a transitive
 * dependency of Material 3, so a handful of paths is cheaper than the dependency.
 *
 * Stroked rather than filled, which keeps them legible as geometry instead of as a wall of
 * transcribed bezier coordinates. `Icon` tints them, so the black here is only a placeholder.
 */
private const val StrokeWidth = 2f

/** A magnifier: a ring centred at (10.5, 10.5) with a handle running out to the bottom right. */
internal val SearchIcon: ImageVector = ImageVector.Builder(
    name = "Search",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = StrokeWidth,
        strokeLineCap = StrokeCap.Round,
    ) {
        // Two half-circles, because an arc that sweeps a full 360° has no direction to take.
        moveTo(17f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, true, true, 4f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, true, true, 17f, 10.5f)
        moveTo(15.1f, 15.1f)
        lineTo(20f, 20f)
    }
}.build()

/** An X, used to empty the search field. */
internal val ClearIcon: ImageVector = ImageVector.Builder(
    name = "Clear",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = StrokeWidth,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }
}.build()
