package org.jellyfin.mobile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The preferences a user sets in this client, as opposed to the ones their server holds.
 *
 * Defaults are the values a fresh install gets, and they are stated here rather than at each read
 * site so the two settings screens and the player cannot disagree about them.
 */
data class ClientSettings(
    /** Vertical drags on the player adjust brightness and volume. */
    val brightnessAndVolumeGestures: Boolean = true,
    /**
     * Reapply [lastBrightness] when playback starts, instead of leaving the screen at whatever the
     * system was doing. Off by default: silently overriding screen brightness is a surprise unless
     * it was asked for.
     */
    val rememberBrightness: Boolean = false,
    /**
     * The brightness a gesture last set, 0f..1f, or null if none ever has.
     *
     * Recorded whether or not [rememberBrightness] is on, so switching it on applies the value the
     * user already chose rather than doing nothing until the next drag.
     */
    val lastBrightness: Float? = null,
)

/**
 * Persists [ClientSettings].
 *
 * On a **separate** preferences file from [SessionStore], which signing out clears wholesale. These
 * belong to the person holding the device rather than to the account they happen to be signed into,
 * so they have to outlive it.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    /**
     * Read eagerly and kept hot: the player reads this on the way in, and a suspending read there
     * would mean a frame of gestures behaving as though they were switched off.
     */
    val settings: StateFlow<ClientSettings> = dataStore.data
        .map { preferences ->
            ClientSettings(
                brightnessAndVolumeGestures = preferences[GESTURES] ?: ClientSettings().brightnessAndVolumeGestures,
                rememberBrightness = preferences[REMEMBER_BRIGHTNESS] ?: ClientSettings().rememberBrightness,
                lastBrightness = preferences[LAST_BRIGHTNESS],
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, ClientSettings())

    fun setBrightnessAndVolumeGestures(enabled: Boolean) = write { it[GESTURES] = enabled }

    fun setRememberBrightness(enabled: Boolean) = write { it[REMEMBER_BRIGHTNESS] = enabled }

    fun setLastBrightness(value: Float) = write { it[LAST_BRIGHTNESS] = value.coerceIn(0f, 1f) }

    /**
     * Fire-and-forget, on a scope that outlives any screen.
     *
     * A setting is toggled and then the user moves on; making the caller await the disk would put a
     * suspension point in a checkbox, and a write that loses a race with process death costs one
     * checkbox rather than anything a user would notice.
     */
    private fun write(block: (MutablePreferences) -> Unit) {
        scope.launch { dataStore.edit(block) }
    }

    private companion object {
        val GESTURES = booleanPreferencesKey("brightness_and_volume_gestures")
        val REMEMBER_BRIGHTNESS = booleanPreferencesKey("remember_brightness")
        val LAST_BRIGHTNESS = floatPreferencesKey("last_brightness")
    }
}
