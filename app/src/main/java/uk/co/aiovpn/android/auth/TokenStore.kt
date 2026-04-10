package uk.co.aiovpn.android.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("aio_vpn_auth", Context.MODE_PRIVATE)

    suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_TOKEN, null)
    }

    suspend fun getUsername(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_USERNAME, null)
    }

    suspend fun getExpiry(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_EXPIRY, null)
    }

    suspend fun getDevicesAllowed(): Int? = withContext(Dispatchers.IO) {
        if (!prefs.contains(KEY_DEVICES_ALLOWED)) return@withContext null
        prefs.getInt(KEY_DEVICES_ALLOWED, 0)
    }

    suspend fun saveAuth(
        token: String,
        username: String,
        expiry: String?,
        devicesAllowed: Int?
    ) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            putString(KEY_EXPIRY, expiry)

            if (devicesAllowed != null) {
                putInt(KEY_DEVICES_ALLOWED, devicesAllowed)
            } else {
                remove(KEY_DEVICES_ALLOWED)
            }
        }.apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    fun getTokenSync(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    private companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USERNAME = "username"
        private const val KEY_EXPIRY = "expiry"
        private const val KEY_DEVICES_ALLOWED = "devices_allowed"
    }
}