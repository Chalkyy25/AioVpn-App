package com.aiovpn.app.home

data class FastServerItem(
    val id: Int,
    val label: String,
    val cityName: String? = null,
    val pingText: String,
    val countryCode: String? = null,
    val isAllServers: Boolean = false
)
