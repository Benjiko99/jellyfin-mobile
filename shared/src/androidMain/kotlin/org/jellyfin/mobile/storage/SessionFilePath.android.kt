package org.jellyfin.mobile.storage

import android.content.Context

/** App-private internal storage — not readable by other apps on a non-rooted device. */
fun Context.sessionFilePath(): String =
    filesDir.resolve(SESSION_FILE_NAME).absolutePath
