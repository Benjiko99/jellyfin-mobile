package org.jellyfin.mobile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

const val SESSION_FILE_NAME = "session.preferences_pb"

/**
 * Separate from [SESSION_FILE_NAME] on purpose: signing out clears the session file wholesale, and
 * client settings belong to the device rather than to the account.
 */
const val SETTINGS_FILE_NAME = "settings.preferences_pb"

/**
 * Creates (or opens) one of the app's preferences files, by name, inside [directory].
 *
 * The directory is supplied by the platform entry point rather than modelled as an
 * `expect class PlatformContext`: Android's `Context` is abstract, so it cannot satisfy the
 * implicit constructor of an expect class, and a path is all this layer actually needs.
 * See `dataStoreDirectory()` in `androidMain`/`iosMain`.
 */
fun createDataStore(directory: String, fileName: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath { "$directory/$fileName".toPath() }
