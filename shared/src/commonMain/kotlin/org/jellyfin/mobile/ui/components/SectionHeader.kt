package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.ScreenPadding

private val ChevronSize = 20.dp

/**
 * The title above a list. When [onMore] is given the title takes a chevron and the two act as one control.
 */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Unweighted children measure first, so the chevron keeps its place on a title
                // long enough to be truncated.
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onMore != null) {
                Icon(
                    imageVector = ChevronIcon,
                    contentDescription = "Show all",
                    modifier = Modifier.padding(start = 2.dp).size(ChevronSize),
                )
            }
        }
    }
}

@Preview(name = "Section header")
@Composable
private fun SectionHeaderPreview() {
    PreviewSurface {
        Column {
            SectionHeader(title = "Movies", onMore = {})
            SectionHeader(title = "Shows")
            SectionHeader(
                title = "A section title long enough that it runs out of room for itself",
                onMore = {},
            )
        }
    }
}
