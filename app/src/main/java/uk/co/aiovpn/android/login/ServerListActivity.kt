package uk.co.aiovpn.android.login

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import uk.co.aiovpn.android.R
import uk.co.aiovpn.android.api.WgServerDto
import uk.co.aiovpn.android.home.AccountActivity
import uk.co.aiovpn.android.home.HomeActivity
import uk.co.aiovpn.android.home.SettingsActivity
import uk.co.aiovpn.android.repo.VpnRepository
import uk.co.aiovpn.android.routing.ServerRankingCache
import uk.co.aiovpn.android.routing.SmartRoutingService
import uk.co.aiovpn.android.vpn.backend.WireGuardBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ServerFilter {
    ALL,
    FASTEST,
    RECOMMENDED,
    FAVORITES
}

class ServerListActivity : AppCompatActivity() {

    private lateinit var repo: VpnRepository
    private lateinit var wgBackend: WireGuardBackend
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ServerAdapter

    private val uiItems = mutableListOf<ServerUiItem>()
    private val allUiItems = mutableListOf<ServerUiItem>()

    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View
    private lateinit var sideNav: View
    private lateinit var navLogo: ImageView
    private lateinit var navLogoBanner: ImageView

    private lateinit var filterAll: TextView
    private lateinit var filterFastest: TextView
    private lateinit var filterRecommended: TextView
    private lateinit var filterFavorites: TextView

