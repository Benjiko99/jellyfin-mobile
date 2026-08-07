package org.jellyfin.mobile.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.domain.UiText
import org.jellyfin.mobile.resources.Res
import org.jellyfin.mobile.resources.login_error_unreachable
import org.jellyfin.mobile.resources.login_password
import org.jellyfin.mobile.resources.login_server_address
import org.jellyfin.mobile.resources.login_server_address_hint
import org.jellyfin.mobile.resources.login_sign_in
import org.jellyfin.mobile.resources.login_title
import org.jellyfin.mobile.resources.login_username
import org.jellyfin.mobile.ui.preview.PreviewSurface
import org.jellyfin.mobile.ui.resolve
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    state: LoginState,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.login_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text(stringResource(Res.string.login_server_address)) },
                    placeholder = { Text(stringResource(Res.string.login_server_address_hint)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(Res.string.login_username)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(Res.string.login_password)) },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let { error ->
                    Text(
                        text = error.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = onSubmit,
                    enabled = !state.busy && state.serverUrl.isNotBlank() && state.username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(stringResource(Res.string.login_sign_in))
                }
            }
        }
    }
}

/** Nothing typed: "Sign in" stays disabled until there is at least an address and a username. */
@Preview(name = "Login · empty")
@Composable
private fun LoginEmptyPreview() {
    PreviewSurface {
        LoginScreenPreview(LoginState())
    }
}

@Preview(name = "Login · filled")
@Composable
private fun LoginFilledPreview() {
    PreviewSurface {
        LoginScreenPreview(
            LoginState(serverUrl = "192.168.1.10:8096", username = "elena", password = "hunter2"),
        )
    }
}

/** Mid-request: every field is disabled and the button carries a spinner. */
@Preview(name = "Login · signing in")
@Composable
private fun LoginBusyPreview() {
    PreviewSurface {
        LoginScreenPreview(
            LoginState(
                serverUrl = "192.168.1.10:8096",
                username = "elena",
                password = "hunter2",
                busy = true,
            ),
        )
    }
}

@Preview(name = "Login · error")
@Composable
private fun LoginErrorPreview() {
    PreviewSurface {
        LoginScreenPreview(
            LoginState(
                serverUrl = "192.168.1.10:8096",
                username = "elena",
                error = UiText.Resource(
                    Res.string.login_error_unreachable,
                    listOf("http://192.168.1.10:8096"),
                ),
            ),
        )
    }
}

@Composable
private fun LoginScreenPreview(state: LoginState) {
    LoginScreen(
        state = state,
        onServerUrlChange = {},
        onUsernameChange = {},
        onPasswordChange = {},
        onSubmit = {},
    )
}
