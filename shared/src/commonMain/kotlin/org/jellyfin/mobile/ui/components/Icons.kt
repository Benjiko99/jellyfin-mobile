package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.preview.PreviewSurface

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

/**
 * A tick, for the "everything watched" badge.
 *
 * Drawn heavier than [StrokeWidth]: this one is rendered at badge size rather than at the 24.dp an
 * icon button gives, and at that scale the standard weight all but disappears.
 */
internal val CheckIcon: ImageVector = ImageVector.Builder(
    name = "Check",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(5f, 12.5f)
        lineTo(9.5f, 17f)
        lineTo(19f, 7.5f)
    }
}.build()

/**
 * A chevron, pointing at the rest of a list from the end of its [SectionHeader] title.
 *
 * `autoMirror` flips it for right-to-left locales, where "onwards" is the other way.
 */
internal val ChevronIcon: ImageVector = ImageVector.Builder(
    name = "Chevron",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = StrokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9.5f, 5f)
        lineTo(16.5f, 12f)
        lineTo(9.5f, 19f)
    }
}.build()

/**
 * A head and shoulders, standing in for the profile picture of a user who has not set one.
 *
 * Material's `Icons.Default.Person` in outline form. Redrawn here rather than depended on for the
 * reason above: `material-icons-core` is deprecated upstream and would be a whole artifact for one
 * glyph.
 */
internal val PersonIcon: ImageVector = ImageVector.Builder(
    name = "Person",
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
        // The head, as two half-circles — an arc sweeping a full 360° has no direction to take.
        moveTo(16f, 8f)
        arcTo(4f, 4f, 0f, true, true, 8f, 8f)
        arcTo(4f, 4f, 0f, true, true, 16f, 8f)
        // The shoulders, as an arc wider than it is tall so the figure reads as a bust rather than
        // as a second circle under the first.
        moveTo(4.5f, 20f)
        arcTo(7.5f, 6f, 0f, false, true, 19.5f, 20f)
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

/** The paths at icon size, which is the only scale their stroke weights were chosen for. */
@Preview(name = "Icons")
@Composable
private fun IconsPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = SearchIcon, contentDescription = "Search")
            Icon(imageVector = ClearIcon, contentDescription = "Clear")
            Icon(imageVector = CheckIcon, contentDescription = "Watched")
            Icon(imageVector = ChevronIcon, contentDescription = "Show all")
            Icon(imageVector = PersonIcon, contentDescription = "Account")
        }
    }
}
