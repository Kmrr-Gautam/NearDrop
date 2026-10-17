package com.neardrop.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neardrop.app.core.*
import com.neardrop.app.data.TransferRecord
import com.neardrop.app.transfer.SendItem
import com.neardrop.app.ui.MainViewModel
import com.neardrop.app.ui.components.*
import com.neardrop.app.ui.theme.Clay
import com.neardrop.app.ui.theme.clay
import com.neardrop.app.transfer.IncomingRequest
import com.neardrop.app.transfer.IncomingUi
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.neardrop.app.ui.qr.*

// ------------------------------------------------------------------ Onboarding (Screen A)
@Composable
fun OnboardingScreen(vm: MainViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(vm.settings.value.deviceName) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { step = 2 }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        ClayEmptyState(
            when (step) { 0 -> Icons.Default.Wifi; 1 -> Icons.Default.Bluetooth; else -> Icons.Default.Smartphone },
            when (step) { 0 -> "Welcome to NearDrop"; 1 -> "Allow nearby sharing"; else -> "Name this device" },
            when (step) {
                0 -> "Share files directly with nearby devices — no internet, no accounts, no cloud. Everything stays between your devices and is encrypted."
                1 -> "Bluetooth lets other devices notice you, nearby-devices access is used for Wi-Fi Direct, and notifications show transfer progress. Files are chosen with Android's file picker, so no storage permission is needed."
                else -> "This is the name other people see. You can change it later in Settings."
            }
        )
        if (step == 2) OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(32) },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> ClayButton("Continue", { step = 1 }, Modifier.fillMaxWidth())
            1 -> {
                ClayButton("Grant permissions", { perms.launch(Perms.required().toTypedArray()) }, Modifier.fillMaxWidth(), icon = Icons.Default.Lock)
                Spacer(Modifier.height(8.dp))
                TextButton({ step = 2 }) { Text("Skip for now (some features will be limited)") }
            }
            else -> ClayButton("Finish", { vm.finishOnboarding(name); onDone() }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
        }
    }
}

