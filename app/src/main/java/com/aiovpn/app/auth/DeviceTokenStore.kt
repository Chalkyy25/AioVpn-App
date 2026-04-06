package com.aiovpn.app.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DeviceTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences("aio_vpn_device", Context.MODE_PRIVATE)

    suspend fun getOrCreateDeviceUuid(): String = withContext(Dispatchers.IO) {
        val existing = prefs.getString(KEY_DEVICE_UUID, null)
        if (!existing.isNullOrBlank()) return@withContext existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_UUID, created).apply()
        created
    }

    suspend fun getDeviceToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_DEVICE_TOKEN, null)
    }

    suspend fun saveDeviceToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    private companion object {
        private const val KEY_DEVICE_UUID = "device_uuid"
        private const val KEY_DEVICE_TOKEN = "device_token"
    }
}