/*
 * Copyright © 2017-2026 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.aiovpn.app.home

import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aiovpn.app.login.ServerListActivity
import com.aiovpn.app.util.SettingsStore
import com.aiovpn.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitTunnelingActivity : AppCompatActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var appAdapter: AppListAdapter

    private lateinit var sideNav: View
    private lateinit var navLogo: ImageView
    private lateinit var navLogoBanner: ImageView
    private lateinit var navHomeContainer: View
    private lateinit var navServersContainer: View
    private lateinit var navAccountContainer: View
    private lateinit var navSettingsContainer: View
    private lateinit var navTexts: List<View>
    private var isExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_split_tunneling)

        settingsStore = SettingsStore(this)

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

        val recyclerView = findViewById<RecyclerView>(R.id.appListRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        appAdapter = AppListAdapter { pkg, isChecked ->
            toggleAppExclusion(pkg, isChecked)
        }
        recyclerView.adapter = appAdapter

        bindNavigation()
        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val mode = settingsStore.splitTunnelModeFlow.first()
            val excluded = settingsStore.excludedAppsFlow.first()

            val apps = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                packages
                    .filter {
                        it.applicationInfo != null &&
                                pm.getLaunchIntentForPackage(it.packageName) != null &&
                                it.packageName != packageName
                    }
                    .map {
                        AppInfo(
                            name = it.applicationInfo!!.loadLabel(pm).toString(),
                            packageName = it.packageName,
                            icon = it.applicationInfo!!.loadIcon(pm),
                            isExcluded = mode == SettingsStore.SplitTunnelMode.EXCLUDE_APPS &&
                                    excluded.contains(it.packageName)
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            }

            appAdapter.submitList(apps)
        }
    }

    private fun toggleAppExclusion(packageName: String, isExcluded: Boolean) {
        lifecycleScope.launch {
            val current = settingsStore.excludedAppsFlow.first().toMutableSet()

            if (isExcluded) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }

            settingsStore.setExcludedApps(current)

            if (current.isEmpty()) {
                settingsStore.setSplitTunnelMode(SettingsStore.SplitTunnelMode.OFF)
            } else {
                settingsStore.setSplitTunnelMode(SettingsStore.SplitTunnelMode.EXCLUDE_APPS)
            }
        }
    }

    private fun bindNavigation() {
        val navContainers = listOf(navHomeContainer, navServersContainer, navAccountContainer, navSettingsContainer)
        navSettingsContainer.isSelected = true

        navContainers.forEach { container ->
            container.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    navContainers.forEach { it.isSelected = false }
                    toggleSidebar(true)
                    v.animate().scaleX(1.03f).scaleY(1.03f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    v.postDelayed({
                        if (navContainers.none { it.hasFocus() }) {
                            navSettingsContainer.isSelected = true
                            toggleSidebar(false)
                        }
                    }, 50)
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
        val widthAnimator = ValueAnimator.ofInt(sideNav.width, targetWidth)
        widthAnimator.addUpdateListener { sideNav.layoutParams = sideNav.layoutParams.apply { width = it.animatedValue as Int } }
        widthAnimator.duration = 250
        widthAnimator.interpolator = AccelerateDecelerateInterpolator()
        widthAnimator.start()
        navTexts.forEach { it.animate().alpha(if (expand) 1f else 0f).setDuration(200).start() }
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

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    data class AppInfo(val name: String, val packageName: String, val icon: Drawable, var isExcluded: Boolean)

    class AppListAdapter(private val onToggle: (String, Boolean) -> Unit) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {
        private var list = emptyList<AppInfo>()
        fun submitList(newList: List<AppInfo>) { list = newList; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_split_tunnel_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.icon.setImageDrawable(item.icon)
            holder.name.text = item.name
            holder.checkbox.isChecked = item.isExcluded
            holder.itemView.setOnClickListener {
                item.isExcluded = !item.isExcluded
                holder.checkbox.isChecked = item.isExcluded
                onToggle(item.packageName, item.isExcluded)
            }
        }

        override fun getItemCount() = list.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val name: TextView = v.findViewById(R.id.appName)
            val checkbox: CheckBox = v.findViewById(R.id.appCheckbox)
        }
    }
}
