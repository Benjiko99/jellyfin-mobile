package org.jellyfin.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.jellyfin.mobile.ui.components.PersonIcon
import org.jellyfin.mobile.ui.preview.PreviewData
import org.jellyfin.mobile.ui.preview.PreviewSurface

/** Fits inside an `IconButton`'s 48.dp touch target with room left for the ripple. */
private val AvatarSize = 30.dp

/** The gutter the menu's rows and headings share, so their text sits on one left edge. */
private val MenuPadding = 24.dp

/**
 * The settings screens the menu links to.
 *
 * None of them exist yet — the entries are here so the shape of the menu is settled before the
 * screens land. Named for the sections jellyfin-web splits its preferences into, so someone coming
 * from the web client finds the same things under the same headings.
 */
internal enum class SettingsEntry(val label: String) {
    Display("Display"),
    Home("Home"),
    Playback("Playback"),
    Subtitles("Subtitles"),
    Client("Client settings"),
}

/**
 * The signed-in user, as their profile picture or — far more often, since a picture is something
 * somebody has to upload — a silhouette.
 */
@Composable
internal fun UserAvatarButton(
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Account",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = PersonIcon,
                    contentDescription = "Account",
                    // Inset from the circle it sits in, so the silhouette does not touch the edge.
                    modifier = Modifier.size(AvatarSize - 8.dp),
                )
            }
        }
    }
}

/**
 * Account and settings, behind [UserAvatarButton].
 *
 * Only [onSignOut] does anything today; the rest are placeholders for screens that have yet to be
 * written. They deliberately leave the dialog open, so a tap that does nothing does not also look
 * like it dismissed something.
 *
 * [onSignOut] is the request, not the act — signing out is destructive enough to confirm first, and
 * [SignOutConfirmDialog] is what asks.
 */
@Composable
internal fun UserMenuDialog(
    userName: String,
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onSettingClick: (SettingsEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The platform default width is a fraction of the screen on Android and unset on iOS, so
        // the two would not agree. Sized below instead, the way the player's track picker is.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        UserMenu(
            userName = userName,
            onProfile = onProfile,
            onSwitchServer = onSwitchServer,
            onSignOut = onSignOut,
            onSettingClick = onSettingClick,
            modifier = modifier,
        )
    }
}

/**
 * The card itself, split out from [UserMenuDialog] so the previews below can place it in the
 * surface at a width they choose, rather than depending on how the tooling renders a dialog window.
 */
@Composable
private fun UserMenu(
    userName: String,
    onProfile: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onSettingClick: (SettingsEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth(0.9f),
    ) {
        // Eight rows and two headings do not fit a short screen in landscape.
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            Text(
                text = userName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = MenuPadding, vertical = 16.dp),
            )

            MenuRow("Profile", onProfile)
            MenuRow("Switch server", onSwitchServer)
            MenuRow("Sign out", onSignOut)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MenuPadding, vertical = 8.dp),
            )

            SettingsEntry.entries.forEach { entry ->
                MenuRow(entry.label) { onSettingClick(entry) }
            }
        }
    }
}

/**
 * Confirms signing out.
 *
 * Worth an extra tap: the token is cleared from disk, so the only way back is the full server
 * address, username and password — and on a household server the person holding the phone is not
 * necessarily the one who knows them.
 */
@Composable
internal fun SignOutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = { Text("You will be signed out and will have to sign in again.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        modifier = modifier,
    )
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MenuPadding, vertical = 14.dp),
    )
}

@Preview(name = "User menu")
@Composable
private fun UserMenuPreview() {
    PreviewSurface {
        UserMenu(
            userName = PreviewData.userName,
            onProfile = {},
            onSwitchServer = {},
            onSignOut = {},
            onSettingClick = {},
        )
    }
}

/** A name long enough to run out of dialog, which display names on a shared server often are. */
@Preview(name = "User menu · long name")
@Composable
private fun UserMenuLongNamePreview() {
    PreviewSurface {
        UserMenu(
            userName = "benjamin.the.household.administrator",
            onProfile = {},
            onSwitchServer = {},
            onSignOut = {},
            onSettingClick = {},
        )
    }
}

@Preview(name = "Sign out confirmation")
@Composable
private fun SignOutConfirmDialogPreview() {
    PreviewSurface {
        SignOutConfirmDialog(onConfirm = {}, onDismiss = {})
    }
}

/** Both avatars: a user who has a picture, and the default everyone else gets. */
@Preview(name = "User avatar")
@Composable
private fun UserAvatarButtonPreview() {
    PreviewSurface {
        Column(modifier = Modifier.padding(12.dp)) {
            UserAvatarButton(imageUrl = PreviewData.userImageUrl, onClick = {})
            UserAvatarButton(imageUrl = null, onClick = {})
        }
    }
}
