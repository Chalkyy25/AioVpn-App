package com.aiovpn.app.routing

import android.content.Context
import android.content.SharedPreferences

class LastGoodServerStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("last_good_server", Context.MODE_PRIVATE)

    fun save(serverId: Int, serverLabel: String) {
        prefs.edit()
            .putInt(KEY_SERVER_ID, serverId)
            .putString(KEY_SERVER_LABEL, serverLabel)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun get(): LastGoodServer? {
        val id = prefs.getInt(KEY_SERVER_ID, -1)
        if (id == -1) return null

        val label = prefs.getString(KEY_SERVER_LABEL, null) ?: return null
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)

        return LastGoodServer(
            serverId = id,
            serverLabel = label,
            savedAt = savedAt
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_SERVER_LABEL = "server_label"
        private const val KEY_SAVED_AT = "saved_at"
    }
}

data class LastGoodServer(
    val serverId: Int,
    val serverLabel: String,
    val savedAt: Long
)