package org.jellyfin.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * The app's icons, named for what they mean here rather than for what they depict.
 *
 * All Material Icons. These were hand-drawn `ImageVector` paths until this file was rewritten,
 * because `material-icons-*` is deprecated upstream and is no longer a transitive dependency of
 * Material 3 — see AGENTS.md for what depending on it costs and what to watch for.
 *
 * The indirection earns its place twice over: it keeps a Jellyfin-shaped vocabulary at the call
 * sites — a chevron is [ChevronIcon] because it opens a list, not because of its shape — and it is
 * the one file to change if the dependency has to go again.
 *
 * Several are `AutoMirrored`, the variant that flips in right-to-left locales. Anything meaning
 * "onwards", "away" or "back" needs it; a magnifier and a television look the same everywhere.
 */
internal val SearchIcon: ImageVector = Icons.Default.Search

/** The "everything watched" badge. */
internal val CheckIcon: ImageVector = Icons.Default.Check

/** Opens the rest of a list, from the end of a [SectionHeader] title. */
internal val ChevronIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight

/** Stands in for the profile picture of a user who has not set one. */
internal val PersonIcon: ImageVector = Icons.Default.Person

/** Opens the navigation drawer. */
internal val MenuIcon: ImageVector = Icons.Default.Menu

/** The library screen's sort-and-filter button. */
internal val FilterIcon: ImageVector = Icons.Default.FilterList

/** Empties the search field. */
internal val ClearIcon: ImageVector = Icons.Default.Clear

/** Goes back. Here rather than inside [BackButton], so the whole set stays in one file. */
internal val BackIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

/** A movie library. */
internal val MovieIcon: ImageVector = Icons.Default.Movie

/** A TV library. */
internal val TvIcon: ImageVector = Icons.Default.Tv

/** A playlist library. `PlaylistPlay` rather than `List`: these are things that play, in order. */
internal val PlaylistIcon: ImageVector = Icons.AutoMirrored.Filled.PlaylistPlay

/**
 * A library of collections — box sets.
 *
 * `VideoLibrary` rather than `Collections`, which is a stack of photographs and belongs to a photo
 * library. A box set groups films.
 */
internal val CollectionIcon: ImageVector = Icons.Default.VideoLibrary

/** A library whose type we have no picture for: music, books, photos. */
internal val FolderIcon: ImageVector = Icons.Default.Folder

/**
 * A link that leaves the app, for the server's custom menu links.
 *
 * The arrow is the point: these go to separate services in a browser, and it says so before the tap
 * rather than after.
 */
internal val OpenInNewIcon: ImageVector = Icons.AutoMirrored.Filled.OpenInNew

/**
 * The set side by side, which is the only way to see that it *is* a set — one icon at a time says
 * nothing about whether it matches its neighbours.
 */
@Preview(name = "Icons")
@Composable
private fun IconsPreview() {
    PreviewSurface {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = MenuIcon, contentDescription = "Menu")
            Icon(imageVector = BackIcon, contentDescription = "Back")
            Icon(imageVector = FilterIcon, contentDescription = "Filter")
            Icon(imageVector = SearchIcon, contentDescription = "Search")
            Icon(imageVector = ClearIcon, contentDescription = "Clear")
            Icon(imageVector = CheckIcon, contentDescription = "Watched")
            Icon(imageVector = ChevronIcon, contentDescription = "Show all")
            Icon(imageVector = PersonIcon, contentDescription = "Account")
            Icon(imageVector = MovieIcon, contentDescription = "Movies")
            Icon(imageVector = TvIcon, contentDescription = "TV shows")
            Icon(imageVector = PlaylistIcon, contentDescription = "Playlists")
            Icon(imageVector = CollectionIcon, contentDescription = "Collections")
            Icon(imageVector = FolderIcon, contentDescription = "Library")
            Icon(imageVector = OpenInNewIcon, contentDescription = "Opens outside the app")
        }
    }
}
