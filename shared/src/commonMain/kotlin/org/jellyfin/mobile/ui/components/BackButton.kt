package org.jellyfin.mobile.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Material's `arrow_back`, drawn here rather than pulled in from `material-icons-core`: that
 * artifact is deprecated upstream and is no longer a transitive dependency of Material 3, so one
 * path is cheaper than the dependency. `autoMirror` flips it for right-to-left locales.
 */
private val ArrowBack: ImageVector = ImageVector.Builder(
    name = "ArrowBack",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = true,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineToRelative(5.59f, -5.59f)
        lineTo(12f, 4f)
        lineToRelative(-8f, 8f)
        lineToRelative(8f, 8f)
        lineToRelative(1.41f, -1.41f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        verticalLineToRelative(-2f)
        close()
    }
}.build()

/**
 * The app's back control: an icon alone, with the label carried by the content description so
 * screen readers still announce it.
 */
@Composable
internal fun BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = ArrowBack, contentDescription = "Back", tint = tint)
    }
}
