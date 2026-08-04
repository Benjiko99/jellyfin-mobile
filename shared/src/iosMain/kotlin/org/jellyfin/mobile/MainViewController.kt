package org.jellyfin.mobile

import androidx.compose.ui.window.ComposeUIViewController
import org.jellyfin.mobile.storage.sessionFilePath
import org.jellyfin.mobile.ui.App

fun MainViewController() = ComposeUIViewController { App(sessionFilePath()) }
