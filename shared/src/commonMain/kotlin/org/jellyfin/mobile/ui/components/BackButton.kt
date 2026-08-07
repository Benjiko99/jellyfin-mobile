package org.jellyfin.mobile.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.ui.preview.PreviewSurface

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
        Icon(imageVector = BackIcon, contentDescription = "Back", tint = tint)
    }
}

@Preview(name = "Back button")
@Composable
private fun BackButtonPreview() {
    PreviewSurface {
        BackButton(onClick = {})
    }
}
