package org.jellyfin.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.settings_appearance_heading
import org.jellyfin.mobile.resources.settings_client
import org.jellyfin.mobile.resources.settings_gestures_brightness_only
import org.jellyfin.mobile.resources.settings_gestures_summary
import org.jellyfin.mobile.resources.settings_gestures_title
import org.jellyfin.mobile.resources.settings_playback_heading
import org.jellyfin.mobile.resources.settings_remember_brightness_summary
import org.jellyfin.mobile.resources.settings_remember_brightness_title
import org.jellyfin.mobile.resources.settings_theme_dark
import org.jellyfin.mobile.resources.settings_theme_light
import org.jellyfin.mobile.resources.settings_theme_summary
import org.jellyfin.mobile.resources.settings_theme_system
import org.jellyfin.mobile.resources.settings_theme_title
import org.jellyfin.mobile.storage.ClientSettings
import org.jellyfin.mobile.storage.ThemePreference
import org.jellyfin.mobile.ui.components.BackButton
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The preferences that belong to this client rather than to the server.
 *
 * The first settings screen in the app. [org.jellyfin.mobile.ui.home.SettingsEntry] lists four more
 * that do not exist yet, so the layout here is deliberately plain — a heading and rows — and worth
 * lifting into shared components only once there is a second screen to agree with.
 *
 * @param volumeGesturesSupported false where the platform offers no way to set the volume, which
 * turns the gesture row into a brightness-only one and says so rather than quietly doing half of
 * what its title promises.
 * @param brightnessGesturesSupported false where the platform offers no way to set the screen's
 * brightness. With [volumeGesturesSupported] also false — desktop — there is no gesture left to
 * configure, so the whole playback section goes rather than being left as switches that do nothing.
 */
@Composable
fun ClientSettingsScreen(
    settings: ClientSettings,
    onBack: () -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onToggleGestures: (Boolean) -> Unit,
    onToggleRememberBrightness: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    volumeGesturesSupported: Boolean = true,
    brightnessGesturesSupported: Boolean = true,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_client)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsHeading(Res.string.settings_appearance_heading)

            ThemePicker(selected = settings.theme, onSelect = onSelectTheme)

            // Both rows below are about the drag gestures, and the heading has nothing else under
            // it, so a platform with neither gesture loses the section entirely rather than being
            // shown a heading over an empty space.
            if (brightnessGesturesSupported || volumeGesturesSupported) {
                SettingsHeading(Res.string.settings_playback_heading)

                CheckboxRow(
                    title = stringResource(Res.string.settings_gestures_title),
                    summary = stringResource(Res.string.settings_gestures_summary) +
                        if (volumeGesturesSupported) {
                            ""
                        } else {
                            // Joined rather than a second Text: it qualifies the sentence above it,
                            // and as its own row it would read as a separate setting.
                            "\n\n" + stringResource(Res.string.settings_gestures_brightness_only)
                        },
                    checked = settings.brightnessAndVolumeGestures,
                    onCheckedChange = onToggleGestures,
                )

                if (brightnessGesturesSupported) {
                    CheckboxRow(
                        title = stringResource(Res.string.settings_remember_brightness_title),
                        summary = stringResource(Res.string.settings_remember_brightness_summary),
                        checked = settings.rememberBrightness,
                        onCheckedChange = onToggleRememberBrightness,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeading(text: StringResource) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = RowPadding, end = RowPadding, top = 20.dp, bottom = 4.dp),
    )
}

/** What each choice is called. Kept here rather than on the enum, which `storage` owns. */
private fun ThemePreference.label(): StringResource = when (this) {
    ThemePreference.System -> Res.string.settings_theme_system
    ThemePreference.Light -> Res.string.settings_theme_light
    ThemePreference.Dark -> Res.string.settings_theme_dark
}

/**
 * The three schemes, as radio buttons.
 *
 * Radios rather than a dropdown or a switch: there are three of them, they are mutually exclusive,
 * and all three fit on screen — so the list shows what the alternatives are without a tap. The whole
 * group is one `selectableGroup`, which is what lets a screen reader announce "2 of 3" instead of
 * reading out three unrelated radio buttons.
 */
@Composable
private fun ThemePicker(selected: ThemePreference, onSelect: (ThemePreference) -> Unit) {
    // The group carries the semantics, so the label and the summary sit inside it: a screen reader
    // reaching the radios has already been told what they are choosing between.
    Column(Modifier.selectableGroup()) {
        Text(
            text = stringResource(Res.string.settings_theme_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = RowPadding, vertical = 4.dp),
        )
        Text(
            text = stringResource(Res.string.settings_theme_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = RowPadding, end = RowPadding, bottom = 8.dp),
        )

        ThemePreference.entries.forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = theme == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(theme) },
                    )
                    .padding(horizontal = RowPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Null for the same reason as the checkbox below: the row owns the click.
                RadioButton(selected = theme == selected, onClick = null)
                Text(
                    text = stringResource(theme.label()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

/**
 * A checkbox with its label.
 *
 * The whole row toggles, not just the box: a 20dp target beside a two-line label is the harder
 * thing to hit, and `toggleable` with [Role.Checkbox] gives a screen reader one control announcing
 * the title rather than a checkbox and some unrelated text.
 */
@Composable
private fun CheckboxRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(horizontal = RowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Null, because the row above owns the click and the semantics. A checkbox with its own
        // handler here would be a second, competing control in the accessibility tree.
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

private val RowPadding = 20.dp

@Preview(name = "Client settings")
@Composable
private fun ClientSettingsPreview() {
    PreviewSurface {
        ClientSettingsScreen(
            settings = ClientSettings(),
            onBack = {},
            onSelectTheme = {},
            onToggleGestures = {},
            onToggleRememberBrightness = {},
        )
    }
}

/** The screen a user who picked Light sees, which is the only way to look at the light scheme. */
@Preview(name = "Client settings · light")
@Composable
private fun ClientSettingsLightPreview() {
    PreviewSurface(darkTheme = false) {
        ClientSettingsScreen(
            settings = ClientSettings(theme = ThemePreference.Light),
            onBack = {},
            onSelectTheme = {},
            onToggleGestures = {},
            onToggleRememberBrightness = {},
        )
    }
}

/** What desktop shows: no gesture either way, so the playback section is not there at all. */
@Preview(name = "Client settings · no gestures")
@Composable
private fun ClientSettingsNoGesturesPreview() {
    PreviewSurface {
        ClientSettingsScreen(
            settings = ClientSettings(),
            onBack = {},
            onSelectTheme = {},
            onToggleGestures = {},
            onToggleRememberBrightness = {},
            volumeGesturesSupported = false,
            brightnessGesturesSupported = false,
        )
    }
}

/** Both on, and the brightness-only note that iOS gets. */
@Preview(name = "Client settings · brightness only")
@Composable
private fun ClientSettingsBrightnessOnlyPreview() {
    PreviewSurface {
        ClientSettingsScreen(
            settings = ClientSettings(brightnessAndVolumeGestures = true, rememberBrightness = true),
            onBack = {},
            onSelectTheme = {},
            onToggleGestures = {},
            onToggleRememberBrightness = {},
            volumeGesturesSupported = false,
        )
    }
}
