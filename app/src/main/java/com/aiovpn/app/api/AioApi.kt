package com.aiovpn.app.api

import android.net.TrafficStats
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class AioApi(
    private val baseUrl: String = "https://panel.aiovpn.co.uk"
) {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val loginAdapter by lazy { moshi.adapter(LoginResponse::class.java) }
    private val profileAdapter by lazy { moshi.adapter(ProfileResponse::class.java) }
    private val wgServersAdapter by lazy { moshi.adapter(WgServersResponse::class.java) }
    private val deviceRegisterAdapter by lazy { moshi.adapter(DeviceRegisterResponse::class.java) }

    @Throws(IOException::class)
    fun login(username: String, password: String): LoginResponse = withSocketTag(NETWORK_TAG_AUTH) {
        val body = FormBody.Builder()
            .add("username", username.trim())
            .add("password", password)
            .build()

        val req = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IOException("Login failed ${res.code}: $raw")
            }
            loginAdapter.fromJson(raw) ?: throw IOException("Bad JSON: $raw")
        }
    }

    @Throws(IOException::class)
    fun logout(token: String) = withSocketTag(NETWORK_TAG_AUTH) {
        val req = Request.Builder()
            .url("$baseUrl/api/auth/logout")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .post(FormBody.Builder().build())
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IOException("Logout failed ${res.code}: $raw")
            }
        }
    }

    @Throws(IOException::class)
    fun registerDeviceToken(
        deviceUuid: String,
        model: String?,
        osVersion: String?,
        appVersionCode: Int
    ): String = withSocketTag(NETWORK_TAG_AUTH) {
        val body = FormBody.Builder()
            .add("device_uuid", deviceUuid)
            .add("model", model.orEmpty())
            .add("os_version", osVersion.orEmpty())
            .add("app_version_code", appVersionCode.toString())
            .build()

        val req = Request.Builder()
            .url("$baseUrl/api/devices/register-token")
            .header("Accept", "application/json")
            .post(body)
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            Log.d(TAG, "Device register response code=${res.code} body=$raw")

            if (!res.isSuccessful) {
                throw IOException("Device token registration failed ${res.code}: $raw")
            }

            val parsed = deviceRegisterAdapter.fromJson(raw)
                ?: throw IOException("Bad JSON: $raw")

            parsed.device_token
        }
    }

    @Throws(IOException::class)
    fun profile(token: String): ProfileResponse = withSocketTag(NETWORK_TAG_PROFILE) {
        val req = Request.Builder()
            .url("$baseUrl/api/auth/me")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IOException("Profile failed ${res.code}: $raw")
            }
            profileAdapter.fromJson(raw) ?: throw IOException("Bad JSON: $raw")
        }
    }

    @Throws(IOException::class)
    fun wgServers(token: String): List<WgServerDto> = withSocketTag(NETWORK_TAG_SERVERS) {
        val req = Request.Builder()
            .url("$baseUrl/api/wg/servers")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()

            if (!res.isSuccessful) {
                Log.e(TAG, "WG servers failed code=${res.code} body=$raw")
                throw IOException("WG servers failed ${res.code}: $raw")
            }

            val parsed = try {
                wgServersAdapter.fromJson(raw)
            } catch (e: Exception) {
                Log.e(TAG, "WG servers parsing failed body=$raw", e)
                throw IOException("Parsing failed: ${e.message}", e)
            } ?: throw IOException("Empty response body")

            parsed.data
        }
    }

    @Throws(IOException::class)
    fun wgConfig(token: String, serverId: Int): String = withSocketTag(NETWORK_TAG_CONFIG) {
        val req = Request.Builder()
            .url("$baseUrl/api/wg/config?server_id=$serverId")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()

            Log.d(TAG, "WG CONFIG RESPONSE code=${res.code} serverId=$serverId")
            Log.d(TAG, "WG CONFIG RAW:\n$raw")

            if (!res.isSuccessful) {
                throw IOException("Config failed ${res.code}: $raw")
            }
            raw
        }
    }

    private inline fun <T> withSocketTag(tag: Int, block: () -> T): T {
        TrafficStats.setThreadStatsTag(tag)
        return try {
            block()
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    private companion object {
        private const val TAG = "AioApi"
        private const val NETWORK_TAG_AUTH = 0xA101
        private const val NETWORK_TAG_SERVERS = 0xA102
        private const val NETWORK_TAG_CONFIG = 0xA103
        private const val NETWORK_TAG_PROFILE = 0xA104
    }
}

data class LoginResponse(
    val token: String,
    val user: UserDto? = null
)

data class UserDto(
    val id: Int,
    val username: String,
    val active: Boolean? = null,
    val expires: String? = null,
    val max_conn: Int? = null
)

data class DeviceRegisterResponse(
    val device_token: String
)

data class ProfileResponse(
    val id: Int,
    val username: String,
    val expires: String? = null,
    val max_conn: Int? = null
)