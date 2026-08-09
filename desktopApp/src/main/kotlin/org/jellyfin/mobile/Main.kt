package org.jellyfin.mobile

import androidx.compose.ui.window.application

// Everything this shows is in `:shared`, including the window itself — see `MainWindow`. Keep this
// file the entry point and nothing more, the way `iosApp/` only calls `MainViewController()`.
fun main() = application { MainWindow() }
