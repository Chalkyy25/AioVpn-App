package uk.co.aiovpn.android.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object PingUtil {

    suspend fun measurePing(host: String?): Int = withContext(Dispatchers.IO) {
        if (host.isNullOrBlank()) return@withContext -1

        val cleanHost = host
            .replace("https://", "")
            .replace("http://", "")
            .substringBefore("/") // Remove path if any
            .substringBefore(":") // Remove port
            .trim()

        var process: Process? = null
        try {
            // Use -c 1 for faster results on TV. 2 packets is often redundant for a quick UI sort.
            val pb = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "1", cleanHost)
                .redirectErrorStream(true) // Combine streams to reduce thread/pipe overhead
            
            process = pb.start()
            
            val result = process.inputStream.bufferedReader().use { reader ->
                var pingAvg = -1
                // We only care about the RTT line. ReadLine is blocking but fine on IO dispatcher.
                reader.forEachLine { line ->
                    if (line.contains("rtt") || line.contains("round-trip")) {
                        val stats = line.substringAfter("= ").substringBefore(" ms").split("/")
                        if (stats.size >= 2) {
                            pingAvg = stats[1].toDoubleOrNull()?.toInt() ?: -1
                        }
                    }
                }
                pingAvg
            }

            if (result != -1) return@withContext result

            // Lightweight fallback
            val start = System.currentTimeMillis()
            if (InetAddress.getByName(cleanHost).isReachable(800)) {
                return@withContext (System.currentTimeMillis() - start).toInt()
            }
        } catch (e: Exception) {
            // Log.e("PingUtil", "Failed to ping $cleanHost", e)
        } finally {
            process?.destroy()
        }

        -1
    }
}
