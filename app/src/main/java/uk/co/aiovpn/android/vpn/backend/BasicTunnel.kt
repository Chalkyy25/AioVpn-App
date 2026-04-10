package uk.co.aiovpn.android.vpn.backend

import com.wireguard.android.backend.Tunnel

class BasicTunnel(
    private val name: String,
    private val listener: Listener? = null
) : Tunnel {

    @Volatile
    private var state: Tunnel.State = Tunnel.State.DOWN

    interface Listener {
        fun onStateChanged(state: Tunnel.State)
    }

    override fun getName(): String = name

    override fun onStateChange(state: Tunnel.State) {
        this.state = state
        listener?.onStateChanged(state)
    }

    fun peekState(): Tunnel.State = state
}