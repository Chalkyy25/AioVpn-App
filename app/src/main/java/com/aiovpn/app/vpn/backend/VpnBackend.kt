package com.aiovpn.app.vpn.backend

interface VpnBackend {
    fun getBackendName(): String
    fun isConnected(): Boolean
    suspend fun connect(configText: String)
    suspend fun disconnect()
}