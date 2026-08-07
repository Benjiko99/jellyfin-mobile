package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.WatchBadge
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.BadgeContent
import org.jellyfin.mobile.ui.theme.BadgeUnwatched
import org.jellyfin.mobile.ui.theme.BadgeWatched

private val BadgeSize = 20.dp
private val BadgeIconSize = 14.dp

/**
 * How much of something is left to watch: a count of unplayed children, or a tick for finished.
 *
 * The one drawing of this in the app. It sits in the corner of a [MediaCard] and inline in the
 * episode and credit rows, which each previously had a tick of their own — a purple circle with a
 * "✓" glyph in one, a bare "✓" in the other — so the same state read differently on three screens.
 *
 * A circle that stretches into a pill for two- and three-digit counts, so a 24-episode season and a
 * 300-film collection both fit without every badge being sized for the worst case.
 */
@Composable
internal fun WatchIndicator(badge: WatchBadge, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = BadgeSize, minHeight = BadgeSize)
            .clip(CircleShape)
            .background(
                when (badge) {
                    is WatchBadge.Unwatched -> BadgeUnwatched
                    WatchBadge.Watched -> BadgeWatched
                },
            )
            // Otherwise a screen reader reads a bare number, or the tick not at all.
            .clearAndSetSemantics {
                contentDescription = when (badge) {
                    is WatchBadge.Unwatched -> "${badge.count} left to watch"
                    WatchBadge.Watched -> "Watched"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (badge) {
            is WatchBadge.Unwatched -> Text(
                text = badge.count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = BadgeContent,
                modifier = Modifier.padding(horizontal = 5.dp),
            )

            WatchBadge.Watched -> Icon(
                imageVector = CheckIcon,
                contentDescription = null,
                tint = BadgeContent,
                modifier = Modifier.size(BadgeIconSize),
            )
        }
    }
}

/**
 * Every width the badge has to survive: one digit stays a circle, two and three stretch it into a
 * pill, and the tick is a fixed-size icon rather than text.
 */
@Preview(name = "Watch indicator")
@Composable
private fun WatchIndicatorPreview() {
    PreviewSurface {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WatchIndicator(WatchBadge.Unwatched(1))
            WatchIndicator(WatchBadge.Unwatched(24))
            WatchIndicator(WatchBadge.Unwatched(312))
            WatchIndicator(WatchBadge.Watched)
        }
    }
}
