package com.neardrop.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neardrop.app.ui.components.ClayButton
import com.neardrop.app.ui.components.ClayDialog
import com.neardrop.app.ui.theme.Clay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

object UpdateChecker {
    const val CURRENT_VERSION_CODE = 4
    const val CURRENT_VERSION_NAME = "0.2.2"
    private const val UPDATE_URL = "https://raw.githubusercontent.com/Kmrr-Gautam/NearDrop/main/version.json"

    val latestWhatsNew = listOf(
        "📱 Instant QR scanner fix: zero-latency camera scanning for PC screens & monitors",
        "🌐 Show device local IP in Settings About section with 1-tap copy",
        "⚡ Fixed in-app update checks with live cache busting and instant APK downloads",
        "🔗 Developer GitHub profile link (@Kmrr-Gautam) in Settings",
        "🚀 Parabolic file flying animations and modern Claymorphic transfer cards"
    )

    fun getInstalledVersionCode(ctx: Context?): Int {
        if (ctx == null) return CURRENT_VERSION_CODE
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
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
}

@Composable
fun UpdateDialog(
    info: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    ClayDialog(
        title = "Update Available: v${info.versionName}",
        onDismiss = onDismiss,
        confirm = {
            ClayButton(
                text = "Download & Install",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    ctx.startActivity(intent)
                    onDismiss()
                },
                color = Clay.Mint,
                icon = Icons.Default.Download
            )
        },
        dismiss = {
            ClayButton("Later", onDismiss, color = Clay.Peach)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}
