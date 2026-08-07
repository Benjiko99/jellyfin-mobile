package org.jellyfin.mobile.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.LibraryKind
import org.jellyfin.mobile.domain.LibraryView
import org.jellyfin.mobile.domain.MenuLink
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.drawer_media
import org.jellyfin.mobile.ui.components.CollectionIcon
import org.jellyfin.mobile.ui.components.FolderIcon
import org.jellyfin.mobile.ui.components.MovieIcon
import org.jellyfin.mobile.ui.components.OpenInNewIcon
import org.jellyfin.mobile.ui.components.PlaylistIcon
import org.jellyfin.mobile.ui.components.TvIcon
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jetbrains.compose.resources.stringResource

/**
 * Text in a drawer sheet sits 28.dp from its edge: the sheet's own 12.dp of item padding plus the
 * 16.dp a [NavigationDrawerItem] insets its label by. Headings repeat the sum so they line up with
 * the rows under them.
 */
private val DrawerPadding = 28.dp

/**
 * The icon for a library, from its type.
 *
 * Here rather than on [LibraryKind] because an `ImageVector` is a UI concern and the domain does not
 * import Compose. The fallback matters more than it looks: an administrator can point a library at
 * any of thirteen collection types, and the ones we have no picture for — music, books, photos —
 * still belong in the drawer with something beside them.
 */
private val LibraryKind.icon: ImageVector
    get() = when (this) {
        LibraryKind.Movies -> MovieIcon
        LibraryKind.TvShows -> TvIcon
        LibraryKind.Playlists -> PlaylistIcon
        LibraryKind.Collections -> CollectionIcon
        LibraryKind.Other -> FolderIcon
    }

/**
 * The navigation drawer behind the home screen's app bar.
 *
 * [menuLinks] are the administrator's own entries, and they come first because they are the ones a
 * particular server went out of its way to offer — a request page most often, which is the whole
 * reason this drawer has anything above "Media". They are absent on most servers; the section
 * disappears with them rather than leaving a heading over nothing.
 *
 * [libraries] are the server's own, in the order the user arranged them there — not a fixed list of
 * four. An administrator names and types every library, so a server can have two movie libraries,
 * one called "Films", or a music library we have no browse view for; the "Media" section is
 * whatever `/UserViews` says it is.
 */
@Composable
internal fun HomeDrawerSheet(
    drawerState: DrawerState,
    menuLinks: List<MenuLink>,
    libraries: List<LibraryView>,
    onMenuLinkClick: (MenuLink) -> Unit,
    onLibraryClick: (LibraryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(drawerState = drawerState, modifier = modifier) {
        // A server with a handful of libraries and an administrator who has added links above them
        // runs past a phone held sideways.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))

            if (menuLinks.isNotEmpty()) {
                menuLinks.forEach { link ->
                    // Every one of these gets the same icon. The web config has an `icon` field per
                    // link, but it names a glyph in the Material Icons font, which we do not ship —
                    // and "this leaves the app" is the more useful thing to say about all of them
                    // than whichever picture an administrator picked.
                    DrawerRow(link.name, OpenInNewIcon) { onMenuLinkClick(link) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Held back until the libraries arrive: a "Media" heading over nothing looks like a
            // server with no libraries rather than a request still in flight.
            if (libraries.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.drawer_media),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = DrawerPadding, vertical = 8.dp),
                )

                libraries.forEach { library ->
                    DrawerRow(library.name, library.kind.icon) { onLibraryClick(library) }
                }
            }
        }
    }
}

/**
 * Every row is unselected: the drawer leads away from the home screen rather than switching between
 * places inside it, so there is nothing here for it to mark as where you already are.
 *
 * The icon carries no `contentDescription`. It restates the label beside it, and a screen reader
 * announcing "Movies, movies" is worse than one announcing "Movies".
 */
@Composable
private fun DrawerRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

/**
 * The sheet on its own. A whole-screen preview of [HomeScreen] cannot show it — whether the drawer
 * is open is internal state, and it always starts closed.
 */
@Preview(name = "Navigation drawer")
@Composable
private fun HomeDrawerSheetPreview() {
    PreviewSurface {
        HomeDrawerSheet(
            drawerState = rememberDrawerState(DrawerValue.Open),
            menuLinks = PreviewData.menuLinks,
            libraries = PreviewData.libraries,
            onMenuLinkClick = {},
            onLibraryClick = {},
        )
    }
}

/**
 * What most servers show: nobody has edited `config.json`, so the drawer is the library list and
 * nothing else. Worth its own preview — the divider and the top spacing have to survive the section
 * above them being absent.
 */
@Preview(name = "Navigation drawer · no server links")
@Composable
private fun HomeDrawerSheetNoLinksPreview() {
    PreviewSurface {
        HomeDrawerSheet(
            drawerState = rememberDrawerState(DrawerValue.Open),
            menuLinks = emptyList(),
            libraries = PreviewData.libraries,
            onMenuLinkClick = {},
            onLibraryClick = {},
        )
    }
}

/** The first moment after opening the drawer, before /UserViews has answered. */
@Preview(name = "Navigation drawer · loading")
@Composable
private fun HomeDrawerSheetLoadingPreview() {
    PreviewSurface {
        HomeDrawerSheet(
            drawerState = rememberDrawerState(DrawerValue.Open),
            menuLinks = emptyList(),
            libraries = emptyList(),
            onMenuLinkClick = {},
            onLibraryClick = {},
        )
    }
}
