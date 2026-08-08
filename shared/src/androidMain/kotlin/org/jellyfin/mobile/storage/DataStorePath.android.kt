package org.jellyfin.mobile.storage

import android.content.Context

/** App-private internal storage — not readable by other apps on a non-rooted device. */
fun Context.dataStoreDirectory(): String = filesDir.absolutePath
