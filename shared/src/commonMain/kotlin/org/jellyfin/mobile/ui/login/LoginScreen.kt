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
import org.jellyfin.mobile.ui.preview.PreviewSurface

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
                    text = "Connect to Jellyfin",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.10:8096") },
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
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
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
                        text = error,
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
                    Text("Sign in")
                }
            }
        }
    }
}

private const val PreviewWidth = 390
private const val PreviewHeight = 844

/** Nothing typed: "Sign in" stays disabled until there is at least an address and a username. */
@Preview(name = "Login · empty", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginEmptyPreview() {
    PreviewSurface {
        LoginScreenPreview(LoginState())
    }
}

@Preview(name = "Login · filled", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginFilledPreview() {
    PreviewSurface {
        LoginScreenPreview(
            LoginState(serverUrl = "192.168.1.10:8096", username = "elena", password = "hunter2"),
        )
    }
}

/** Mid-request: every field is disabled and the button carries a spinner. */
@Preview(name = "Login · signing in", widthDp = PreviewWidth, heightDp = PreviewHeight)
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

@Preview(name = "Login · error", widthDp = PreviewWidth, heightDp = PreviewHeight)
@Composable
private fun LoginErrorPreview() {
    PreviewSurface {
        LoginScreenPreview(
            LoginState(
                serverUrl = "192.168.1.10:8096",
                username = "elena",
                error = "Could not reach a Jellyfin server at http://192.168.1.10:8096",
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
