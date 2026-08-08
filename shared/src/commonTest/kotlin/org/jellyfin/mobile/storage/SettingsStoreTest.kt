package org.jellyfin.mobile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An in-memory stand-in for the preferences file.
 *
 * The real `PreferenceDataStoreFactory` wants a writable path, which a common test does not have on
 * both targets. Only the read-modify-write contract matters here.
 */
private class FakeDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @Test
    fun `a fresh install gets gestures on and remembered brightness off`() = runTest(UnconfinedTestDispatcher()) {
        val store = SettingsStore(FakeDataStore(), backgroundScope)

        val settings = store.settings.value

        assertTrue(settings.brightnessAndVolumeGestures)
        assertFalse(settings.rememberBrightness)
        assertNull(settings.lastBrightness)
    }

    @Test
    fun `a fresh install is dark rather than following the device`() = runTest(UnconfinedTestDispatcher()) {
        // Dark is the branding. Following a phone in light mode is a choice the user makes.
        val store = SettingsStore(FakeDataStore(), backgroundScope)

        assertEquals(ThemePreference.Dark, store.settings.value.theme)
    }

    @Test
    fun `reads back what was written`() = runTest(UnconfinedTestDispatcher()) {
        val store = SettingsStore(FakeDataStore(), backgroundScope)

        store.setTheme(ThemePreference.System)
        store.setBrightnessAndVolumeGestures(false)
        store.setRememberBrightness(true)
        store.setLastBrightness(0.42f)

        val settings = store.settings.value
        assertEquals(ThemePreference.System, settings.theme)
        assertFalse(settings.brightnessAndVolumeGestures)
        assertTrue(settings.rememberBrightness)
        assertEquals(0.42f, settings.lastBrightness)
    }

    @Test
    fun `a theme it does not recognise falls back to the default`() = runTest(UnconfinedTestDispatcher()) {
        // A settings file written by a newer build, or by a build that spelled an entry
        // differently. Reading it must not take the app down on launch.
        val stored = mutablePreferencesOf(stringPreferencesKey("theme") to "Sepia")
        val store = SettingsStore(FakeDataStore(stored), backgroundScope)

        assertEquals(ThemePreference.Dark, store.settings.value.theme)
    }

    @Test
    fun `an absent key falls back to its default rather than to false`() = runTest(UnconfinedTestDispatcher()) {
        // Only one of the two is stored. The other must not come back off, which is what a bare
        // `?: false` would have done to a setting that defaults on.
        val stored = mutablePreferencesOf(booleanPreferencesKey("remember_brightness") to true)
        val store = SettingsStore(FakeDataStore(stored), backgroundScope)

        val settings = store.settings.value

        assertTrue(settings.rememberBrightness)
        assertTrue(settings.brightnessAndVolumeGestures)
    }

    @Test
    fun `clamps a brightness outside the range instead of storing it`() = runTest(UnconfinedTestDispatcher()) {
        val store = SettingsStore(FakeDataStore(), backgroundScope)

        store.setLastBrightness(1.5f)
        assertEquals(1f, store.settings.value.lastBrightness)

        store.setLastBrightness(-0.2f)
        assertEquals(0f, store.settings.value.lastBrightness)
    }

    @Test
    fun `records the last brightness even while remembering is off`() = runTest(UnconfinedTestDispatcher()) {
        // So that switching the setting on later applies the brightness the user already chose,
        // rather than doing nothing until the next drag.
        val store = SettingsStore(FakeDataStore(), backgroundScope)

        store.setLastBrightness(0.3f)

        assertFalse(store.settings.value.rememberBrightness)
        assertEquals(0.3f, store.settings.value.lastBrightness)
    }

    @Test
    fun `stores brightness under a key the session store does not use`() = runTest(UnconfinedTestDispatcher()) {
        // The two stores are separate files, but a shared key name would still be a trap if they
        // were ever merged. This pins the name the reader expects.
        val dataStore = FakeDataStore()
        SettingsStore(dataStore, backgroundScope).setLastBrightness(0.6f)

        var seen: Float? = null
        dataStore.updateData { preferences ->
            seen = preferences[floatPreferencesKey("last_brightness")]
            preferences
        }
        assertEquals(0.6f, seen)
    }
}
