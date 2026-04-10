package uk.co.aiovpn.android.vpn.backend

import android.util.Log
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

object WgConfigParser {
    private const val TAG = "WgConfigParser"

    @Throws(Exception::class)
    fun parse(configText: String): Config {
        Log.d(TAG, "Attempting to parse WireGuard config")

        return try {
            val method = Config::class.java.getMethod(
                "fromWgQuickString",
                String::class.java
            )
            method.invoke(null, configText) as Config
        } catch (_: NoSuchMethodException) {
            val method = Config::class.java.getMethod(
                "parse",
                BufferedReader::class.java
            )
            BufferedReader(StringReader(configText)).use { reader ->
                method.invoke(null, reader) as Config
            }
        }
    }
}