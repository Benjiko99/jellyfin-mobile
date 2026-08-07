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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.MenuLink
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Text in a drawer sheet sits 28.dp from its edge: the sheet's own 12.dp of item padding plus the
 * 16.dp a [NavigationDrawerItem] insets its label by. Headings repeat the sum so they line up with
 * the rows under them.
 */
private val DrawerPadding = 28.dp

/**
 * The libraries under the drawer's "Media" heading.
 *
 * Fixed rather than read from `/UserViews`, which is what will eventually fill this list: a server's
 * libraries are named and typed by whoever set it up, so the real drawer has to ask. These are the
 * four every install ends up with, and they settle the shape of the sheet before that query lands.
 */
internal enum class LibraryEntry(val label: String) {
    TvShows("TV Shows"),
    Movies("Movies"),
    Playlists("Playlists"),
    Collections("Collections"),
}

/**
 * The navigation drawer behind the home screen's app bar.
 *
 * [menuLinks] are the administrator's own entries, and they come first because they are the ones a
 * particular server went out of its way to offer — a request page most often, which is the whole
 * reason this drawer has anything above "Media". They are absent on most servers; the section
 * disappears with them rather than leaving a heading over nothing.
 *
 * The library rows below link nowhere yet, so — as in [UserMenuDialog] — their callback is a
 * placeholder and they deliberately leave the drawer open: a tap that goes nowhere should not also
 * look like it dismissed the sheet.
 */
@Composable
internal fun HomeDrawerSheet(
    drawerState: DrawerState,
    menuLinks: List<MenuLink>,
    onMenuLinkClick: (MenuLink) -> Unit,
    onLibraryClick: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(drawerState = drawerState, modifier = modifier) {
        // Five rows and a heading clear a phone in portrait, but not one held sideways — and an
        // administrator can add as many links above them as they like.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(modifier = Modifier.height(12.dp))

            if (menuLinks.isNotEmpty()) {
                menuLinks.forEach { link ->
                    DrawerRow(link.name) { onMenuLinkClick(link) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            Text(
                text = "Media",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = DrawerPadding, vertical = 8.dp),
            )

            LibraryEntry.entries.forEach { entry ->
                DrawerRow(entry.label) { onLibraryClick(entry) }
            }
        }
    }
}

/**
 * Every row is unselected: the drawer leads away from the home screen rather than switching between
 * places inside it, so there is nothing here for it to mark as where you already are.
 */
@Composable
private fun DrawerRow(label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
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
            onMenuLinkClick = {},
            onLibraryClick = {},
        )
    }
}
