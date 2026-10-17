package com.neardrop.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.neardrop.app.ui.components.ClayButton
import com.neardrop.app.ui.components.ClayDialog
import com.neardrop.app.ui.theme.Clay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseUrl: String,
    val whatsNew: List<String>
)

data class UpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val info: AppUpdateInfo
)

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    READY,
    ERROR
}

object UpdateInstaller {
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            Toast.makeText(context, "APK file not found or incomplete", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(context, "Please allow NearDrop to install updates", Toast.LENGTH_LONG).show()
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            try {
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = contentUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Failed to launch installer: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

object UpdateChecker {
    const val CURRENT_VERSION_CODE = 5
    const val CURRENT_VERSION_NAME = "0.2.3"
    private const val UPDATE_URL = "https://raw.githubusercontent.com/Kmrr-Gautam/NearDrop/main/version.json"

    val latestWhatsNew = listOf(
        "⚡ In-app update downloader: download & install updates seamlessly inside NearDrop without browser redirects",
        "📱 Instant QR scanner fix: zero-latency camera scanning for PC screens & monitors",
        "🌐 Show device local IP in Settings About section with 1-tap copy",
        "🔗 Developer GitHub profile link (@Kmrr-Gautam) in Settings",
        "🚀 Parabolic file flying animations and modern Claymorphic transfer cards"
    )

    fun getInstalledVersionCode(ctx: Context?): Int {
        if (ctx == null) return CURRENT_VERSION_CODE
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
            }
        } catch (e: Exception) {
            CURRENT_VERSION_CODE
        }
    }

    suspend fun checkUpdate(ctx: Context? = null): Result<UpdateCheckResult> = withContext(Dispatchers.IO) {
        val installedCode = getInstalledVersionCode(ctx)
        runCatching {
            val urlWithBuster = "$UPDATE_URL?nocache=${System.currentTimeMillis()}"
            val conn = (URL(urlWithBuster).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "NearDrop-Android/$CURRENT_VERSION_NAME")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val code = json.optInt("versionCode", CURRENT_VERSION_CODE)
                val name = json.optString("versionName", CURRENT_VERSION_NAME)
                val url = json.optString("releaseUrl", "https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v$name.apk")
                val whatsNewArr = json.optJSONArray("whatsNew")
                val notes = mutableListOf<String>()
                if (whatsNewArr != null) {
                    for (i in 0 until whatsNewArr.length()) {
                        notes.add(whatsNewArr.getString(i))
                    }
                }
                val info = AppUpdateInfo(code, name, url, notes.ifEmpty { latestWhatsNew })
                UpdateCheckResult(isUpdateAvailable = code > installedCode, info = info)
            } else {
                throw IllegalStateException("GitHub responded with HTTP ${conn.responseCode}")
            }
        }
    }

    suspend fun check(ctx: Context? = null): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        val res = checkUpdate(ctx)
        if (res.isSuccess) {
            val checkRes = res.getOrThrow()
            if (checkRes.isUpdateAvailable) Result.success(checkRes.info) else Result.success(null)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    private fun openUrlWithRedirects(initialUrl: String): HttpURLConnection {
        var url = URL(initialUrl)
        var redirects = 0
        while (redirects < 7) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "NearDrop-Android-Updater")
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                val location = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                url = if (location.startsWith("http://") || location.startsWith("https://")) {
                    URL(location)
                } else {
                    URL(url, location)
                }
                redirects++
            } else {
                return conn
            }
        }
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "NearDrop-Android-Updater")
        }
    }

    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val apkFileName = "neardrop-v${info.versionName}.apk"
            val targetApk = File(updatesDir, apkFileName)
            val tempApk = File(updatesDir, "$apkFileName.download")

            if (tempApk.exists()) {
                tempApk.delete()
            }

            val conn = openUrlWithRedirects(info.releaseUrl)
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Server returned HTTP ${conn.responseCode}")
            }

            val contentLength = conn.contentLengthLong
            var totalBytesRead = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempApk).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            tempApk.delete()
                            throw java.util.concurrent.CancellationException("Download cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        onProgress(totalBytesRead, contentLength)
                    }
                    output.flush()
                }
            }

            if (tempApk.length() < 100_000) {
                tempApk.delete()
                throw IllegalStateException("Downloaded file is incomplete or invalid")
            }

            if (targetApk.exists()) {
                targetApk.delete()
            }

            if (!tempApk.renameTo(targetApk)) {
                tempApk.copyTo(targetApk, overwrite = true)
                tempApk.delete()
            }

            targetApk
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }
}