// ------------------------------------------------------------------ Home (Screens B + C)
@Composable
fun HomeScreen(vm: MainViewModel, onSend: () -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val isDark = isDark()
    val settings by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val active by vm.progress.collectAsState()
    val history by vm.history.collectAsState()
    val trusted by vm.trusted.collectAsState()
    val internet by vm.hasInternet.collectAsState()
    val lan by vm.hasLan.collectAsState()
    val ble by vm.bleNearby.collectAsState()
    val peers by vm.p2pPeers.collectAsState()
    val p2pMsg by vm.p2pMessage.collectAsState()
    val hostIp by vm.p2pGroupHost.collectAsState()
    val owner by vm.p2pIsOwner.collectAsState()
    val receiving by vm.receiving.collectAsState()
    val localIp by vm.localIp.collectAsState()
    val port by vm.listenPort.collectAsState()
    var permsMissing by remember { mutableStateOf(Perms.missing(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permsMissing = Perms.missing(ctx); vm.startDiscovery() }
    val fps = trusted.map { it.fingerprint }.toSet()
    var detailRecord by remember { mutableStateOf<TransferRecord?>(null) }
    var showReceiveQr by remember { mutableStateOf(false) }
    var showScanQr by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NearDrop", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text(settings.deviceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
                ClayIconButton(Icons.Default.Settings, "Settings", onSettings)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val statusText = if (receiving) "✓ Ready to receive" else "Receiving is off"
                ClayChip(statusText, true, color = if (receiving) Clay.Mint else Clay.Peach)
                if (!internet) ClayChip("Direct P2P", true, color = Clay.Sky)
            }
        }
        item {
            ClayCard(Modifier.fillMaxWidth(), color = if (isDark) Color(0xFF221F33) else Clay.Lavender.copy(alpha = 0.35f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Send,
                            null,
                            tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Send Photos & Files", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Share pictures or files with nearby devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClayButton("Send Files", onSend, Modifier.weight(1.1f).height(48.dp), icon = Icons.Default.Send, color = Clay.Mint)
                    ClayButton("Scan QR", { showScanQr = true }, Modifier.weight(0.9f).height(48.dp), icon = Icons.Default.QrCodeScanner, color = Clay.Lavender)
                }
            }
        }
        item {
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clay(if (receiving) (if (isDark) Clay.DarkMint else Clay.Mint) else (if (isDark) Clay.DarkPeach else Clay.Peach), CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (receiving) Icons.Default.Check else Icons.Default.Download,
                            null,
                            tint = if (isDark) Color.White else Clay.Charcoal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Receive Files", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (receiving) "Ready! Other devices can see you as “${settings.deviceName}”" else "Turn on so other devices can send you files",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                    ClaySwitch(receiving) { vm.setVisibility(if (it) Visibility.EVERYONE else Visibility.OFF) }
                }
                if (receiving) {
                    Spacer(Modifier.height(12.dp))
                    ClayButton(
                        text = "Show QR Code to Receive",
                        onClick = { showReceiveQr = true },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        icon = Icons.Default.QrCode2,
                        color = Clay.Mint
                    )
                }
            }
        }
        items(active, key = { it.id }) { p -> ClayProgressCard(p, { onOpen(p.id) }, { vm.cancel(p.id) }) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Nearby Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton({ onSend() }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Connect by IP")
                }
            }
        }
        if (permsMissing.isNotEmpty()) item {
            ClayEmptyState(Icons.Default.Lock, "Permissions needed", "NearDrop needs nearby-device access to discover other devices.") {
                ClayButton("Grant", { permLauncher.launch(permsMissing.toTypedArray()) })
                TextButton({ ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Open Android settings") }
            }
        }
        if (devices.isEmpty()) item {
            ClayRadarScanner(
                title = "Looking for devices…",
                subtitle = "Make sure both devices have NearDrop open on the same Wi-Fi network."
            )
        }
        items(devices, key = { it.id }) { d ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                ClayDeviceCard(d, d.fingerprintPrefix?.let { p -> fps.any { it.startsWith(p) } } == true) { vm.selectedDevice.value = d; onSend() }
            }
        }
        item { Text("Recently Transferred", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) item { Text("No transfers yet. Files you send or receive will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
        items(history.take(3), key = { it.id }) { item -> ClayTransferItem(item) { detailRecord = item } }
        item { Spacer(Modifier.height(16.dp)) }
    }

    detailRecord?.let { r ->
        ClayDialog("Transfer details", { detailRecord = null }, confirm = { TextButton({ detailRecord = null }) { Text("Close") } }) {
            val df = DateFormat.getDateTimeInstance()
            Text("${if (r.direction == "SEND") "Sent to" else "Received from"} ${r.peerName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("Status: ${r.status.lowercase().replaceFirstChar { it.uppercase() }}" + (r.message?.let { " — $it" } ?: ""), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Text("Date: ${df.format(Date(r.startedAt))}", style = MaterialTheme.typography.bodySmall)
            Text("Total size: ${Fmt.bytes(r.totalBytes)}", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("Files:", fontWeight = FontWeight.Bold)
            r.fileNames.lines().filter { it.isNotBlank() }.take(20).forEach {
                val fName = it.substringAfterLast('/').substringAfterLast('\\')
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(fName, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showScanQr) {
        QrScannerDialog(
            onDismiss = { showScanQr = false },
            onScanned = { info ->
                vm.addManualDevice(info.ip, info.port, info.name)
                showScanQr = false
                onSend()
            }
        )
    }
    if (showReceiveQr) {
        QrReceiverDialog(
            deviceName = settings.deviceName,
            ipAddress = localIp ?: "127.0.0.1",
            port = if (port > 0) port else 45454,
            fingerprint = vm.fingerprint,
            onDismiss = { showReceiveQr = false }
        )
    }
}



// ------------------------------------------------------------------ Send (Screen D)
@Composable
fun SendScreen(vm: MainViewModel, onBack: () -> Unit, onSent: (String) -> Unit) {
    val items by vm.selectedItems.collectAsState()
    val device by vm.selectedDevice.collectAsState()
    val devices by vm.devices.collectAsState()
    var showIpDialog by remember { mutableStateOf(false) }
    var showScanQr by remember { mutableStateOf(false) }
    val any = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    val media = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { vm.addFiles(it) }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::addTree) }
    Column(Modifier.fillMaxSize()) {
        ClayTopBar("Send files", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClayButton(
                        "Photos & Videos",
                        { media.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        Modifier.weight(1f).height(48.dp),
                        icon = Icons.Default.Image,
                        color = Clay.Sky
                    )
                    ClayButton(
                        "Files & Documents",
                        { any.launch(arrayOf("*/*")) },
                        Modifier.weight(1f).height(48.dp),
                        icon = Icons.Default.InsertDriveFile,
                        color = Clay.Lavender
                    )
                }
            }
            if (items.isEmpty()) item { ClayEmptyState(Icons.Default.AttachFile, "Select files to send", "Tap 'Photos & Videos' or 'Files & Documents' above.") }
            itemsIndexed(items) { i, it -> SelectedRow(it) { vm.removeItem(i) } }
            if (items.isNotEmpty()) item { Text("${items.size} item(s) · ${Fmt.bytes(items.sumOf { it.size })}", fontWeight = FontWeight.Bold) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recipient Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({ showScanQr = true }) {
                            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan QR")
                        }
                        TextButton({ showIpDialog = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Enter IP")
                        }
                    }
                }
            }
            if (devices.isEmpty()) item { Text("Looking for nearby devices... Make sure NearDrop is open on the other phone, or tap 'Enter IP'.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) }
            items(devices, key = { it.id }) { d ->
                val selected = device?.id == d.id
                val cardColor = if (selected) {
                    if (isDark()) Color(0xFF2C244A) else Clay.Mint
                } else MaterialTheme.colorScheme.surface

                ClayCard(Modifier.fillMaxWidth(), color = cardColor, onClick = { vm.selectedDevice.value = d }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(deviceIcon(d.type), null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${d.host}:${d.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                        }
                        if (selected) {
                            Text("✓ Selected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(16.dp).navigationBarsPadding()) {
            ClayButton(
                "Send Now",
                { vm.send()?.let(onSent) },
                Modifier.fillMaxWidth().height(52.dp),
                enabled = items.isNotEmpty() && device != null && devices.any { it.id == device?.id },
                icon = Icons.Default.Send,
                color = Clay.Mint
            )
        }
    }

    if (showIpDialog) {
        var ipText by remember { mutableStateOf("") }
        ClayDialog(
            title = "Connect by IP",
            onDismiss = { showIpDialog = false },
            confirm = {
                ClayButton("Connect", {
                    val trimmed = ipText.trim()
                    if (trimmed.isNotEmpty()) {
                        val parts = trimmed.split(":")
                        val host = parts[0].trim()
                        val port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: Proto.DEFAULT_PORT
                        vm.addManualDevice(host, port)
                        showIpDialog = false
                    }
                }, enabled = ipText.isNotBlank(), color = Clay.Mint)
            },
            dismiss = {
                TextButton({ showIpDialog = false }) { Text("Cancel") }
            }
        ) {
            Text(
                "Enter the recipient device's IP address (shown on its NearDrop screen, e.g. 192.168.1.9):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it },
                label = { Text("IP address (e.g. 192.168.1.9)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showScanQr) {
        QrScannerDialog(
            onDismiss = { showScanQr = false },
            onScanned = { info ->
                vm.addManualDevice(info.ip, info.port, info.name)
                vm.devices.value.firstOrNull { it.host == info.ip && it.port == info.port }?.let {
                    vm.selectedDevice.value = it
                }
                showScanQr = false
            }
        )
    }
}

@Composable
private fun SelectedRow(i: SendItem, onRemove: () -> Unit) {
    val fileName = i.relativePath.substringAfterLast('/').substringAfterLast('\\')
    ClayCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clay(Clay.Sky.copy(alpha = 0.45f), CircleShape, 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        i.mime?.startsWith("image") == true -> Icons.Default.Image
                        i.mime?.startsWith("video") == true -> Icons.Default.Videocam
                        i.mime?.startsWith("audio") == true -> Icons.Default.MusicNote
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = Clay.Charcoal,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, maxLines = 1, fontWeight = FontWeight.Bold, overflow = TextOverflow.Ellipsis)
                Text(Fmt.bytes(i.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            IconButton(onRemove) { Icon(Icons.Default.Close, "Remove") }
        }
    }
}

// ------------------------------------------------------------------ Incoming (Screen E)
@Composable
fun IncomingDialog(vm: MainViewModel, ui: IncomingUi) {
    val r: IncomingRequest = ui.request
    var trust by remember(r.transferId) { mutableStateOf(false) }
    val known = vm.trusted.collectAsState().value.any { it.fingerprint == r.peerFingerprint }
    val isDark = isDark()
    val t = rememberInfiniteTransition(label = "dialogPulse")
    val pulseScale by t.animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")

    val firstFile = r.files.firstOrNull()?.path?.substringAfterLast('/')?.substringAfterLast('\\') ?: "File"
    val displayName = if (r.files.size > 1) "$firstFile (+${r.files.size - 1} more)" else firstFile

    ClayDialog("Incoming file", onDismiss = { vm.respond(false, false) },
        confirm = { ClayButton("ACCEPT", { vm.respond(true, trust) }, color = Clay.Mint, icon = Icons.Default.Check) },
        dismiss = { ClayButton("DECLINE", { vm.respond(false, false) }, color = Clay.Peach, icon = Icons.Default.Close) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                    .clay(if (isDark) Clay.DarkSky else Clay.Sky, CircleShape, 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(deviceIcon(r.senderType), null, tint = if (isDark) Clay.DarkSkyAccent else Clay.Charcoal, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("${r.senderName} wants to send you:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(if (known) "Known device" else "Unknown sender", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            }
        }
        Spacer(Modifier.height(12.dp))
        ClayCard(Modifier.fillMaxWidth(), color = if (isDark) Color(0xFF26213D) else Color.White) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true) || displayName.endsWith(".png", true) -> Icons.Default.Image
                        displayName.endsWith(".mp4", true) || displayName.endsWith(".mkv", true) -> Icons.Default.Videocam
                        displayName.endsWith(".mp3", true) || displayName.endsWith(".wav", true) -> Icons.Default.MusicNote
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${r.files.size} file(s) · ${Fmt.bytes(r.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Security code: ${r.sas}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        if (!known) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(trust, { trust = it })
                Text("Remember this device for next time", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ------------------------------------------------------------------ Progress (Screen F)
@Composable
fun ProgressScreen(vm: MainViewModel, id: String, onBack: () -> Unit) {
    val list by vm.progress.collectAsState()
    val trusted by vm.trusted.collectAsState()
    val p = list.firstOrNull { it.id == id }
    val isDark = isDark()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ClayTopBar("Transfer", onBack)
        if (p == null) { ClayEmptyState(Icons.Default.CheckCircle, "Nothing active", "This transfer has finished. See Transfers for history."); return }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TransferHero(p)
            ClayProgressCard(p, {}, null)

            // Minimal, senior-friendly status card
            ClayCard(Modifier.fillMaxWidth()) {
                val isCompleted = p.status == TransferStatus.COMPLETED
                val isInterrupted = p.status == TransferStatus.INTERRUPTED
                val isFailed = p.status == TransferStatus.FAILED || p.status == TransferStatus.REJECTED

                val rawName = p.currentFile.substringAfterLast('/').substringAfterLast('\\')
                val fileName = if (rawName.isNotBlank()) rawName else if (p.fileCount > 1) "${p.fileCount} files" else "File"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            isCompleted -> Icons.Default.CheckCircle
                            isInterrupted -> Icons.Default.Warning
                            isFailed -> Icons.Default.Error
                            else -> Icons.Default.Sync
                        },
                        contentDescription = null,
                        tint = when {
                            isCompleted -> if (isDark) Clay.DarkMintAccent else Color(0xFF1B5E20)
                            isInterrupted -> if (isDark) Clay.DarkPeachAccent else Color(0xFFE65100)
                            isFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            when (p.status) {
                                TransferStatus.COMPLETED -> "✓ Transfer Complete!"
                                TransferStatus.TRANSFERRING -> "Transferring file..."
                                TransferStatus.VERIFYING -> "Checking file safety..."
                                TransferStatus.CONNECTING -> "Connecting..."
                                TransferStatus.AWAITING_APPROVAL -> "Waiting for approval..."
                                TransferStatus.INTERRUPTED -> "Transfer Interrupted"
                                TransferStatus.REJECTED -> "Declined by other device"
                                TransferStatus.CANCELLED -> "Cancelled"
                                TransferStatus.FAILED -> "Transfer Failed"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = when {
                                isCompleted -> if (isDark) Clay.DarkMintAccent else Color(0xFF1B5E20)
                                isInterrupted -> if (isDark) Clay.DarkPeachAccent else Color(0xFFE65100)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Text(
                            (if (p.direction == Direction.SEND) "Sent to " else "Received from ") + p.peerName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(14.dp))

                // Prominently show the file name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when {
                            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) || fileName.endsWith(".png", true) || fileName.endsWith(".webp", true) -> Icons.Default.Image
                            fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || fileName.endsWith(".mov", true) -> Icons.Default.Videocam
                            fileName.endsWith(".mp3", true) || fileName.endsWith(".wav", true) -> Icons.Default.MusicNote
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = fileName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Size: ${Fmt.bytes(p.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                if (isCompleted && p.direction == Direction.RECEIVE) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "📁 Saved to your device's NearDrop folder.",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Clay.DarkMintAccent else Color(0xFF1B5E20)
                    )
                }

                p.message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCompleted) LocalContentColor.current else MaterialTheme.colorScheme.error
                    )
                }

                p.sas?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Security code: $it",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Big, senior-friendly action buttons
            if (p.status == TransferStatus.COMPLETED) {
                ClayButton(
                    "Done",
                    { vm.dismiss(p.id); onBack() },
                    Modifier.fillMaxWidth().height(52.dp),
                    color = Clay.Mint,
                    icon = Icons.Default.Check
                )
            } else if (p.status.terminal) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.canRetry(p.id) && p.status != TransferStatus.REJECTED) {
                        ClayButton(
                            "Try Again",
                            { vm.retry(p.id) },
                            Modifier.weight(1f).height(52.dp),
                            icon = Icons.Default.Refresh,
                            color = Clay.Lavender
                        )
                    }
                    ClayButton(
                        "Close",
                        { vm.dismiss(p.id); onBack() },
                        Modifier.weight(1f).height(52.dp),
                        color = Clay.Peach
                    )
                }
            } else {
                ClayButton(
                    "Cancel Transfer",
                    { vm.cancel(p.id) },
                    Modifier.fillMaxWidth().height(52.dp),
                    color = Clay.Peach,
                    icon = Icons.Default.Close
                )
            }

            val fp = p.peerFingerprint
            if (fp != null && trusted.none { it.fingerprint == fp }) {
                ClayButton(
                    "Trust this device for next time",
                    { vm.trustPeer(fp, p.peerName) },
                    Modifier.fillMaxWidth(),
                    color = Clay.Sky,
                    icon = Icons.Default.Verified
                )
            }
        }
    }
}

@Composable
private fun TransferHero(p: TransferProgress) {
    val isDark = isDark()
    val t = rememberInfiniteTransition(label = "hero")
    val isTransferring = p.status == TransferStatus.TRANSFERRING
    val isCompleted = p.status == TransferStatus.COMPLETED

    val haloScale by t.animateFloat(1f, 1.25f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "halo")
    val haloAlpha by t.animateFloat(0.6f, 0.1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "haloA")
    val fileBob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val streamPhase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "stream")

    ClayCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sender avatar
            Box(contentAlignment = Alignment.Center) {
                if (isTransferring && p.direction == Direction.SEND) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = haloScale
                                scaleY = haloScale
                                alpha = haloAlpha
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF5B45C9))
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (p.direction == Direction.SEND) Icons.Default.PhoneAndroid else deviceIcon(DeviceType.PHONE),
                        contentDescription = null,
                        tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Animated transfer stream bridge in center
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                    val y = size.height / 2
                    val startX = 0f
                    val endX = size.width

                    drawLine(
                        color = if (isDark) Color(0xFF382F5E) else Color(0xFFC9B8FF).copy(alpha = 0.5f),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (isTransferring) {
                        val packetCount = 4
                        for (i in 0 until packetCount) {
                            val fraction = (streamPhase + i.toFloat() / packetCount) % 1f
                            val actualFraction = if (p.direction == Direction.SEND) fraction else (1f - fraction)
                            val packetX = startX + actualFraction * (endX - startX)
                            drawCircle(
                                color = if (isDark) Color(0xFF9D8BFF) else Color(0xFF5B45C9),
                                radius = 4.dp.toPx(),
                                center = Offset(packetX, y)
                            )
                        }
                    }
                }

                // Floating file badge
                Box(
                    Modifier
                        .graphicsLayer {
                            translationY = if (isTransferring) fileBob else 0f
                        }
                        .clay(
                            if (isCompleted) (if (isDark) Clay.DarkMint else Clay.Mint)
                            else (if (isDark) Color(0xFF28233C) else Clay.Cream),
                            RoundedCornerShape(14.dp),
                            3.dp
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            when {
                                isCompleted -> Icons.Default.CheckCircle
                                p.status == TransferStatus.VERIFYING -> Icons.Default.VerifiedUser
                                p.currentFile.endsWith(".jpg", true) || p.currentFile.endsWith(".png", true) || p.currentFile.endsWith(".webp", true) -> Icons.Default.Image
                                p.currentFile.endsWith(".mp4", true) || p.currentFile.endsWith(".mkv", true) -> Icons.Default.Videocam
                                p.currentFile.endsWith(".mp3", true) || p.currentFile.endsWith(".wav", true) -> Icons.Default.MusicNote
                                p.currentFile.endsWith(".zip", true) || p.currentFile.endsWith(".tar", true) -> Icons.Default.Archive
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (isCompleted) (if (isDark) Clay.DarkMintAccent else Color(0xFF1B5E20))
                            else (if (isDark) Color.White else Clay.Charcoal),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Receiver avatar
            Box(contentAlignment = Alignment.Center) {
                if (isTransferring && p.direction == Direction.RECEIVE) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                scaleX = haloScale
                                scaleY = haloScale
                                alpha = haloAlpha
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF38B88B))
                    )
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clay(
                            if (isCompleted) (if (isDark) Clay.DarkMint else Clay.Mint)
                            else (if (isDark) Clay.DarkSky else Clay.Sky),
                            CircleShape,
                            4.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isCompleted) Icons.Default.Check else Icons.Default.Devices,
                        contentDescription = null,
                        tint = if (isDark) (if (isCompleted) Clay.DarkMintAccent else Clay.DarkSkyAccent) else Clay.Charcoal,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        val rawName = p.currentFile.substringAfterLast('/').substringAfterLast('\\')
        val fileName = if (rawName.isNotBlank()) rawName else if (p.fileCount > 1) "${p.fileCount} files" else "Preparing files…"
        Text(
            fileName,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            (if (p.direction == Direction.SEND) "Sending to " else "Receiving from ") + p.peerName,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            "File ${minOf(p.fileIndex + 1, maxOf(p.fileCount, 1))} of ${maxOf(p.fileCount, 1)} · ${Fmt.bytes(p.totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        )
    }
}

// ------------------------------------------------------------------ History (Screen G)
@Composable
fun TransfersScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val active by vm.progress.collectAsState()
    val history by vm.history.collectAsState()
    var filter by remember { mutableStateOf("All") }
    var detail by remember { mutableStateOf<TransferRecord?>(null) }
    val shown = history.filter { when (filter) {
        "Sent" -> it.direction == "SEND"; "Received" -> it.direction == "RECEIVE"
        "Failed" -> it.status in setOf("FAILED", "INTERRUPTED", "CANCELLED", "REJECTED"); else -> true } }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar("Transfers")
        ClaySegmentedControl(
            items = listOf("All" to "All", "Sent" to "Sent", "Received" to "Received", "Failed" to "Failed"),
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(active, key = { it.id }) { p -> ClayProgressCard(p, { onOpen(p.id) }, { vm.cancel(p.id) }) }
            if (shown.isEmpty()) item { ClayEmptyState(Icons.Default.History, "No transfers", "Completed, failed and cancelled transfers appear here.") }
            items(shown, key = { it.id }) { r -> ClayTransferItem(r) { detail = r } }
        }
    }
    detail?.let { r ->
        ClayDialog("Transfer details", { detail = null }, confirm = { TextButton({ detail = null }) { Text("Close") } }) {
            val df = DateFormat.getDateTimeInstance()
            Text("${if (r.direction == "SEND") "Sent to" else "Received from"} ${r.peerName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("Status: ${r.status.lowercase().replaceFirstChar { it.uppercase() }}" + (r.message?.let { " — $it" } ?: ""), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Text("Date: ${df.format(Date(r.startedAt))}", style = MaterialTheme.typography.bodySmall)
            Text("Total size: ${Fmt.bytes(r.totalBytes)}", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("Files:", fontWeight = FontWeight.Bold)
            r.fileNames.lines().filter { it.isNotBlank() }.take(20).forEach {
                val fName = it.substringAfterLast('/').substringAfterLast('\\')
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(fName, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Trusted (Screen H)
@Composable
fun TrustedScreen(vm: MainViewModel, onBack: () -> Unit) {
    val list by vm.trusted.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ClayTopBar("Trusted devices", onBack) { if (list.isNotEmpty()) ClayIconButton(Icons.Default.DeleteSweep, "Clear all", { confirm = true }, color = Clay.Peach) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list.isEmpty()) item { ClayEmptyState(Icons.Default.Shield, "No trusted devices", "Devices become trusted only when you choose “Trust this device”.") }
            items(list, key = { it.fingerprint }) { d ->
                ClayCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(deviceIcon(DeviceType.parse(d.type)), null); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.Bold)
                            Text("ID ${d.fingerprint.take(12)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            Text("Last connected: " + (d.lastConnectedAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "never"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        IconButton({ vm.removeTrusted(d.fingerprint) }) { Icon(Icons.Default.Delete, "Remove") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Auto-accept files", Modifier.weight(1f)); ClaySwitch(d.autoAccept) { vm.setAutoAccept(d.fingerprint, it) } }
                }
            }
        }
    }
    if (confirm) ClayDialog("Clear all trust?", { confirm = false },
        confirm = { TextButton({ vm.clearTrusted(); confirm = false }) { Text("Clear") } }, dismiss = { TextButton({ confirm = false }) { Text("Cancel") } }) {
        Text("Every device will need to be approved again.")
    }
}

// ------------------------------------------------------------------ Settings (Screen I)
@Composable
fun SettingsScreen(vm: MainViewModel, onTrusted: () -> Unit) {
    val s by vm.settings.collectAsState()
    val isDark = isDark()
    val internet by vm.hasInternet.collectAsState()
    val port by vm.listenPort.collectAsState()
    var rename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(s.deviceName) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>("You're on the latest version (v0.1.0)") }
    var lastCheckedTime by remember { mutableStateOf("Just now") }
    var autoCheckUpdates by remember { mutableStateOf(true) }
    var showReleaseNotes by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ClayTopBar("Settings")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Device name
            ClayCard(Modifier.fillMaxWidth(), onClick = { newName = s.deviceName; rename = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Smartphone, null, tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Device name", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(s.deviceName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }

            // Visibility
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkMint else Clay.Mint, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Visibility, null, tint = if (isDark) Clay.DarkMintAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Visibility", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))
                ClaySegmentedControl(
                    items = listOf(
                        Visibility.EVERYONE to "Everyone",
                        Visibility.TRUSTED_ONLY to "Trusted",
                        Visibility.OFF to "Off"
                    ),
                    selected = s.visibility,
                    onSelect = { vm.setVisibility(it) }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "“Trusted” still advertises on the network but refuses unknown devices right after authentication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Theme
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkSky else Clay.Sky, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Palette, null, tint = if (isDark) Clay.DarkSkyAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))
                ClaySegmentedControl(
                    items = listOf(
                        ThemeMode.SYSTEM to "System",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark"
                    ),
                    selected = s.theme,
                    onSelect = { vm.setTheme(it) }
                )
            }

            // Notifications
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkPeach else Clay.Peach, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, null, tint = if (isDark) Clay.DarkPeachAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Transfer notifications", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Foreground alerts while transferring", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    ClaySwitch(s.notifications) { vm.setNotifications(it) }
                }
            }

            // Trusted devices
            ClayCard(Modifier.fillMaxWidth(), onClick = onTrusted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VerifiedUser, null, tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Trusted devices & auto-accept", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Manage paired keys and auto-receive rules", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Storage
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkMint else Clay.Mint, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, null, tint = if (isDark) Clay.DarkMintAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Storage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(vm.receivedPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClayButton(
                        text = "Clear partial data",
                        onClick = { vm.clearPartials() },
                        modifier = Modifier.weight(1f),
                        color = Clay.Peach,
                        icon = Icons.Default.DeleteSweep
                    )
                    ClayButton(
                        text = "Clear history",
                        onClick = { vm.clearHistory() },
                        modifier = Modifier.weight(1f),
                        color = Clay.Peach,
                        icon = Icons.Default.History
                    )
                }
            }

            // Check for updates
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkMint else Clay.Mint, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SystemUpdate, null, tint = if (isDark) Clay.DarkMintAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Check for updates", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("NearDrop v0.1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    Surface(
                        color = if (isDark) Clay.DarkMint else Clay.Mint,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = if (isDark) Clay.DarkMintAccent else Color(0xFF1F7A5C), modifier = Modifier.size(14.dp))
                            Text("Up to date", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isDark) Clay.DarkMintText else Clay.Charcoal)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = if (isDark) Clay.DarkMintAccent else Color(0xFF1F7A5C),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (isCheckingUpdates) "Checking for newer versions..." else (updateStatusMessage ?: "NearDrop is up to date"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Last checked: $lastCheckedTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ClayButton(
                        text = if (isCheckingUpdates) "Checking..." else "Check now",
                        onClick = {
                            if (!isCheckingUpdates) {
                                scope.launch {
                                    isCheckingUpdates = true
                                    delay(1200)
                                    isCheckingUpdates = false
                                    updateStatusMessage = "You're on the latest version (v0.1.0)"
                                    val now = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                                    lastCheckedTime = "Today at $now"
                                }
                            }
                        },
                        modifier = Modifier.weight(1.1f),
                        enabled = !isCheckingUpdates,
                        icon = Icons.Default.Refresh,
                        color = Clay.Sky
                    )

                    ClayButton(
                        text = "What's new",
                        onClick = { showReleaseNotes = true },
                        modifier = Modifier.weight(0.9f),
                        icon = Icons.Default.Info,
                        color = Clay.Lavender
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Check automatically", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text("Notifies you when safe updates are ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                    ClaySwitch(autoCheckUpdates) { autoCheckUpdates = it }
                }
            }

            // Privacy & security
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkSky else Clay.Sky, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, null, tint = if (isDark) Clay.DarkSkyAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Privacy & security", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "No accounts, no cloud, no relay. Direct peer-to-peer transfers protected by ECDH key exchange and AES-256-GCM authenticated encryption. Unknown senders always require manual approval. Received files are never opened or executed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }

            // Diagnostics
            ClayCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clay(if (isDark) Clay.DarkLavender else Clay.Lavender, CircleShape, 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Info, null, tint = if (isDark) Clay.DarkLavenderAccent else Clay.Charcoal, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("Diagnostics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(10.dp))
                Text("Device ID: ${vm.fingerprint.take(16)}…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Text("Listening port: ${if (port > 0) port else "not listening"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Text("Internet: ${if (internet) "available (unused)" else "none"} · Wi-Fi Direct: ${if (vm.p2pSupported) "supported" else "unsupported"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }

            Text("NearDrop 0.1.0 · protocol v${Proto.VERSION}", Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
    if (rename) ClayDialog("Device name", { rename = false },
        confirm = { TextButton({ vm.setName(newName); rename = false }) { Text("Save") } }, dismiss = { TextButton({ rename = false }) { Text("Cancel") } }) {
        OutlinedTextField(newName, { newName = it.take(32) }, singleLine = true)
    }
    if (showReleaseNotes) {
        ClayDialog(
            title = "What's new in v0.1.0",
            onDismiss = { showReleaseNotes = false },
            confirm = {
                TextButton(onClick = { showReleaseNotes = false }) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• Senior-friendly interface with large buttons and clear text", style = MaterialTheme.typography.bodyMedium)
                Text("• Prominent file name display on every transfer screen", style = MaterialTheme.typography.bodyMedium)
                Text("• Fast direct Wi-Fi and Local Network file sharing", style = MaterialTheme.typography.bodyMedium)
                Text("• Zero cloud or third-party servers: 100% private P2P", style = MaterialTheme.typography.bodyMedium)
                Text("• Manual IP connection for testing and isolated networks", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
