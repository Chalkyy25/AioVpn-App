package uk.co.aiovpn.android.repo

import android.content.Context
import android.os.Build
import android.util.Log
import uk.co.aiovpn.android.api.AioApi
import uk.co.aiovpn.android.auth.DeviceTokenStore
import uk.co.aiovpn.android.BuildConfig

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