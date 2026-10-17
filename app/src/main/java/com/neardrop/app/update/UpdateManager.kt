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

object UpdateChecker {
    const val CURRENT_VERSION_CODE = 2
    const val CURRENT_VERSION_NAME = "0.2.0"
    private const val UPDATE_URL = "https://raw.githubusercontent.com/Kmrr-Gautam/NearDrop/main/version.json"

    val latestWhatsNew = listOf(
        "🚀 Fluid file flying animations with dynamic 3D banking and glowing particle trails",
        "✨ Clean file spotlight cards displaying proper file names, format badges, and exact sizes",
        "🧹 Removed all cluttered and duplicate cards for a modern, distraction-free transfer UI",
        "📲 Instant automatic transition to progress screen upon receiving or accepting files",
        "⚡ Real-time speed chips, remaining time estimation, and senior-friendly one-tap actions"
    )

    suspend fun check(): Result<AppUpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(UPDATE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "NearDrop-Android/$CURRENT_VERSION_NAME")
            }
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val code = json.optInt("versionCode", 1)
                val name = json.optString("versionName", "0.2.0")
                val url = json.optString("releaseUrl", "https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.0.apk")
                val whatsNewArr = json.optJSONArray("whatsNew")
                val notes = mutableListOf<String>()
                if (whatsNewArr != null) {
                    for (i in 0 until whatsNewArr.length()) {
                        notes.add(whatsNewArr.getString(i))
                    }
                }
                val info = AppUpdateInfo(code, name, url, notes.ifEmpty { latestWhatsNew })
                if (code > CURRENT_VERSION_CODE) info else null
            } else {
                null
            }
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
