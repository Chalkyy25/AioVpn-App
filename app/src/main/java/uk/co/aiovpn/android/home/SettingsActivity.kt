package uk.co.aiovpn.android.home

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import uk.co.aiovpn.android.App
import uk.co.aiovpn.android.BuildConfig
import uk.co.aiovpn.android.R
import uk.co.aiovpn.android.login.ServerListActivity
import uk.co.aiovpn.android.updater.UpdateState
import uk.co.aiovpn.android.updater.Updater
import uk.co.aiovpn.android.util.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore

    private lateinit var sideNav: View
    private lateinit var navLogo: ImageView
    private lateinit var navLogoBanner: ImageView
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View

    private lateinit var settingKillSwitch: TextView
    private lateinit var settingAutoConnect: TextView
    private lateinit var settingSplitTunneling: TextView

    private lateinit var currentVersionText: TextView
    private lateinit var updateStatusText: TextView
    private lateinit var checkUpdatesActionText: TextView
    private lateinit var updateReleaseNotes: TextView
    private lateinit var settingCheckUpdates: View

    private lateinit var navTexts: List<View>

    private var isExpanded = false
    private var lastToastKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsStore = SettingsStore(this)

        sideNav = findViewById(R.id.sideNav)
        navLogo = findViewById(R.id.navLogo)
        navLogoBanner = findViewById(R.id.navLogoBanner)
        navHomeContainer = findViewById(R.id.navHomeContainer)
        navServersContainer = findViewById(R.id.navServersContainer)
        navAccountContainer = findViewById(R.id.navAccountContainer)
        navSettingsContainer = findViewById(R.id.navSettingsContainer)

        settingKillSwitch = findViewById(R.id.settingKillSwitch)
        settingAutoConnect = findViewById(R.id.settingAutoConnect)
        settingSplitTunneling = findViewById(R.id.settingSplitTunneling)

        currentVersionText = findViewById(R.id.currentVersionText)
        updateStatusText = findViewById(R.id.updateStatusText)
        checkUpdatesActionText = findViewById(R.id.checkUpdatesActionText)
        updateReleaseNotes = findViewById(R.id.updateReleaseNotes)
        settingCheckUpdates = findViewById(R.id.settingCheckUpdates)

        navTexts = listOf(
            findViewById(R.id.navHome),
            findViewById(R.id.navServers),
            findViewById(R.id.navAccount),
            findViewById(R.id.navSettings)
        )

        currentVersionText.text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        updateStatusText.text = "Up to date"
        checkUpdatesActionText.text = "Check"
        updateReleaseNotes.text = ""
        updateReleaseNotes.visibility = View.GONE

        bindNavigation()
        bindSettings()
        observeSettings()
        observeUpdater()
    }

    override fun onResume() {
        super.onResume()

        val state = Updater.state.value
        if (state is UpdateState.InstallPermissionRequired) {
            val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                packageManager.canRequestPackageInstalls()
            } else {
                true
            }

            if (canInstall) {
                lifecycleScope.launch {
                    try {
                        App.get().deviceRepository.getOrRegisterDeviceToken()
                        Updater.startUpdate()
                    } catch (e: Exception) {
                        updateStatusText.text = "Update failed"
                        checkUpdatesActionText.text = "Retry"
                        setCheckUpdatesEnabled(true)
                        updateReleaseNotes.text = e.message ?: "Failed to prepare device token"
                        updateReleaseNotes.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun bindSettings() {
        settingKillSwitch.setOnClickListener {
            Toast.makeText(
                this@SettingsActivity,
                "Kill Switch is coming soon",
                Toast.LENGTH_SHORT
            ).show()
        }

        settingAutoConnect.setOnClickListener {
            lifecycleScope.launch {
                val current = settingsStore.autoConnectFlow.first()
                settingsStore.setAutoConnect(!current)
            }
        }

        settingSplitTunneling.setOnClickListener {
            startActivity(Intent(this, SplitTunnelingActivity::class.java))
        }

        settingCheckUpdates.setOnClickListener {
            when (val state = Updater.state.value) {
                is UpdateState.Idle,
                is UpdateState.Error -> {
                    lifecycleScope.launch {
                        try {
                            setCheckUpdatesEnabled(false)
                            updateStatusText.text = "Preparing check..."
                            checkUpdatesActionText.text = "Please wait"

                            App.get().deviceRepository.getOrRegisterDeviceToken()
                            Updater.checkNow()
                        } catch (e: Exception) {
                            updateStatusText.text = "Update failed"
                            checkUpdatesActionText.text = "Retry"
                            setCheckUpdatesEnabled(true)
                            updateReleaseNotes.text = e.message ?: "Failed to prepare device token"
                            updateReleaseNotes.visibility = View.VISIBLE
                        }
                    }
                }

                is UpdateState.Available -> {
                    lifecycleScope.launch {
                        try {
                            setCheckUpdatesEnabled(false)
                            updateStatusText.text = "Preparing update..."
                            checkUpdatesActionText.text = "Fetching"

                            App.get().deviceRepository.getOrRegisterDeviceToken()
                            Updater.startUpdate()
                        } catch (e: Exception) {
                            updateStatusText.text = "Update failed"
                            checkUpdatesActionText.text = "Retry"
                            setCheckUpdatesEnabled(true)
                            updateReleaseNotes.text = e.message ?: "Failed to prepare device token"
                            updateReleaseNotes.visibility = View.VISIBLE
                        }
                    }
                }

                is UpdateState.InstallPermissionRequired -> {
                    startActivity(state.intent)
                }

                is UpdateState.ReadyToInstall -> {
                    updateStatusText.text = "Opening installer..."
                    checkUpdatesActionText.text = "Opening"
                    setCheckUpdatesEnabled(false)
                    startActivity(state.intent)
                }

                is UpdateState.Checking,
                is UpdateState.StartingDownload,
                is UpdateState.Downloading,
                is UpdateState.Verifying,
                is UpdateState.LaunchingInstaller -> {
                    // Busy. Ignore repeated presses.
                }
            }
        }

        settingKillSwitch.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }

        settingAutoConnect.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }

        settingSplitTunneling.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }

        settingCheckUpdates.setOnFocusChangeListener { v, hasFocus ->
            animateSettingRow(v, hasFocus)
        }
    }

    private fun observeSettings() {
        settingKillSwitch.text = "Kill Switch  •  Coming soon"

        lifecycleScope.launch {
            settingsStore.autoConnectFlow.collect { enabled ->
                settingAutoConnect.text = if (enabled) {
                    "Auto Connect  •  On"
                } else {
                    "Auto Connect  •  Off"
                }
            }
        }

        lifecycleScope.launch {
            settingsStore.excludedAppsFlow.collect { apps ->
                settingSplitTunneling.text = if (apps.isEmpty()) {
                    "Split Tunneling  •  Off"
                } else {
                    "Split Tunneling  •  ${apps.size} apps"
                }
            }
        }
    }

    private fun observeUpdater() {
        lifecycleScope.launch {
            Updater.state.collect { state ->
                when (state) {
                    is UpdateState.Idle -> {
                        updateStatusText.text = "Up to date"
                        checkUpdatesActionText.text = "Check"
                        setCheckUpdatesEnabled(true)
                        updateReleaseNotes.text = ""
                        updateReleaseNotes.visibility = View.GONE
                        lastToastKey = null
                    }

                    is UpdateState.Checking -> {
                        updateStatusText.text = "Checking for updates..."
                        checkUpdatesActionText.text = "Checking"
                        setCheckUpdatesEnabled(false)
                        updateReleaseNotes.text = ""
                        updateReleaseNotes.visibility = View.GONE
                    }

                    is UpdateState.Available -> {
                        updateStatusText.text = "Update available • ${state.versionName}"
                        checkUpdatesActionText.text = "Update"
                        setCheckUpdatesEnabled(true)

                        val notes = state.releaseNotes?.trim().orEmpty()
                        if (notes.isNotEmpty()) {
                            updateReleaseNotes.text = notes
                            updateReleaseNotes.visibility = View.VISIBLE
                        } else {
                            updateReleaseNotes.text = ""
                            updateReleaseNotes.visibility = View.GONE
                        }

                        showToastOnce(
                            key = "available_${state.versionCode}",
                            message = "Update available: ${state.versionName}"
                        )
                    }

                    is UpdateState.StartingDownload -> {
                        updateStatusText.text = "Preparing update..."
                        checkUpdatesActionText.text = "Fetching"
                        setCheckUpdatesEnabled(false)
                    }

                    is UpdateState.Downloading -> {
                        setCheckUpdatesEnabled(false)

                        if (state.bytesTotal > 0L) {
                            val percent = ((state.bytesDownloaded * 100L) / state.bytesTotal)
                                .toInt()
                                .coerceIn(0, 100)
                            updateStatusText.text = "Downloading... $percent%"
                            checkUpdatesActionText.text = "$percent%"
                        } else {
                            updateStatusText.text = "Downloading update..."
                            checkUpdatesActionText.text = "Downloading"
                        }
                    }

                    is UpdateState.Verifying -> {
                        updateStatusText.text = "Verifying package..."
                        checkUpdatesActionText.text = "Verifying"
                        setCheckUpdatesEnabled(false)
                    }

                    is UpdateState.ReadyToInstall -> {
                        updateStatusText.text = "Ready to install"
                        checkUpdatesActionText.text = "Install"
                        setCheckUpdatesEnabled(true)

                        showToastOnce(
                            key = "ready_install",
                            message = "Update downloaded and ready to install"
                        )
                    }

                    is UpdateState.InstallPermissionRequired -> {
                        updateStatusText.text = "Allow installs to continue"
                        checkUpdatesActionText.text = "Allow"
                        setCheckUpdatesEnabled(true)

                        showToastOnce(
                            key = "install_permission",
                            message = "Allow app installs to continue update"
                        )
                    }

                    is UpdateState.LaunchingInstaller -> {
                        updateStatusText.text = "Opening installer..."
                        checkUpdatesActionText.text = "Opening"
                        setCheckUpdatesEnabled(false)
                    }

                    is UpdateState.Error -> {
                        updateStatusText.text = "Update failed"
                        checkUpdatesActionText.text = "Retry"
                        setCheckUpdatesEnabled(true)

                        updateReleaseNotes.text = state.message
                        updateReleaseNotes.visibility = View.VISIBLE

                        showToastOnce(
                            key = "error_${state.message}",
                            message = state.message
                        )
                    }
                }
            }
        }
    }

    private fun setCheckUpdatesEnabled(enabled: Boolean) {
        settingCheckUpdates.isEnabled = enabled
        settingCheckUpdates.isClickable = enabled
        settingCheckUpdates.alpha = if (enabled) 1f else 0.72f
    }

    private fun showToastOnce(key: String, message: String) {
        if (lastToastKey == key) return
        lastToastKey = key
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun bindNavigation() {
        val navContainers = listOf(
            navHomeContainer,
            navServersContainer,
            navAccountContainer,
            navSettingsContainer
        )

        navSettingsContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    v.isSelected = true
                    toggleSidebar(true)
                    v.animate()
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(120)
                        .start()
                } else {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()

                    v.postDelayed({
                        if (navContainers.none { it.hasFocus() }) {
                            navSettingsContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
                }
            }

            container.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN &&
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    settingKillSwitch.requestFocus()
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
                        startActivity(Intent(this, ServerListActivity::class.java))
                        finish()
                    }

                    R.id.navAccountContainer -> {
                        startActivity(Intent(this, AccountActivity::class.java))
                        finish()
                    }

                    R.id.navSettingsContainer -> {
                        // Already here
                    }
                }
            }
        }
    }

    private fun animateSettingRow(view: View, hasFocus: Boolean) {
        if (hasFocus) {
            view.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(120)
                .start()
        } else {
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120)
                .start()
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

    private fun Int.toPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
}
