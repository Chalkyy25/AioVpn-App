package uk.co.aiovpn.android.api

data class WgServersResponse(
    val data: List<WgServerDto>
)

data class WgServerDto(
    val id: Int,
    val name: String,
    val ip: String,
    val port: Int,
    val country_code: String? = null,
    val city: String? = null,
    val label: String? = null,
    val mtu: Int = 1340
) {
    val displayName: String
        get() = label ?: name

    val pingHost: String
        get() = ip
}