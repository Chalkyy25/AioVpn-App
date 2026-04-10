package uk.co.aiovpn.android.updater

import android.content.Intent

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object StartingDownload : UpdateState()
    data class Downloading(
        val bytesDownloaded: Long,
        val bytesTotal: Long
    ) : UpdateState()
    object Verifying : UpdateState()
    object LaunchingInstaller : UpdateState()

    data class Available(
        val versionCode: Long,
        val versionName: String,
        val mandatory: Boolean,
        val releaseNotes: String?
    ) : UpdateState()

    data class InstallPermissionRequired(
        val intent: Intent
    ) : UpdateState()

    data class ReadyToInstall(
        val intent: Intent
    ) : UpdateState()

    data class Error(
        val message: String
    ) : UpdateState()
}

data class LatestAppResponse(
    val id: Long,
    val versionCode: Long,
    val versionName: String,
    val mandatory: Boolean,
    val releaseNotes: String?,
    val sha256: String,
    val apkUrl: String
)