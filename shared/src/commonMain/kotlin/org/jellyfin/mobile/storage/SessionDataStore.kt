package org.jellyfin.mobile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

const val SESSION_FILE_NAME = "session.preferences_pb"

/**
 * Creates (or opens) the preferences file backing [SessionStore].
 *
 * The absolute path is supplied by the platform entry point rather than modelled as an
 * `expect class PlatformContext`: Android's `Context` is abstract, so it cannot satisfy the
 * implicit constructor of an expect class, and a path is all this layer actually needs.
 * See `sessionFilePath()` in `androidMain`/`iosMain`.
 */
fun createSessionDataStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { path.toPath() }
