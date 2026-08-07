package org.jellyfin.mobile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.network.Session

/**
 * Persists the signed-in session across launches.
 *
 * SECURITY: the access token is stored unencrypted in app-private storage. That matches what
 * jellyfin-android does, and app-private files are unreadable by other apps on a non-rooted
 * device — but it is not equivalent to the Keychain (iOS) or Keystore-backed storage (Android),
 * which is where the token should end up. Tracked as a follow-up; do not treat this as final.
 */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun read(): Session? {
        val preferences = dataStore.data.first()
        val serverUrl = preferences[SERVER_URL] ?: return null
        val accessToken = preferences[ACCESS_TOKEN] ?: return null
        val userId = preferences[USER_ID] ?: return null
        return Session(
            serverUrl = serverUrl,
            accessToken = accessToken,
            userId = userId,
            userName = preferences[USER_NAME].orEmpty(),
            userImageTag = preferences[USER_IMAGE_TAG],
        )
    }

    suspend fun write(session: Session) {
        dataStore.edit { preferences ->
            preferences[SERVER_URL] = session.serverUrl
            preferences[ACCESS_TOKEN] = session.accessToken
            preferences[USER_ID] = session.userId
            preferences[USER_NAME] = session.userName
            // Removed rather than left behind, so a user who deletes their picture does not get a
            // stale tag restored on the next launch.
            session.userImageTag
                ?.let { preferences[USER_IMAGE_TAG] = it }
                ?: preferences.remove(USER_IMAGE_TAG)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_IMAGE_TAG = stringPreferencesKey("user_image_tag")
    }
}
