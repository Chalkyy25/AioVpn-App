package uk.co.aiovpn.android.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "aio_updater")

class UpdatePrefs(private val context: Context) {

    private val KEY_SEEN_VERSION = longPreferencesKey("seen_version")
    private val KEY_CONSENTED_VERSION = longPreferencesKey("consented_version")

    val seenVersionFlow: Flow<Long> =
        context.updateDataStore.data.map { it[KEY_SEEN_VERSION] ?: 0L }

    val consentedVersionFlow: Flow<Long> =
        context.updateDataStore.data.map { it[KEY_CONSENTED_VERSION] ?: 0L }

    suspend fun setSeenVersion(version: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_SEEN_VERSION] = version
        }
    }

    suspend fun setConsentedVersion(version: Long) {
        context.updateDataStore.edit { prefs ->
            prefs[KEY_CONSENTED_VERSION] = version
        }
    }
}