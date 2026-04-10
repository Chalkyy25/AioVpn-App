package uk.co.aiovpn.android.routing

import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL

object WgTunnelValidator {

    suspend fun waitForWorkingTunnel(
        timeoutMs: Long = 10000,
        intervalMs: Long = 1000
    ): Boolean {
        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (verifyTunnel()) return true
            delay(intervalMs)
        }

        return false
    }

    private fun verifyTunnel(): Boolean {
        return try {
            val conn = (URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = false
                useCaches = false
            }

            conn.connect()
            val code = conn.responseCode
            conn.disconnect()

            code == 204 || code in 200..399
        } catch (_: Exception) {
            false
        }
    }
}