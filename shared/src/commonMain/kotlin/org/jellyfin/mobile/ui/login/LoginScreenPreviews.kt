package org.jellyfin.mobile.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.jellyfin.mobile.ui.preview.PreviewSurface

/**
 * Previews for the connect screen.
 *
 * The interesting states are the ones a user actually gets stuck in: an empty form with the button
 * disabled, a submitted form with everything locked, and a rejection.
 */

private const val PreviewWidth = 390
private const val PreviewHeight = 844

/** Nothing typed: "Sign in" is disabled until there is at least an address and a username. */
@Preview(name = "Login · empty", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginEmptyPreview() {
    PreviewSurface {
        LoginScreen(
            state = LoginState(),
            onServerUrlChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Login · filled", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginFilledPreview() {
    PreviewSurface {
        LoginScreen(
            state = LoginState(
                serverUrl = "192.168.1.10:8096",
                username = "elena",
                password = "hunter2",
            ),
            onServerUrlChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

/** Mid-request: every field is disabled and the button carries a spinner. */
@Preview(name = "Login · signing in", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginBusyPreview() {
    PreviewSurface {
        LoginScreen(
            state = LoginState(
                serverUrl = "192.168.1.10:8096",
                username = "elena",
                password = "hunter2",
                busy = true,
            ),
            onServerUrlChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}

@Preview(name = "Login · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginErrorPreview() {
    PreviewSurface {
        LoginScreen(
            state = LoginState(
                serverUrl = "192.168.1.10:8096",
                username = "elena",
                error = "Could not reach a Jellyfin server at http://192.168.1.10:8096",
            ),
            onServerUrlChange = {},
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
        )
    }
}
