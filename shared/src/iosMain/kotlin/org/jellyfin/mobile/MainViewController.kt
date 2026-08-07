package org.jellyfin.mobile

import androidx.compose.ui.window.ComposeUIViewController
import org.jellyfin.mobile.storage.sessionFilePath
import org.jellyfin.mobile.ui.App

// PascalCase because Swift calls this as a type-like factory from `iosApp/`; renaming it would
// break the SwiftUI glue.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { App(sessionFilePath()) }
