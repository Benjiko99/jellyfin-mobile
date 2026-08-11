package org.jellyfin.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.app_name
import org.jellyfin.mobile.storage.dataStoreDirectory
import org.jellyfin.mobile.ui.App
import org.jetbrains.compose.resources.stringResource

/**
 * The window's own state, offered to whatever is drawn inside it.
 *
 * The player is the one thing below here that has to move the window rather than draw in it, and it
 * is several screens down: carrying a parameter to it would put a desktop-shaped argument through
 * shared navigation, and reaching for the focused AWT window instead — which is what this replaced —
 * changes the window behind Compose's back. `WindowState` is the thing Compose watches, so writing
 * to it is the only way a change is followed through: placement, and the size and position to put
 * back when the placement is undone.
 *
 * Null where nothing provides it, which is a test harness rather than the app.
 *
 * A CompositionLocal is an implicit dependency and ktlint asks for a reason before allowing one;
 * this one's is in `.editorconfig` beside the allowlist that names it.
 */
internal val LocalWindowState = staticCompositionLocalOf<WindowState?> { null }

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
    // Large enough for the home screen's rows to show more than one card without being told to, and
    // centred because the platform default stacks successive windows down and right from a corner —
    // which for a single-window app just means "not where you were looking".
    val state = rememberWindowState(
        size = DpSize(1280.dp, 800.dp),
        position = WindowPosition(Alignment.Center),
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = state,
    ) {
        CompositionLocalProvider(LocalWindowState provides state) {
            App(dataStoreDirectory())
        }
    }
}
