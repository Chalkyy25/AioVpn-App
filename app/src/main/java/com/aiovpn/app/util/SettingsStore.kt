package com.aiovpn.app.util

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.settingsDataStore by preferencesDataStore(name = "aio_settings")

class SettingsStore(private val context: Context) {

    enum class SplitTunnelMode {
        OFF,
        EXCLUDE_APPS,
        INCLUDE_ONLY_APPS
    }

    private val KEY_REQUEST_SYSTEM_KILL_SWITCH =
        booleanPreferencesKey("request_system_kill_switch")

    private val KEY_AUTO_CONNECT =
        booleanPreferencesKey("auto_connect")

    private val KEY_LAST_SERVER_ID =
        intPreferencesKey("last_server_id")

    private val KEY_LAST_SERVER_LABEL =
        stringPreferencesKey("last_server_label")

    private val KEY_SPLIT_TUNNEL_MODE =
        stringPreferencesKey("split_tunnel_mode")

    private val KEY_EXCLUDED_APPS =
        stringSetPreferencesKey("excluded_apps")

    private val KEY_INCLUDED_APPS =
        stringSetPreferencesKey("included_apps")

    // ===== FLOWS =====

    val requestSystemKillSwitchFlow: Flow<Boolean> =
        context.settingsDataStore.data.map {
            it[KEY_REQUEST_SYSTEM_KILL_SWITCH] ?: false
        }

    val autoConnectFlow: Flow<Boolean> =
        context.settingsDataStore.data.map {
            it[KEY_AUTO_CONNECT] ?: false
        }

    val lastServerIdFlow: Flow<Int?> =
        context.settingsDataStore.data.map {
            it[KEY_LAST_SERVER_ID]
        }

    val lastServerLabelFlow: Flow<String?> =
        context.settingsDataStore.data.map {
            it[KEY_LAST_SERVER_LABEL]
        }

    val splitTunnelModeFlow: Flow<SplitTunnelMode> =
        context.settingsDataStore.data.map { prefs ->
            when (prefs[KEY_SPLIT_TUNNEL_MODE]) {
                SplitTunnelMode.EXCLUDE_APPS.name -> SplitTunnelMode.EXCLUDE_APPS
                SplitTunnelMode.INCLUDE_ONLY_APPS.name -> SplitTunnelMode.INCLUDE_ONLY_APPS
                else -> SplitTunnelMode.OFF
            }
        }

    val excludedAppsFlow: Flow<Set<String>> =
        context.settingsDataStore.data.map {
            it[KEY_EXCLUDED_APPS] ?: emptySet()
        }

    val includedAppsFlow: Flow<Set<String>> =
        context.settingsDataStore.data.map {
            it[KEY_INCLUDED_APPS] ?: emptySet()
        }

    // ===== SETTERS =====

    suspend fun setRequestSystemKillSwitch(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_REQUEST_SYSTEM_KILL_SWITCH] = enabled
        }
    }

    suspend fun clearLastServer() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_LAST_SERVER_ID)
            prefs.remove(KEY_LAST_SERVER_LABEL)
        }
    }

    suspend fun setAutoConnect(enabled: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_AUTO_CONNECT] = enabled
        }
    }

    suspend fun saveLastServer(id: Int, label: String) {
        context.settingsDataStore.edit {
            it[KEY_LAST_SERVER_ID] = id
            it[KEY_LAST_SERVER_LABEL] = label
        }
    }

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        context.settingsDataStore.edit {
            it[KEY_SPLIT_TUNNEL_MODE] = mode.name
        }
    }

    suspend fun setExcludedApps(packageNames: Set<String>) {
        context.settingsDataStore.edit {
            it[KEY_EXCLUDED_APPS] = packageNames
        }
    }

    suspend fun setIncludedApps(packageNames: Set<String>) {
        context.settingsDataStore.edit {
            it[KEY_INCLUDED_APPS] = packageNames
        }
    }

    suspend fun clearSplitTunnelApps() {
        context.settingsDataStore.edit {
            it[KEY_EXCLUDED_APPS] = emptySet()
            it[KEY_INCLUDED_APPS] = emptySet()
        }
    }

    // ===== BLOCKING (FOR JAVABACKEND) =====

    fun getSplitTunnelModeBlocking(): SplitTunnelMode = runBlocking {
        splitTunnelModeFlow.first()
    }

    fun getExcludedAppsBlocking(): Set<String> = runBlocking {
        excludedAppsFlow.first()
    }

    fun getIncludedAppsBlocking(): Set<String> = runBlocking {
        includedAppsFlow.first()
    }
}