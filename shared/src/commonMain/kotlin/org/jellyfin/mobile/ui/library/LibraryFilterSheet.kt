package org.jellyfin.mobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.LibraryFilterOptions
import org.jellyfin.mobile.domain.LibraryFilters
import org.jellyfin.mobile.domain.LibrarySort
import org.jellyfin.mobile.domain.PlayedFilter
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.theme.ScreenPadding

/**
 * Sort and filter, in a bottom sheet.
 *
 * Every change applies immediately rather than behind an "Apply" button. The grid is one request
 * away and the sheet does not cover it entirely, so the result of a tap is visible as it is made —
 * and there is no half-set state to abandon by swiping the sheet away.
 *
 * The options come from the server ([LibraryFilterOptions]) rather than from a list of genres we
 * invented, so a library with no age ratings simply has no rating section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterSheet(
    filters: LibraryFilters,
    options: LibraryFilterOptions,
    onFiltersChange: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        LibraryFilterSheetContent(filters, options, onFiltersChange)
    }
}

/**
 * The sheet's contents, split out so a preview can render them — preview tooling cannot open a
 * modal bottom sheet, and what is inside it is the part worth looking at.
 */
@Composable
private fun LibraryFilterSheetContent(
    filters: LibraryFilters,
    options: LibraryFilterOptions,
    onFiltersChange: (LibraryFilters) -> Unit,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sort & filter", style = MaterialTheme.typography.titleLarge)
            // Clears the filters and leaves the ordering, which is not a filter and is rarely what
            // anyone means by "reset".
            TextButton(
                onClick = { onFiltersChange(filters.cleared()) },
                enabled = filters.isFiltering,
            ) {
                Text("Reset")
            }
        }

        FilterGroup("Sort by") {
            LibrarySort.entries.forEach { sort ->
                FilterChip(
                    selected = filters.sort == sort,
                    // Picking an ordering picks its natural direction with it — newest first for a
                    // date, A–Z for a name — and picking it again flips that.
                    onClick = {
                        onFiltersChange(
                            if (filters.sort == sort) {
                                filters.copy(descending = !filters.descending)
                            } else {
                                filters.copy(sort = sort, descending = sort.defaultDescending)
                            },
                        )
                    },
                    label = {
                        Text(
                            when {
                                filters.sort != sort -> sort.label
                                filters.descending -> "${sort.label} ↓"
                                else -> "${sort.label} ↑"
                            },
                        )
                    },
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        FilterGroup("Watched") {
            PlayedFilter.entries.forEach { played ->
                FilterChip(
                    selected = filters.played == played,
                    onClick = { onFiltersChange(filters.copy(played = played)) },
                    label = {
                        Text(
                            when (played) {
                                PlayedFilter.All -> "Any"
                                PlayedFilter.Played -> "Watched"
                                PlayedFilter.Unplayed -> "Unwatched"
                            },
                        )
                    },
                )
            }
            FilterChip(
                selected = filters.favoritesOnly,
                onClick = { onFiltersChange(filters.copy(favoritesOnly = !filters.favoritesOnly)) },
                label = { Text("Favourites") },
            )
        }

        if (options.genres.isNotEmpty()) {
            FilterGroup("Genre") {
                options.genres.forEach { genre ->
                    ToggleChip(
                        label = genre,
                        selected = genre in filters.genres,
                        onToggle = { onFiltersChange(filters.copy(genres = filters.genres.toggle(genre))) },
                    )
                }
            }
        }

        if (options.officialRatings.isNotEmpty()) {
            FilterGroup("Rating") {
                options.officialRatings.forEach { rating ->
                    ToggleChip(
                        label = rating,
                        selected = rating in filters.officialRatings,
                        onToggle = {
                            onFiltersChange(
                                filters.copy(officialRatings = filters.officialRatings.toggle(rating)),
                            )
                        },
                    )
                }
            }
        }

        if (options.years.isNotEmpty()) {
            FilterGroup("Year") {
                options.years.forEach { year ->
                    ToggleChip(
                        label = year.toString(),
                        selected = year in filters.years,
                        onToggle = { onFiltersChange(filters.copy(years = filters.years.toggle(year))) },
                    )
                }
            }
        }
    }
}

/** Selecting is adding and deselecting is removing; every group below the sort is a multi-select. */
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

/** A genre can be "Action & Adventure", which must wrap to the next chip rather than off the sheet. */
@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Preview(name = "Library filters")
@Composable
private fun LibraryFilterSheetPreview() {
    PreviewSurface {
        LibraryFilterSheetContent(
            filters = LibraryFilters(),
            options = PreviewData.filterOptions,
            onFiltersChange = {},
        )
    }
}

/** Filters applied, which is what enables "Reset" and marks the chips the query is narrowed by. */
@Preview(name = "Library filters · active")
@Composable
private fun LibraryFilterSheetActivePreview() {
    PreviewSurface {
        LibraryFilterSheetContent(
            filters = LibraryFilters(
                sort = LibrarySort.DateAdded,
                descending = true,
                played = PlayedFilter.Unplayed,
                genres = setOf("Drama"),
                years = setOf(2024),
            ),
            options = PreviewData.filterOptions,
            onFiltersChange = {},
        )
    }
}
