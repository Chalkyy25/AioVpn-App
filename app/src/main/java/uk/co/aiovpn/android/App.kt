package uk.co.aiovpn.android

import android.app.Application
import android.util.Log
import uk.co.aiovpn.android.repo.DeviceRepository
import uk.co.aiovpn.android.updater.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var deviceRepository: DeviceRepository
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        instance = this
        deviceRepository = DeviceRepository(applicationContext)

        Log.d(TAG, "AIO VPN App started")

        Updater.init(applicationContext)

        appScope.launch {
            try {
                deviceRepository.getOrRegisterDeviceToken()
                Log.d(TAG, "Device token ready, starting updater monitor")
                Updater.monitorForUpdates()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to prepare updater", e)
            }
        }
    }

    companion object {
        private const val TAG = "AIOVPN/App"

        @Volatile
        private lateinit var instance: App

        fun get(): App = instance
    }
}