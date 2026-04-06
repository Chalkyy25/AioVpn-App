package com.aiovpn.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.aiovpn.app.BuildConfig
import com.aiovpn.app.auth.DeviceTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Updater {

    private const val TAG = "AIOVPN/Updater"
    private const val LATEST_URL = "https://panel.aiovpn.co.uk/api/app/latest"
    private const val MAX_APK_SIZE_BYTES = 500L * 1024L * 1024L

    private val ALLOWED_HOSTS = setOf(
        "panel.aiovpn.co.uk",
        "aiovpn.co.uk"
    )

    private lateinit var appContext: Context
    private lateinit var updatePrefs: UpdatePrefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    @Volatile
    private var isUpdating = false

    fun init(context: Context) {
        appContext = context.applicationContext
        updatePrefs = UpdatePrefs(appContext)
    }

    fun monitorForUpdates() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Debug build: running updater for testing")
        }
        checkNow()
    }

    fun markUserAccepted(versionCode: Long) {
        scope.launch {
            updatePrefs.setConsentedVersion(versionCode)
        }
    }

    fun checkNow() {
        scope.launch {
            try {
                Log.d(TAG, "Updater checkNow started")
                _state.value = UpdateState.Checking

                val update = fetchLatest()
                val currentVersion = BuildConfig.VERSION_CODE.toLong()

                Log.d(TAG, "Updater currentVersion=$currentVersion latest=${update?.versionCode}")

                if (update != null && update.versionCode > currentVersion) {
                    updatePrefs.setSeenVersion(update.versionCode)

                    _state.value = UpdateState.Available(
                        versionCode = update.versionCode,
                        versionName = update.versionName,
                        mandatory = update.mandatory,
                        releaseNotes = update.releaseNotes
                    )
                } else {
                    _state.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _state.value = UpdateState.Error(e.message ?: "Update check failed")
            }
        }
    }

    fun startUpdate() {
        if (isUpdating) return

        when (val current = _state.value) {
            is UpdateState.StartingDownload,
            is UpdateState.Downloading,
            is UpdateState.Verifying,
            is UpdateState.InstallPermissionRequired,
            is UpdateState.ReadyToInstall,
            is UpdateState.LaunchingInstaller -> {
                Log.d(TAG, "Update already in progress: $current")
                return
            }

            else -> Unit
        }

        scope.launch {
            try {
                isUpdating = true

                val update = fetchLatest() ?: throw IOException("No update metadata found")
                val currentVersion = BuildConfig.VERSION_CODE.toLong()

                if (update.versionCode <= currentVersion) {
                    _state.value = UpdateState.Idle
                    return@launch
                }

                updatePrefs.setConsentedVersion(update.versionCode)
                _state.value = UpdateState.StartingDownload

                val apkFile = downloadApk(update)

                _state.value = UpdateState.Verifying
                verifySha256(apkFile, update.sha256)

                if (!canRequestPackageInstalls()) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    _state.value = UpdateState.InstallPermissionRequired(intent)
                    return@launch
                }

                val installIntent = buildInstallIntent(apkFile)
                _state.value = UpdateState.ReadyToInstall(installIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Update start failed", e)
                _state.value = UpdateState.Error(e.message ?: "Update failed")
            } finally {
                isUpdating = false
            }
        }
    }

    private suspend fun requireDeviceToken(): String {
        return DeviceTokenStore(appContext)
            .getDeviceToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Missing device token")
    }

    private suspend fun fetchLatest(): LatestAppResponse? {
        Log.d(TAG, "Calling $LATEST_URL")
        Log.d(TAG, "Using app version code ${BuildConfig.VERSION_CODE}")

        val token = requireDeviceToken()
        val connection = URL(LATEST_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-App-Version-Code", BuildConfig.VERSION_CODE.toString())
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.connect()

            Log.d(TAG, "Update response code=${connection.responseCode}")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Update check failed: ${connection.responseCode}")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val response = LatestAppResponse(
                id = json.getLong("id"),
                versionCode = json.getLong("version_code"),
                versionName = json.getString("version_name"),
                mandatory = json.optBoolean("mandatory", false),
                releaseNotes = json.optString("release_notes").takeIf { it.isNotBlank() },
                sha256 = json.getString("sha256"),
                apkUrl = json.getString("apk_url")
            )

            validateApkUrl(response.apkUrl)
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun validateApkUrl(apkUrl: String) {
        val url = URL(apkUrl)
        if (url.protocol != "https" || url.host !in ALLOWED_HOSTS) {
            throw SecurityException("Untrusted APK URL: $apkUrl")
        }
    }

    private suspend fun downloadApk(update: LatestAppResponse): File {
        val token = requireDeviceToken()
        val url = URL(update.apkUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
            connection.setRequestProperty("X-App-Version-Code", BuildConfig.VERSION_CODE.toString())
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("APK download failed: ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong
            if (totalBytes > MAX_APK_SIZE_BYTES) {
                throw IOException("APK too large: $totalBytes bytes")
            }

            val updatesDir = File(appContext.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            val apkFile = File(updatesDir, "aiovpn-update-${update.versionCode}.apk")

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        downloaded += read

                        if (totalBytes > 0L && downloaded > totalBytes) {
                            throw IOException("Download size mismatch")
                        }

                        _state.value = UpdateState.Downloading(
                            bytesDownloaded = downloaded,
                            bytesTotal = totalBytes.coerceAtLeast(0L)
                        )
                    }
                }
            }

            if (!apkFile.exists() || apkFile.length() <= 0) {
                throw IOException("Downloaded APK file is empty")
            }

            if (totalBytes > 0L && apkFile.length() != totalBytes) {
                throw IOException("Downloaded APK size mismatch")
            }

            return apkFile
        } finally {
            connection.disconnect()
        }
    }

    private fun verifySha256(file: File, expectedHex: String) {
        val expected = expectedHex.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }

        val actual = digest.digest().joinToString("") { "%02x".format(it) }

        if (actual != expected) {
            throw SecurityException("APK SHA-256 mismatch")
        }
    }

    private fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun buildInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