@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(DownloadStatus.IDLE) }
    var bytesDownloaded by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(-1L) }
    var errorMessage by remember { mutableStateOf("") }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(info.versionName) {
        val updatesDir = File(ctx.externalCacheDir ?: ctx.cacheDir, "updates")
        val targetApk = File(updatesDir, "neardrop-v${info.versionName}.apk")
        if (targetApk.exists() && targetApk.length() > 500_000) {
            downloadedFile = targetApk
            status = DownloadStatus.READY
        }
    }

    fun startDownload() {
        status = DownloadStatus.DOWNLOADING
        errorMessage = ""
        bytesDownloaded = 0L
        totalBytes = -1L
        downloadJob = scope.launch {
            val result = UpdateChecker.downloadApk(ctx, info) { bytes, total ->
                bytesDownloaded = bytes
                totalBytes = total
            }
            if (result.isSuccess) {
                val file = result.getOrThrow()
                downloadedFile = file
                status = DownloadStatus.READY
                UpdateInstaller.installApk(ctx, file)
            } else {
                val ex = result.exceptionOrNull()
                if (ex !is java.util.concurrent.CancellationException) {
                    errorMessage = ex?.localizedMessage ?: "Failed to download update"
                    status = DownloadStatus.ERROR
                }
            }
        }
    }

    val dialogTitle = when (status) {
        DownloadStatus.IDLE -> "Update Available: v${info.versionName}"
        DownloadStatus.DOWNLOADING -> "Downloading Update v${info.versionName}"
        DownloadStatus.READY -> "Update Ready to Install"
        DownloadStatus.ERROR -> "Update Download Failed"
    }

    ClayDialog(
        title = dialogTitle,
        onDismiss = {
            if (status == DownloadStatus.DOWNLOADING) {
                downloadJob?.cancel()
            }
            onDismiss()
        },
        confirm = {
            when (status) {
                DownloadStatus.IDLE -> {
                    ClayButton(
                        text = "Download & Install",
                        onClick = { startDownload() },
                        color = Clay.Mint,
                        icon = Icons.Default.Download
                    )
                }
                DownloadStatus.DOWNLOADING -> {
                    ClayButton(
                        text = "Downloading...",
                        onClick = {},
                        enabled = false,
                        color = Clay.Mint
                    )
                }
                DownloadStatus.READY -> {
                    ClayButton(
                        text = "Install Now",
                        onClick = {
                            downloadedFile?.let { UpdateInstaller.installApk(ctx, it) }
                        },
                        color = Clay.Mint,
                        icon = Icons.Default.SystemUpdate
                    )
                }
                DownloadStatus.ERROR -> {
                    ClayButton(
                        text = "Retry",
                        onClick = { startDownload() },
                        color = Clay.Mint,
                        icon = Icons.Default.Refresh
                    )
                }
            }
        },
        dismiss = {
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    ClayButton(
                        text = "Cancel",
                        onClick = {
                            downloadJob?.cancel()
                            status = DownloadStatus.IDLE
                        },
                        color = Clay.Peach,
                        icon = Icons.Default.Close
                    )
                }
                DownloadStatus.READY -> {
                    ClayButton("Close", onDismiss, color = Clay.Peach)
                }
                DownloadStatus.ERROR -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ClayButton(
                            text = "Browser",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                ctx.startActivity(intent)
                                onDismiss()
                            },
                            color = Clay.LavenderDeep,
                            icon = Icons.Default.OpenInBrowser
                        )
                        ClayButton("Close", onDismiss, color = Clay.Peach)
                    }
                }
                DownloadStatus.IDLE -> {
                    ClayButton("Later", onDismiss, color = Clay.Peach)
                }
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (status) {
                DownloadStatus.IDLE -> {
                    Text(
                        "A newer, improved version of NearDrop is ready!",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "What's new in v${info.versionName}:",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    info.whatsNew.forEach { note ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                note,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                DownloadStatus.DOWNLOADING -> {
                    val fraction = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                    val percentage = (fraction * 100).toInt()

                    Text(
                        "Downloading update package in-app...",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Clay.Mint,
                        trackColor = Clay.Mint.copy(alpha = 0.2f)
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${UpdateChecker.formatBytes(bytesDownloaded)} / ${if (totalBytes > 0) UpdateChecker.formatBytes(totalBytes) else "..."}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "$percentage%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        "The package installer will launch automatically once download completes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                DownloadStatus.READY -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Ready",
                            tint = Clay.Mint,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Download Complete!",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "NearDrop v${info.versionName} is ready to install.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        "Tap 'Install Now' to proceed with the system update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                DownloadStatus.ERROR -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Clay.Peach,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                "Download Interrupted",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Clay.Peach
                            )
                            Text(
                                errorMessage.ifBlank { "Could not complete update download." },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        "You can retry in-app or open the download link in your browser as a fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
