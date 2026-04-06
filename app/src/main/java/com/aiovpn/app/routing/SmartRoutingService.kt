package com.aiovpn.app.routing

import com.aiovpn.app.api.WgServerDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress

data class ServerScore(
    val server: WgServerDto,
    val latencyMs: Long,
    val success: Boolean
)

class SmartRoutingService {

    suspend fun rankServers(
        servers: List<WgServerDto>,
        timeoutMs: Int = 1200
    ): List<ServerScore> = coroutineScope {
        servers.map { server ->
            async {
                val latency = measureLatency(server.ip, timeoutMs)
                ServerScore(
                    server = server,
                    latencyMs = latency,
                    success = latency < Long.MAX_VALUE
                )
            }
        }.awaitAll()
            .filter { it.success }
            .sortedBy { it.latencyMs }
    }

    private suspend fun measureLatency(
        host: String,
        timeoutMs: Int
    ): Long = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            val reachable = InetAddress.getByName(host).isReachable(timeoutMs)
            if (reachable) {
                (System.nanoTime() - start) / 1_000_000
            } else {
                Long.MAX_VALUE
            }
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}