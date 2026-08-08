package org.jellyfin.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.jellyfin.mobile.storage.dataStoreDirectory
import org.jellyfin.mobile.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draws under the system bars. The bar *appearance* it picks here is the device's dark-mode
        // setting, which is not what the app draws in — `SystemBarAppearance` calls this again from
        // inside the theme, with the app's own choice, as soon as there is a composition.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(applicationContext.dataStoreDirectory())
        }
    }
}
