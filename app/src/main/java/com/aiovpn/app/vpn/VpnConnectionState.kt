package com.aiovpn.app.vpn

sealed class VpnConnectionState {
    data object Disconnected : VpnConnectionState()
    data object Connecting : VpnConnectionState()
    data object Connected : VpnConnectionState()
    data object Switching : VpnConnectionState()
    data class Error(val message: String? = null) : VpnConnectionState()
}
