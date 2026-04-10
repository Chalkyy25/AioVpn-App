package uk.co.aiovpn.android.repo

import android.content.Context
import android.util.Log
import uk.co.aiovpn.android.api.AioApi
import uk.co.aiovpn.android.api.WgServerDto
import uk.co.aiovpn.android.auth.TokenStore
import uk.co.aiovpn.android.util.SettingsStore
import kotlinx.coroutines.flow.first

class VpnRepository(context: Context) {

    private val appContext = context.applicationContext
    private val tokenStore = TokenStore(appContext)
    private val settingsStore = SettingsStore(appContext)
    private val api = AioApi()

    suspend fun getToken(): String? = tokenStore.getToken()
    suspend fun getUsername(): String? = tokenStore.getUsername()
    suspend fun getExpiry(): String? = tokenStore.getExpiry()
    suspend fun getDevicesAllowed(): Int? = tokenStore.getDevicesAllowed()

    fun getTokenSync(): String? {
        return tokenStore.getTokenSync()
    }

    suspend fun hasToken(): Boolean {
        return !getToken().isNullOrBlank()
    }

    private suspend fun requireToken(): String {
        return getToken()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No token (user not logged in)")
    }

    suspend fun login(username: String, password: String) {
        Log.d("VpnRepository", "LOGIN_START username=${username.trim()}")

        val response = api.login(username.trim(), password)

        Log.d(
            "VpnRepository",
            "LOGIN_RESPONSE token=${response.token} user=${response.user?.username} expires=${response.user?.expires} max_conn=${response.user?.max_conn}"
        )

        tokenStore.saveAuth(
            token = response.token,
            username = response.user?.username ?: username.trim(),
            expiry = response.user?.expires,
            devicesAllowed = response.user?.max_conn
        )

        Log.d("VpnRepository", "LOGIN_SAVE_COMPLETE token=${tokenStore.getTokenSync()}")
    }

    suspend fun refreshProfile() {
        val token = requireToken()
        val profile = api.profile(token)

        tokenStore.saveAuth(
            token = token,
            username = profile.username,
            expiry = profile.expires,
            devicesAllowed = profile.max_conn
        )
    }

    suspend fun servers(): List<WgServerDto> {
        val token = requireToken()
        return api.wgServers(token)
    }

    suspend fun wgConfig(serverId: Int): String {
        val token = requireToken()
        val rawConfig = api.wgConfig(token, serverId)
        val finalConfig = applySplitTunneling(rawConfig)

        Log.d("VpnRepository", "FINAL WG CONFIG serverId=$serverId\n$finalConfig")

        return finalConfig
    }

    private suspend fun applySplitTunneling(configText: String): String {
        val mode = settingsStore.splitTunnelModeFlow.first()
        val excluded = settingsStore.excludedAppsFlow.first()
        val included = settingsStore.includedAppsFlow.first()

        if (mode == SettingsStore.SplitTunnelMode.OFF) {
            return configText
        }

        val lines = configText.lines().toMutableList()

        lines.removeAll { line ->
            val trimmed = line.trim()
            trimmed.startsWith("ExcludedApplications =", ignoreCase = true) ||
                    trimmed.startsWith("IncludedApplications =", ignoreCase = true)
        }

        val peerIndex = lines.indexOfFirst {
            it.trim().equals("[Peer]", ignoreCase = true)
        }

        val insertIndex = if (peerIndex == -1) lines.size else peerIndex

        when (mode) {
            SettingsStore.SplitTunnelMode.EXCLUDE_APPS -> {
                if (excluded.isNotEmpty()) {
                    lines.add(
                        insertIndex,
                        "ExcludedApplications = ${excluded.sorted().joinToString(",")}"
                    )
                }
            }

            SettingsStore.SplitTunnelMode.INCLUDE_ONLY_APPS -> {
                if (included.isNotEmpty()) {
                    lines.add(
                        insertIndex,
                        "IncludedApplications = ${included.sorted().joinToString(",")}"
                    )
                }
            }

            SettingsStore.SplitTunnelMode.OFF -> Unit
        }

        return lines.joinToString("\n")
    }

    suspend fun logout() {
        val token = getToken()
        if (!token.isNullOrBlank()) {
            try {
                api.logout(token)
            } catch (_: Exception) {
            }
        }
        tokenStore.clear()
    }
}