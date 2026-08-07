package org.jellyfin.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.Alphabet
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Wide enough for a letter and its selected pill, narrow enough that it costs the grid one card at
 * most. The touch targets it makes are under the 48.dp minimum — unavoidably, since 27 of them have
 * to share the height of a phone — so each letter is padded to take the full width of the rail.
 */
private val RailWidth = 28.dp

/**
 * The A–Z rail down the side of a library grid.
 *
 * A jump-to rather than a filter: it re-runs the query anchored at a letter, so it works on a
 * library of ten thousand films where scrolling to "S" does not. The server does the work —
 * `nameStartsWith`, or `nameLessThan` for `#` — so nothing has to be loaded to skip past it.
 *
 * Tapping the selected letter clears it, which is the only way back to the whole list without
 * reaching for the filter sheet.
 */
@Composable
internal fun AlphabetRail(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(RailWidth)
            // 27 letters do not fit a short screen, and on a long one this keeps them centred
            // rather than stretched.
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Alphabet.letters.forEach { letter ->
            val isSelected = letter == selected
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .sizeIn(minWidth = RailWidth)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    // Passing the letter even when it is already selected; the view model reads that
                    // as "clear", which keeps the toggle in one place rather than in both.
                    .clickable { onSelect(letter) }
                    .semantics {
                        this.selected = isSelected
                        role = Role.Tab
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Preview(name = "Alphabet rail")
@Composable
private fun AlphabetRailPreview() {
    PreviewSurface {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AlphabetRail(selected = null, onSelect = {})
            AlphabetRail(selected = "M", onSelect = {})
            AlphabetRail(selected = Alphabet.OTHER, onSelect = {})
        }
    }
}
