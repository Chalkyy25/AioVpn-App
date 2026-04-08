package com.aiovpn.app.vpn.backend

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class WireGuardBackend private constructor(
    context: Context
) : VpnBackend {

    private val appContext = context.applicationContext
    private val backend = GoBackend(appContext)
    private val connected = AtomicBoolean(false)

    @Volatile
    private var pendingConfig: String? = null

    @Volatile
    private var tunnel: BasicTunnel? = null

    @Volatile
    private var permissionCallback: PermissionCallback? = null

    data class AlwaysOnStatus(
        val isAlwaysOn: Boolean,
        val isLockdownEnabled: Boolean
    )

    interface PermissionCallback {
        fun onVpnPermissionRequired(intent: Intent)
    }

    companion object {
        private const val TAG = "WireGuardBackend"
        private const val TUNNEL_NAME = "AIO-WG"

        @Volatile
        private var instance: WireGuardBackend? = null

        fun get(context: Context): WireGuardBackend {
            return instance ?: synchronized(this) {
                instance ?: WireGuardBackend(context.applicationContext).also { instance = it }
            }
        }

        fun maybe(): WireGuardBackend? = instance
    }

    fun setPermissionCallback(callback: PermissionCallback?) {
        permissionCallback = callback
    }

    override fun getBackendName(): String = "WireGuard"

    override fun isConnected(): Boolean = connected.get()

    suspend fun getAlwaysOnStatus(): AlwaysOnStatus? = withContext(Dispatchers.IO) {
        try {
            AlwaysOnStatus(
                isAlwaysOn = backend.isAlwaysOn(),
                isLockdownEnabled = backend.isLockdownEnabled()
            )
        } catch (e: TimeoutException) {
            Log.d(TAG, "Always-on status unavailable (VPN service not started)")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read Always-on VPN status", e)
            null
        }
    }

    suspend fun retryConnectionAfterPermission() = withContext(Dispatchers.IO) {
        val config = pendingConfig ?: return@withContext
        pendingConfig = null
        connect(config)
    }

    override suspend fun connect(configText: String) = withContext(Dispatchers.IO) {
        val permissionIntent = VpnService.prepare(appContext)
        if (permissionIntent != null) {
            pendingConfig = configText
            permissionCallback?.onVpnPermissionRequired(permissionIntent)
                ?: throw IllegalStateException("VPN permission required but no callback registered")
            return@withContext
        }

        val config = WgConfigParser.parse(configText)

        val activeTunnel = tunnel ?: BasicTunnel(
            TUNNEL_NAME,
            object : BasicTunnel.Listener {
                override fun onStateChanged(state: Tunnel.State) {
                    connected.set(state == Tunnel.State.UP)
                }
            }
        ).also { tunnel = it }

        try {
            backend.setState(activeTunnel, Tunnel.State.UP, config)
            connected.set(true)
            pendingConfig = null
        } catch (e: Exception) {
            connected.set(false)
            Log.e(TAG, "Connect failed", e)
            throw e
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val activeTunnel = tunnel ?: run {
            connected.set(false)
            return@withContext
        }

        try {
            backend.setState(activeTunnel, Tunnel.State.DOWN, null)
        } finally {
            connected.set(false)
            tunnel = null
        }
    }
}