    private lateinit var navTexts: List<View>
    private var isExpanded = false
    private var currentFilter = ServerFilter.ALL
    private lateinit var smartRoutingService: SmartRoutingService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.aio_server_list)

        repo = VpnRepository(this)
        wgBackend = WireGuardBackend.get(this)

        wgBackend.setPermissionCallback(object : WireGuardBackend.PermissionCallback {
            override fun onVpnPermissionRequired(intent: Intent) {
                startActivityForResult(intent, REQUEST_VPN_PERMISSION)
            }
        })

        recyclerView = findViewById(R.id.aioServerList)

        sideNav = findViewById(R.id.sideNav)
        navLogo = findViewById(R.id.navLogo)
        navLogoBanner = findViewById(R.id.navLogoBanner)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        filterAll = findViewById(R.id.filterAll)
        filterFastest = findViewById(R.id.filterFastest)
        filterRecommended = findViewById(R.id.filterRecommended)
        filterFavorites = findViewById(R.id.filterFavorites)
        smartRoutingService = SmartRoutingService()

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        setupRecyclerView()
        bindNavigation()
        bindFilters()
        loadServers()

        recyclerView.post {
            recyclerView.requestFocus()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.clipToPadding = false
        recyclerView.clipChildren = false

        adapter = ServerAdapter(
            items = emptyList(),
            onMoveToSidebar = {
                navServersContainer.requestFocus()
            }
        ) { server ->
            connectToServer(server)
        }

        recyclerView.adapter = adapter
    }

    private fun bindFilters() {
        val chips = listOf(filterAll, filterFastest, filterRecommended, filterFavorites)

        fun updateSelection(selected: TextView) {
            chips.forEach { it.isSelected = it === selected }
        }

        filterAll.setOnClickListener {
            currentFilter = ServerFilter.ALL
            updateSelection(filterAll)
            applyFilter()
        }

        filterFastest.setOnClickListener {
            currentFilter = ServerFilter.FASTEST
            updateSelection(filterFastest)
            applyFilter()
        }

        filterRecommended.setOnClickListener {
            currentFilter = ServerFilter.RECOMMENDED
            updateSelection(filterRecommended)
            applyFilter()
        }

        filterFavorites.setOnClickListener {
            currentFilter = ServerFilter.FAVORITES
            updateSelection(filterFavorites)
            applyFilter()
        }

        chips.forEachIndexed { index, chip ->
            chip.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false

                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        recyclerView.requestFocus()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (index == 0) {
                            navServersContainer.requestFocus()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            }
        }

        updateSelection(filterAll)
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            ServerFilter.ALL -> allUiItems
            ServerFilter.FASTEST -> allUiItems.sortedBy { parsePing(it.pingText) }.take(9)
            ServerFilter.RECOMMENDED -> allUiItems.sortedBy { parsePing(it.pingText) }.take(6)
            ServerFilter.FAVORITES -> emptyList()
        }

        uiItems.clear()
        uiItems.addAll(filtered)
        adapter.updateItems(uiItems.toList())
    }

    private fun parsePing(pingText: String): Int {
        return pingText.substringBefore(" ms").toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun bindNavigation() {
        val navContainers = listOf(
            navHomeContainer,
            navServersContainer,
            navAccountContainer,
            navSettingsContainer
        )

        navServersContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    view.isSelected = true
                    toggleSidebar(true)
                    view.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(120)
                        .start()
                } else {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()

                    view.postDelayed({
                        if (navContainers.none { it.hasFocus() }) {
                            navServersContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    filterAll.requestFocus()
                    true
                } else {
                    false
                }
            }

            container.setOnClickListener {
                when (container.id) {
                    R.id.navHomeContainer -> {
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }

                    R.id.navServersContainer -> {
                        // Already here
                    }

                    R.id.navAccountContainer -> {
                        startActivity(Intent(this, AccountActivity::class.java))
                        finish()
                    }

                    R.id.navSettingsContainer -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        finish()
                    }
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
            text.animate()
                .alpha(targetAlpha)
                .setDuration(200)
                .start()
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

    private fun loadServers() {
        lifecycleScope.launch {
            try {
                val servers: List<WgServerDto> = withContext(Dispatchers.IO) {
                    repo.servers()
                }

                Log.d(TAG, "Loaded servers count=${servers.size}")

                allUiItems.clear()
                allUiItems.addAll(
                    servers.map { server ->
                        ServerUiItem(
                            server = server,
                            pingText = "-- ms"
                        )
                    }
                )

                uiItems.clear()
                uiItems.addAll(allUiItems)
                adapter.updateItems(uiItems.toList())

                val ranked = withContext(Dispatchers.IO) {
                    if (ServerRankingCache.rankedServers != null && ServerRankingCache.isValid()) {
                        ServerRankingCache.rankedServers!!
                    } else {
                        smartRoutingService.rankServers(servers).also {
                            ServerRankingCache.set(it)
                        }
                    }
                }

                if (ranked.isNotEmpty()) {
                    allUiItems.clear()
                    allUiItems.addAll(
                        ranked.map { score ->
                            ServerUiItem(
                                server = score.server,
                                pingText = "${score.latencyMs} ms"
                            )
                        }
                    )

                    uiItems.clear()
                    uiItems.addAll(allUiItems)
                    adapter.updateItems(uiItems.toList())
                } else {
                    Log.w(TAG, "No ranked servers available, keeping original list")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load servers", e)
                Toast.makeText(
                    this@ServerListActivity,
                    "Failed to load servers: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun connectToServer(server: WgServerDto) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to server id=${server.id}, label=${server.label}")

                pendingServerId = server.id
                pendingServerLabel = server.displayName

                val config = repo.wgConfig(server.id)
                wgBackend.connect(config)

                Log.d(TAG, "Connected to server id=${server.id}")

                withContext(Dispatchers.Main) {
                    openHomeForConnectedServer(
                        server.id,
                        server.displayName
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for server id=${server.id}", e)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ServerListActivity,
                        "Connection failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_VPN_PERMISSION) return

        val serverId = pendingServerId
        val serverLabel = pendingServerLabel

        if (resultCode == RESULT_OK && serverId != null && serverLabel != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    wgBackend.retryConnectionAfterPermission()

                    withContext(Dispatchers.Main) {
                        openHomeForConnectedServer(serverId, serverLabel)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Retry connection failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ServerListActivity,
                            "Connection failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    pendingServerId = null
                    pendingServerLabel = null
                }
            }
        } else {
            pendingServerId = null
            pendingServerLabel = null
        }
    }

    private fun openHomeForConnectedServer(serverId: Int, serverLabel: String) {
        val intent = Intent(this, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("server_id", serverId)
            putExtra("server_label", serverLabel)
        }
        startActivity(intent)
        finish()
    }

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "ServerListActivity"
        private const val REQUEST_VPN_PERMISSION = 1001

        private var pendingServerId: Int? = null
        private var pendingServerLabel: String? = null
    }
}
