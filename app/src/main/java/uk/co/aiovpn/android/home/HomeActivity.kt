package uk.co.aiovpn.android.home

import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import uk.co.aiovpn.android.R
import uk.co.aiovpn.android.api.WgServerDto
import uk.co.aiovpn.android.login.ServerListActivity
import uk.co.aiovpn.android.repo.DeviceRepository
import uk.co.aiovpn.android.repo.VpnRepository
import uk.co.aiovpn.android.routing.LastGoodServerStore
import uk.co.aiovpn.android.routing.ServerRankingCache
import uk.co.aiovpn.android.routing.ServerScore
import uk.co.aiovpn.android.routing.SmartRoutingService
import uk.co.aiovpn.android.routing.WgTunnelValidator
import uk.co.aiovpn.android.updater.UpdateState
import uk.co.aiovpn.android.updater.Updater
import uk.co.aiovpn.android.util.ConnectButtonAnimator
import uk.co.aiovpn.android.util.SettingsStore
import uk.co.aiovpn.android.vpn.backend.WireGuardBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var lastGoodServerStore: LastGoodServerStore
    private lateinit var wgBackend: WireGuardBackend

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var glowView: ImageView
    private lateinit var connectButton: ImageView
    private lateinit var powerIcon: ImageView
    private lateinit var connectFocusRing: View
    private lateinit var buttonGlowView: ImageView
    private lateinit var statusPill: TextView
    private lateinit var selectedServerText: TextView
    private lateinit var fastestServersRecycler: RecyclerView

    private lateinit var sideNav: View
    private lateinit var navLogo: ImageView
    private lateinit var navLogoBanner: ImageView
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View
    private lateinit var navTexts: List<View>

    private lateinit var connectButtonAnimator: ConnectButtonAnimator
    private lateinit var fastServerAdapter: FastServerAdapter

    private var isExpanded = false
    private var currentVpnState: VpnUiState = VpnUiState.DISCONNECTED
    private var selectedServerId: Int? = null
    private var selectedServerLabel: String? = null
    private var connectButtonStroke: GradientDrawable? = null
    private var fastServerLookup: Map<Int, String> = emptyMap()
    private var smartAutoSelected: Boolean = false
    private var currentConnectedServerLabel: String? = null
    private var updateDialogShowing = false
    private val smartRoutingService = SmartRoutingService()

    enum class VpnUiState {
        DISCONNECTED,
        CONNECTING,
        VERIFYING,
        SWITCHING,
        CONNECTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        repo = VpnRepository(this)
        settingsStore = SettingsStore(this)
        lastGoodServerStore = LastGoodServerStore(this)
        wgBackend = WireGuardBackend.get(this)

        wgBackend.setPermissionCallback(object : WireGuardBackend.PermissionCallback {
            override fun onVpnPermissionRequired(intent: Intent) {
                startActivityForResult(intent, REQUEST_VPN_PERMISSION)
            }
        })

        drawerLayout = findViewById(R.id.homeDrawer)
        glowView = findViewById(R.id.glowView)
        buttonGlowView = findViewById(R.id.buttonGlowView)
        connectButton = findViewById(R.id.connectButton)
        powerIcon = findViewById(R.id.powerIcon)
        connectFocusRing = findViewById(R.id.connectFocusRing)
        statusPill = findViewById(R.id.statusPill)
        selectedServerText = findViewById(R.id.selectedServerText)
        fastestServersRecycler = findViewById(R.id.fastestServersRecycler)

        sideNav = findViewById(R.id.sideNav)
        navLogo = findViewById(R.id.navLogo)
        navLogoBanner = findViewById(R.id.navLogoBanner)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        connectButtonStroke = connectButton.background?.mutate() as? GradientDrawable
        connectButtonAnimator = ConnectButtonAnimator(buttonGlowView)

        setupRecycler()
        bindNavigation()
        bindConnectButton()
        observeUpdaterState()

        setVpnState(VpnUiState.DISCONNECTED)

        lifecycleScope.launch {
            selectedServerId = settingsStore.lastServerIdFlow.first()
            selectedServerLabel = settingsStore.lastServerLabelFlow.first()

            smartAutoSelected = selectedServerId == null || selectedServerLabel.isNullOrBlank()

            selectedServerText.text = if (smartAutoSelected) {
                "Smart Auto"
            } else {
                selectedServerLabel ?: "Smart Auto"
            }

            if (settingsStore.autoConnectFlow.first()) {
                handleConnectButtonClick()
            }
        }

        handleIntent(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DeviceRepository(this@HomeActivity).getOrRegisterDeviceToken()
            } catch (e: Exception) {
                Log.e(TAG, "Device registration failed", e)
            }
        }

        connectButton.post {
            connectButton.requestFocus()
        }

        lifecycleScope.launch {
            delay(1500)
            loadFastestServers()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        updateUiFromBackendState()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectButtonAnimator.stop()
    }

    private fun isBusy(): Boolean {
        return currentVpnState == VpnUiState.CONNECTING ||
                currentVpnState == VpnUiState.VERIFYING ||
                currentVpnState == VpnUiState.SWITCHING
    }

    private fun handleIntent(intent: Intent?) {
        val serverId = intent?.getIntExtra("server_id", -1)?.takeIf { it != -1 }
        val serverLabel = intent?.getStringExtra("server_label")

        if (serverId != null && !serverLabel.isNullOrBlank()) {
            smartAutoSelected = false
            selectedServerId = serverId
            selectedServerLabel = serverLabel
            selectedServerText.text = serverLabel

            lifecycleScope.launch {
                settingsStore.saveLastServer(serverId, serverLabel)
            }
        }
    }

    private fun observeUpdaterState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Updater.state.collect { state ->
                    if (state is UpdateState.Available && !updateDialogShowing) {
                        showUpdateDialog(state)
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(state: UpdateState.Available) {
        updateDialogShowing = true

        val message = buildString {
            append("Version ${state.versionName} is available.")
            state.releaseNotes?.takeIf { it.isNotBlank() }?.let {
                append("\n\n")
                append(it)
            }
        }

        AlertDialog.Builder(this, R.style.Aio_Dialog)
            .setTitle("Update available")
            .setMessage(message)
            .setCancelable(!state.mandatory)
            .setPositiveButton("Open Settings") { _, _ ->
                updateDialogShowing = false
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .apply {
                if (state.mandatory) {
                    setNegativeButton("Exit") { _, _ ->
                        finishAffinity()
                    }
                } else {
                    setNegativeButton("Later") { _, _ ->
                        // no-op
                    }
                }
            }
            .setOnDismissListener {
                updateDialogShowing = false
            }
            .show()
    }

    private fun updateUiFromBackendState() {
        lifecycleScope.launch {
            val connected = withContext(Dispatchers.IO) {
                wgBackend.isConnected()
            }

            if (connected) {
                setVpnState(VpnUiState.CONNECTED)
                selectedServerText.text = if (smartAutoSelected) {
                    currentConnectedServerLabel?.let { "Smart Auto • $it" } ?: "Smart Auto"
                } else {
                    selectedServerLabel ?: currentConnectedServerLabel ?: "Connected"
                }
            } else {
                setVpnState(VpnUiState.DISCONNECTED)
                currentConnectedServerLabel = null
                selectedServerText.text = if (smartAutoSelected) {
                    "Smart Auto"
                } else {
                    selectedServerLabel ?: "Smart Auto"
                }
            }
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(
            navHomeContainer,
            navServersContainer,
            navAccountContainer,
            navSettingsContainer
        )

        navHomeContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    v.isSelected = true
                    toggleSidebar(true)
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    v.postDelayed({
                        if (navContainers.none { it.hasFocus() }) {
                            navHomeContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    connectButton.requestFocus()
                    true
                } else {
                    false
                }
            }

            container.setOnClickListener {
                when (container.id) {
                    R.id.navHomeContainer -> Unit
                    R.id.navServersContainer -> startActivity(Intent(this, ServerListActivity::class.java))
                    R.id.navAccountContainer -> startActivity(Intent(this, AccountActivity::class.java))
                    R.id.navSettingsContainer -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
        }
    }

    private fun toggleSidebar(expand: Boolean) {
        if (isExpanded == expand) return
        isExpanded = expand

        animateLogoSwap(expand)

        val targetWidth = if (expand) 240.toPx() else 84.toPx()
        val targetAlpha = if (expand) 1f else 0f

        val widthAnimator = ValueAnimator.ofInt(sideNav.width, targetWidth)
        widthAnimator.addUpdateListener { animator ->
            val params = sideNav.layoutParams
            params.width = animator.animatedValue as Int
            sideNav.layoutParams = params
        }
        widthAnimator.duration = 250
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.start()

        navTexts.forEach { text ->
            text.animate().alpha(targetAlpha).setDuration(200).start()
        }
    }

    private fun animateLogoSwap(expand: Boolean) {
        if (expand) {
            navLogoBanner.visibility = View.VISIBLE
            navLogoBanner.alpha = 0f
            navLogoBanner.animate().alpha(1f).setDuration(180).start()
            navLogo.animate().alpha(0f).setDuration(140).withEndAction {
                navLogo.visibility = View.GONE
                navLogo.alpha = 1f
            }.start()
        } else {
            navLogo.visibility = View.VISIBLE
            navLogo.alpha = 0f
            navLogo.animate().alpha(1f).setDuration(180).start()
            navLogoBanner.animate().alpha(0f).setDuration(140).withEndAction {
                navLogoBanner.visibility = View.GONE
                navLogoBanner.alpha = 1f
            }.start()
        }
    }

    private fun bindConnectButton() {
        connectButton.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                connectFocusRing.animate().alpha(0.85f).setDuration(120).start()
                v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(120).start()
                powerIcon.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
            } else {
                connectFocusRing.animate().alpha(0f).setDuration(120).start()
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                powerIcon.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
        }

        connectButton.setOnClickListener {
            handleConnectButtonClick()
        }

        connectButton.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    navHomeContainer.requestFocus()
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    fastestServersRecycler.post {
                        val firstCard =
                            fastestServersRecycler.layoutManager?.findViewByPosition(0)
                                ?: fastestServersRecycler.getChildAt(0)
                        firstCard?.requestFocus()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun handleConnectButtonClick() {
        if (isBusy()) return

        if (currentVpnState == VpnUiState.CONNECTED) {
            disconnectCurrentTunnel()
            return
        }

        val serverId = selectedServerId
        val label = selectedServerLabel

        if (serverId != null && !label.isNullOrBlank()) {
            connectToSpecificServer(serverId, label)
        } else {
            connectSmartAuto()
        }
    }

    private fun connectSmartAuto(forceFresh: Boolean = false) {
        if (isBusy()) return

        setVpnState(VpnUiState.CONNECTING)
        selectedServerText.text = "Finding best server..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val servers = repo.servers()
                if (servers.isEmpty()) {
                    throw IllegalStateException("No WireGuard servers available")
                }

                val ranked = getOrBuildRankings(servers)
                if (ranked.isEmpty()) {
                    throw IllegalStateException("No reachable servers found")
                }

                val orderedCandidates = if (forceFresh) {
                    ranked.map { it.server }.distinctBy { it.id }.take(4)
                } else {
                    buildCandidateOrder(servers, ranked).take(4)
                }
                var lastError: Exception? = null

                for (candidate in orderedCandidates) {
                    try {
                        withContext(Dispatchers.Main) {
                            selectedServerText.text = "Connecting to ${candidate.displayName}..."
                        }

                        Log.d(TAG, "Smart Auto trying ${candidate.name} (${candidate.ip})")

                        val config = repo.wgConfig(candidate.id)
                        wgBackend.connect(config)

                        withContext(Dispatchers.Main) {
                            setVpnState(VpnUiState.VERIFYING)
                            selectedServerText.text = "Verifying ${candidate.displayName}..."
                        }

                        delay(1200)
                        validateTunnelOrThrow(candidate.displayName)

                        val winnerLabel = candidate.displayName
                        currentConnectedServerLabel = winnerLabel

                        lastGoodServerStore.save(candidate.id, winnerLabel)

                        withContext(Dispatchers.Main) {
                            selectedServerText.text = "Smart Auto • $winnerLabel"
                            setVpnState(VpnUiState.CONNECTED)
                            Toast.makeText(
                                this@HomeActivity,
                                "Connected to $winnerLabel",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        return@launch
                    } catch (e: Exception) {
                        lastError = e
                        Log.e(TAG, "Smart Auto failed on server id=${candidate.id}", e)

                        try {
                            wgBackend.disconnect()
                        } catch (_: Exception) {
                        }
                    }
                }

                throw lastError ?: IllegalStateException("No candidate server could connect")
            } catch (e: Exception) {
                Log.e(TAG, "Smart Auto connection failed", e)
                withContext(Dispatchers.Main) {
                    setVpnState(VpnUiState.DISCONNECTED)
                    selectedServerText.text = "Smart Auto"
                    Toast.makeText(
                        this@HomeActivity,
                        "Smart connect failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun selectSmartAutoAndConnect() {
        if (isBusy()) return

        smartAutoSelected = true
        selectedServerId = null
        selectedServerLabel = null
        currentConnectedServerLabel = null
        selectedServerText.text = "Smart Auto"

        lifecycleScope.launch {
            settingsStore.clearLastServer()
            connectSmartAuto(forceFresh = true)
        }
    }

    private fun connectToSpecificServer(serverId: Int, label: String) {
        if (isBusy()) return

        val previousServerId = selectedServerId
        val previousServerLabel = selectedServerLabel
        val switching = currentVpnState == VpnUiState.CONNECTED

        smartAutoSelected = false
        selectedServerId = serverId
        selectedServerLabel = label

        selectedServerText.text =
            if (switching) "Switching to $label..." else "Connecting to $label..."

        setVpnState(if (switching) VpnUiState.SWITCHING else VpnUiState.CONNECTING)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "CONNECT_START serverId=$serverId label=$label")

                val config = repo.wgConfig(serverId)
                wgBackend.connect(config)

                withContext(Dispatchers.Main) {
                    setVpnState(VpnUiState.VERIFYING)
                    selectedServerText.text = "Verifying $label..."
                }

                delay(1200)

                validateTunnelOrThrow(label)

                Log.d(TAG, "CONNECT_SUCCESS serverId=$serverId label=$label")

                currentConnectedServerLabel = label
                settingsStore.saveLastServer(serverId, label)
                lastGoodServerStore.save(serverId, label)

                withContext(Dispatchers.Main) {
                    selectedServerText.text = label
                    setVpnState(VpnUiState.CONNECTED)
                }

            } catch (e: Exception) {
                Log.e(TAG, "CONNECT_FAIL serverId=$serverId label=$label", e)

                try {
                    wgBackend.disconnect()
                } catch (_: Exception) {}

                selectedServerId = previousServerId
                selectedServerLabel = previousServerLabel

                withContext(Dispatchers.Main) {
                    setVpnState(VpnUiState.DISCONNECTED)
                    selectedServerText.text = previousServerLabel ?: "Smart Auto"

                    Toast.makeText(
                        this@HomeActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun disconnectCurrentTunnel() {
        if (isBusy()) return

        setVpnState(VpnUiState.SWITCHING)
        selectedServerText.text = "Disconnecting..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                wgBackend.disconnect()

                withContext(Dispatchers.Main) {
                    setVpnState(VpnUiState.DISCONNECTED)
                    currentConnectedServerLabel = null
                    selectedServerText.text = if (smartAutoSelected) {
                        "Smart Auto"
                    } else {
                        selectedServerLabel ?: "Smart Auto"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect failed", e)
                withContext(Dispatchers.Main) {
                    updateUiFromBackendState()
                    Toast.makeText(
                        this@HomeActivity,
                        "Disconnect failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun buildCandidateOrder(
        servers: List<WgServerDto>,
        ranked: List<ServerScore>
    ): List<WgServerDto> {
        val rankedServers = ranked.map { it.server }.toMutableList()
        val lastGood = lastGoodServerStore.get() ?: return rankedServers

        val preferred = servers.firstOrNull { it.id == lastGood.serverId } ?: return rankedServers

        val reordered = mutableListOf<WgServerDto>()
        reordered.add(preferred)
        reordered.addAll(rankedServers.filter { it.id != preferred.id })

        return reordered.distinctBy { it.id }
    }

    private suspend fun getOrBuildRankings(servers: List<WgServerDto>): List<ServerScore> {
        return if (ServerRankingCache.rankedServers != null && ServerRankingCache.isValid()) {
            ServerRankingCache.rankedServers!!
        } else {
            smartRoutingService.rankServers(servers).also {
                ServerRankingCache.set(it)
            }
        }
    }

    private suspend fun validateTunnelOrThrow(serverName: String?) {
        Log.d(TAG, "VALIDATOR_START server=${serverName ?: "unknown"}")

        val ok = WgTunnelValidator.waitForWorkingTunnel(
            timeoutMs = 10000,
            intervalMs = 1000
        )

        Log.d(TAG, "VALIDATOR_RESULT server=${serverName ?: "unknown"} ok=$ok")

        if (!ok) {
            throw IllegalStateException(
                if (!serverName.isNullOrBlank()) {
                    "Tunnel validation failed for $serverName"
                } else {
                    "Tunnel validation failed"
                }
            )
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_VPN_PERMISSION) return

        if (resultCode == RESULT_OK) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    wgBackend.retryConnectionAfterPermission()
                    validateTunnelOrThrow(null)

                    withContext(Dispatchers.Main) {
                        updateUiFromBackendState()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Permission retry failed", e)

                    try {
                        wgBackend.disconnect()
                    } catch (_: Exception) {
                    }

                    withContext(Dispatchers.Main) {
                        setVpnState(VpnUiState.DISCONNECTED)
                        selectedServerText.text = selectedServerLabel ?: "Smart Auto"
                        Toast.makeText(
                            this@HomeActivity,
                            "Connection failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } else {
            setVpnState(VpnUiState.DISCONNECTED)
            selectedServerText.text = selectedServerLabel ?: "Smart Auto"
            Toast.makeText(this, "VPN permission is required to connect", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecycler() {
        fastServerAdapter = FastServerAdapter(
            items = listOf(FastServerItem(-1, "All Servers", null, "See more", null, true)),
            onMoveToSidebar = {
                navHomeContainer.requestFocus()
            },
            onServerClick = { item ->
                when {
                    item.id == -2 -> {
                        selectSmartAutoAndConnect()
                    }

                    item.isAllServers -> {
                        startActivity(Intent(this@HomeActivity, ServerListActivity::class.java))
                    }

                    else -> {
                        val fullLabel = fastServerLookup[item.id] ?: item.label
                        connectToSpecificServer(item.id, fullLabel)
                    }
                }
            }
        )

        fastestServersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fastestServersRecycler.adapter = fastServerAdapter
        fastestServersRecycler.clipToPadding = false
        fastestServersRecycler.clipChildren = false
        fastestServersRecycler.setHasFixedSize(true)
        fastestServersRecycler.isFocusable = true
        fastestServersRecycler.isFocusableInTouchMode = true
        fastestServersRecycler.descendantFocusability = RecyclerView.FOCUS_AFTER_DESCENDANTS
    }

    private fun loadFastestServers() {
        lifecycleScope.launch {
            try {
                val servers = withContext(Dispatchers.IO) { repo.servers() }

                fastServerLookup = servers.associate { server ->
                    server.id to server.displayName
                }

                val initialItems = mutableListOf(
                    FastServerItem(
                        id = -2,
                        label = "Smart Auto",
                        cityName = "Best available route",
                        pingText = "Auto",
                        countryCode = null,
                        isAllServers = false
                    )
                ).apply {
                    addAll(
                        servers.take(3).map { server ->
                            FastServerItem(
                                id = server.id,
                                label = server.displayName,
                                cityName = server.city,
                                pingText = "-- ms",
                                countryCode = server.country_code,
                                isAllServers = false
                            )
                        }
                    )
                    add(FastServerItem(-1, "All Servers", null, "See more", null, true))
                }

                fastServerAdapter.updateItems(initialItems)

                val ranked = withContext(Dispatchers.IO) { getOrBuildRankings(servers) }

                if (ranked.isNotEmpty()) {
                    val sortedItems = mutableListOf(
                        FastServerItem(
                            id = -2,
                            label = "Smart Auto",
                            cityName = "Best available route",
                            pingText = "Auto",
                            countryCode = null,
                            isAllServers = false
                        )
                    ).apply {
                        addAll(
                            ranked.take(3).map { score ->
                                FastServerItem(
                                    id = score.server.id,
                                    label = score.server.displayName,
                                    cityName = score.server.city,
                                    pingText = "${score.latencyMs} ms",
                                    countryCode = score.server.country_code,
                                    isAllServers = false
                                )
                            }
                        )
                        add(FastServerItem(-1, "All Servers", null, "See more", null, true))
                    }

                    fastServerAdapter.updateItems(sortedItems)
                } else {
                    Log.w(TAG, "No ranked servers available, keeping initial items")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fastest servers", e)
            }
        }
    }

    private fun setVpnState(state: VpnUiState) {
        currentVpnState = state

        when (state) {
            VpnUiState.DISCONNECTED -> {
                connectButtonAnimator.stop()
                glowView.animate()
                    .alpha(0f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(180)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFFD1D5DB.toInt())
                statusPill.text = "Disconnected"
                statusPill.setBackgroundResource(R.drawable.pill_disconnected)
            }

            VpnUiState.CONNECTING -> {
                buttonGlowView.setImageResource(R.drawable.button_glow_purple)
                connectButtonAnimator.startConnectingAnimation()

                glowView.setImageResource(R.drawable.glow_purple)
                glowView.scaleX = 0.92f
                glowView.scaleY = 0.92f
                glowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFFB44CFF.toInt())
                statusPill.text = "Connecting"
                statusPill.setBackgroundResource(R.drawable.pill_connecting)
            }

            VpnUiState.VERIFYING -> {
                // Keep visuals same as connecting (don’t overdesign yet)
                buttonGlowView.setImageResource(R.drawable.button_glow_purple)
                connectButtonAnimator.startConnectingAnimation()

                glowView.setImageResource(R.drawable.glow_purple)
                glowView.scaleX = 0.92f
                glowView.scaleY = 0.92f
                glowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFFB44CFF.toInt())
                statusPill.text = "Verifying"
                statusPill.setBackgroundResource(R.drawable.pill_connecting)
            }

            VpnUiState.SWITCHING -> {
                buttonGlowView.setImageResource(R.drawable.button_glow_yellow)
                connectButtonAnimator.startConnectingAnimation()

                glowView.setImageResource(R.drawable.glow_yellow)
                glowView.scaleX = 0.92f
                glowView.scaleY = 0.92f
                glowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFFDAA520.toInt())
                statusPill.text = "Switching"
                statusPill.setBackgroundResource(R.drawable.pill_switching)
            }

            VpnUiState.CONNECTED -> {
                buttonGlowView.setImageResource(R.drawable.button_glow_blue)
                connectButtonAnimator.setStable(1.0f)

                glowView.setImageResource(R.drawable.glow_blue)
                glowView.scaleX = 0.92f
                glowView.scaleY = 0.92f
                glowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .start()

                powerIcon.imageTintList = ColorStateList.valueOf(0xFF4F7BFF.toInt())
                statusPill.text = "Connected"
                statusPill.setBackgroundResource(R.drawable.pill_connected)
            }
        }
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_VPN_PERMISSION = 1001
        private const val TAG = "HomeActivity"
    }
}
