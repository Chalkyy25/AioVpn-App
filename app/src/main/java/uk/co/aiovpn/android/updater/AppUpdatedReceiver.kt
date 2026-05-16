package uk.co.aiovpn.android.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("AIOVPN/AppUpdated", "App updated, cleaning up old update files")
            try {
                val updatesDir = File(context.cacheDir, "updates")
                if (updatesDir.exists()) {
                    updatesDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AIOVPN/AppUpdated", "Failed to clean up updates directory", e)
            }
        }
    }
}
