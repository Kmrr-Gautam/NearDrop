package com.neardrop.app.ui.wireless

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neardrop.app.core.Proto
import com.neardrop.app.ui.MainViewModel
import com.neardrop.app.ui.components.ClayButton
import com.neardrop.app.ui.components.ClayCard
import com.neardrop.app.ui.components.ClaySegmentedControl
import com.neardrop.app.ui.components.isDark
import com.neardrop.app.ui.qr.QrCodeManager
import com.neardrop.app.ui.theme.Clay
import com.neardrop.app.ui.theme.clay

@Composable
fun DirectOfflineShareDialog(
    vm: MainViewModel,
    initialTab: String = "receiver",
    onDismiss: () -> Unit,
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isDark()
    var selectedRole by remember { mutableStateOf(if (initialTab == "sender") "sender" else "receiver") }

    val hotspotRunning by vm.hotspotRunning.collectAsState()
    val hotspotSsid by vm.hotspotSsid.collectAsState()
    val hotspotPass by vm.hotspotPassphrase.collectAsState()
    val hotspotIp by vm.hotspotIp.collectAsState()
    val hotspotError by vm.hotspotError.collectAsState()
    val isConnecting by vm.isConnectingHotspot.collectAsState()
    val isConnected by vm.isConnectedHotspot.collectAsState()
    val settings by vm.settings.collectAsState()

    val qrBitmap by produceState<Bitmap?>(initialValue = null, key1 = hotspotRunning, key2 = hotspotSsid) {
        if (hotspotRunning && hotspotSsid.isNotBlank()) {
            val payload = QrCodeManager.encodeReceiverInfo(
                ip = hotspotIp.ifBlank { "192.168.43.1" },
                port = Proto.DEFAULT_PORT,
                name = settings.deviceName,
                fingerprint = vm.fingerprint,
                ssid = hotspotSsid,
                passphrase = hotspotPass,
                isHotspot = true
            )
            value = QrCodeManager.generateQrBitmap(payload, 512)
        } else {
            value = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).background(if (isDark) Clay.DarkMint else Clay.Mint, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = if (isDark) Clay.DarkMintAccent else Clay.Charcoal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Direct Offline Share",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "No shared Wi-Fi · Direct Hotspot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Role selector
                ClaySegmentedControl(
                    items = listOf(
                        "receiver" to "📥 I am Receiving",
                        "sender" to "📤 I am Sending"
                    ),
                    selected = selectedRole,
                    onSelect = { selectedRole = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                if (selectedRole == "receiver") {
                    // RECEIVER FLOW (Create Hotspot)
                    Text(
                        "Create a temporary local hotspot. The sender connects to you without any passwords to transfer files without mobile data.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(Modifier.height(12.dp))

                    if (!hotspotRunning) {
                        ClayCard(Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.WifiTethering,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Ready to start hotspot",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Click below to enable temporary hotspot on this receiver device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (hotspotError != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                hotspotError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        ClayButton(
                            text = "Start Temporary Hotspot",
                            onClick = { vm.startReceiverHotspot() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            color = Clay.Mint,
                            icon = Icons.Default.WifiTethering
                        )
                    } else {
                        // Hotspot is active! Show QR code and SSID
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .size(200.dp)
                                .padding(4.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap!!.asImageBitmap(),
                                        contentDescription = "Receiver Hotspot QR Code",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = Clay.LavenderDeep
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Network: $hotspotSsid",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "✓ Hotspot Active · No password needed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Have the sender tap 'Scan QR' in Send mode to connect automatically and send files.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )

                        Spacer(Modifier.height(12.dp))

                        ClayButton(
                            text = "Stop Hotspot",
                            onClick = { vm.stopReceiverHotspot() },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            color = Clay.Peach,
                            icon = Icons.Default.WifiOff
                        )
                    }
                } else {
                    // SENDER FLOW (Enable Wi-Fi & Connect)
                    Text(
                        "Turn on Wi-Fi on this device and connect directly to the receiver's temporary hotspot without typing any passwords.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    Spacer(Modifier.height(14.dp))

                    ClayCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Step 1: Turn on Wi-Fi",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Enable Wi-Fi so this device can discover the receiver's hotspot.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(8.dp))
                            ClayButton(
                                text = "Turn on Wi-Fi",
                                onClick = { vm.openWifiSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                color = Clay.Sky,
                                icon = Icons.Default.Wifi
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    ClayCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Step 2: Connect to Receiver",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Scan the receiver's QR code. Android will connect automatically without requiring any passwords!",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(8.dp))
                            ClayButton(
                                text = if (isConnecting) "Connecting to Hotspot…" else "Scan Receiver's QR Code",
                                onClick = {
                                    onDismiss()
                                    onOpenScanner()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = Clay.Mint,
                                icon = Icons.Default.QrCodeScanner,
                                enabled = !isConnecting
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                ClayButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    color = Clay.Lavender
                )
            }
        }
    )
}
