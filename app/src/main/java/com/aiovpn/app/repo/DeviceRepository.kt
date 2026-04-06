package com.aiovpn.app.repo

import android.content.Context
import android.os.Build
import android.util.Log
import com.aiovpn.app.api.AioApi
import com.aiovpn.app.auth.DeviceTokenStore
import com.aiovpn.app.BuildConfig

class DeviceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val api = AioApi()
    private val store = DeviceTokenStore(appContext)

    suspend fun getOrRegisterDeviceToken(): String {
        val existing = store.getDeviceToken()
        if (!existing.isNullOrBlank()) {
            Log.d("AIOVPN", "Using existing device token")
            return existing
        }

        val deviceUuid = store.getOrCreateDeviceUuid()
        Log.d("AIOVPN", "Registering device token for UUID=$deviceUuid")

        val token = api.registerDeviceToken(
            deviceUuid = deviceUuid,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            appVersionCode = BuildConfig.VERSION_CODE
        )

        Log.d("AIOVPN", "Registered device token successfully")
        store.saveDeviceToken(token)
        return token
    }

    suspend fun getDeviceToken(): String? {
        return store.getDeviceToken()
    }
}