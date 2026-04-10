package uk.co.aiovpn.android.vpn.backend

interface VpnBackend {
    fun getBackendName(): String
    fun isConnected(): Boolean
    suspend fun connect(configText: String)
    suspend fun disconnect()
}