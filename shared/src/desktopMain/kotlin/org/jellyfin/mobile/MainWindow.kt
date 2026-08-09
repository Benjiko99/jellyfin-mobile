package org.jellyfin.mobile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.app_name
import org.jellyfin.mobile.storage.dataStoreDirectory
import org.jellyfin.mobile.ui.App
import org.jetbrains.compose.resources.stringResource

/**
 * The app's window, here rather than in `:desktopApp` for the same reason `MainViewController` is
 * here rather than in `iosApp/`: the entry point module is the platform's `main()` and nothing else,
 * and the title is a translated string that only this module can read (`Res` is internal to it).
 *
 * A window has no modifier and no state to hoist — it is the composition root, the same exemption
 * [org.jellyfin.mobile.ui.App] takes.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun ApplicationScope.MainWindow() {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        // Large enough for the home screen's rows to show more than one card without being told to,
        // and centred because the platform default stacks successive windows down and right from a
        // corner — which for a single-window app just means "not where you were looking".
        state = rememberWindowState(
            size = DpSize(1280.dp, 800.dp),
            position = WindowPosition(Alignment.Center),
        ),
    ) {
        App(dataStoreDirectory())
    }
}
