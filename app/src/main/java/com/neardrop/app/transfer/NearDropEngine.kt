package com.neardrop.app.transfer

import android.content.Context
import android.os.Build
import com.neardrop.app.core.*
import com.neardrop.app.data.*
import com.neardrop.app.discovery.*
import com.neardrop.app.security.KeystoreIdentity
import com.neardrop.app.service.TransferNotifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class IncomingUi(val request: IncomingRequest, val decision: CompletableDeferred<Pair<Boolean, Boolean>>)

private class SendRequest(val device: DiscoveredDevice, val items: List<SendItem>, val transferId: String)

/** Owns discovery, the listening socket, running transfers, trust and history. Lives in the Application. */
class NearDropEngine(private val app: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val identity = KeystoreIdentity()
    val db = AppDatabase.get(app)
    val settings = SettingsStore(app)
    val config = TransferConfig()

    val nsd = NsdDiscovery(app, scope)
    val ble = BleBeacon(app, scope)
    val wifiDirect = WifiDirectManager(app)
    val network = NetworkMonitor(app)

    val progress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val incoming = MutableStateFlow<IncomingUi?>(null)
    val receiving = MutableStateFlow(false)
    val listenPort = MutableStateFlow(0)
    val manualDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val localIp get() = network.localIp
    val devices: StateFlow<List<DiscoveredDevice>> = combine(nsd.devices, wifiDirect.groupHost, manualDevices) { lan, p2p, manual ->
        val list = ArrayList<DiscoveredDevice>(lan.size + (if (p2p != null) 1 else 0) + manual.size)
        list.addAll(lan)
        if (p2p != null) list.add(DiscoveredDevice("p2p-$p2p", "Wi-Fi Direct peer", DeviceType.UNKNOWN, p2p, Proto.DEFAULT_PORT, Via.WIFI_DIRECT, null))
        for (m in manual) {
            if (list.none { it.host == m.host }) list.add(m)
        }
        list
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val jobs = ConcurrentHashMap<String, Job>()
    private val sockets = ConcurrentHashMap<String, Socket>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val lastSend = ConcurrentHashMap<String, SendRequest>()
    private val incomingMutex = Mutex()
    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    @Volatile private var current = Settings()

    val partialRoot = File(app.getExternalFilesDir(null) ?: app.filesDir, "partial")
    val receivedDir: File get() = NearDropFileManager.getNearDropFolder(app)

    init {
        scope.launch { settings.flow.collect { current = it } }
        scope.launch { cleanStalePartials() }
    }

    private fun local() = LocalDeviceInfo(current.deviceName, deviceType())
    private fun deviceType(): DeviceType =
        if (app.resources.configuration.smallestScreenWidthDp >= 600) DeviceType.TABLET else DeviceType.PHONE

    // ---------------------------------------------------------------- discovery
    fun startDiscovery() {
        nsd.start(identity.fingerprint)
        ble.start(advertise = receiving.value)
        wifiDirect.start()
    }
    fun stopDiscovery() { nsd.stop(); ble.stop(); wifiDirect.stop() }

    fun addManualDevice(host: String, port: Int = Proto.DEFAULT_PORT, name: String? = null): DiscoveredDevice {
        val trimmed = host.trim()
        val dev = DiscoveredDevice(
            id = "manual-$trimmed:$port",
            name = name?.takeIf { it.isNotBlank() } ?: "Device ($trimmed)",
            type = DeviceType.PHONE,
            host = trimmed,
            port = port,
            via = Via.LAN,
            fingerprintPrefix = null
        )
        manualDevices.update { list -> (list.filter { it.host != trimmed } + dev) }
        return dev
    }

    // ---------------------------------------------------------------- receiving
    fun setReceiving(enabled: Boolean) {
        if (enabled == receiving.value) return
        if (enabled) startServer() else stopServer()
    }

    @Synchronized private fun startServer() {
        if (server != null) return
        val ss = try { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(Proto.DEFAULT_PORT)) } }
        catch (e: IOException) { ServerSocket(0) }
        server = ss; listenPort.value = ss.localPort; receiving.value = true
        nsd.register(current.deviceName, deviceType(), identity.fingerprint, ss.localPort)
        acceptJob = scope.launch {
            while (isActive && !ss.isClosed) {
                val s = try { ss.accept() } catch (e: IOException) { break }
                launch { handleIncoming(s) }
            }
        }
    }

    @Synchronized private fun stopServer() {
        nsd.unregister()
        runCatching { server?.close() }; server = null
        acceptJob?.cancel(); acceptJob = null
        receiving.value = false; listenPort.value = 0
    }

    fun refreshAdvertisement() { server?.let { nsd.register(current.deviceName, deviceType(), identity.fingerprint, it.localPort) } }

    private suspend fun handleIncoming(sock: Socket) {
        val id = UUID.randomUUID().toString()
        val tracker = Tracker(id, Direction.RECEIVE, "Incoming…")
        sockets[id] = sock
        try {
            val receiver = TransferReceiver(identity, partialRoot, receivedDir, config)
            val saved = withContext(Dispatchers.IO) {
                receiver.handle(sock, tracker.listener,
                    allowPeer = { fp ->
                        val trusted = runBlocking { db.trusted().get(fp) != null }
                        current.visibility == Visibility.EVERYONE || (current.visibility == Visibility.TRUSTED_ONLY && trusted)
                    },
                    decide = { req -> runBlocking { decideIncoming(req) } })
            }
            val finalFiles = withContext(Dispatchers.IO) {
                saved.map { f ->
                    NearDropFileManager.saveToNearDropFolder(app, f, f.name)
                }
            }
            tracker.fileNames = finalFiles.map { it.name }
            tracker.finish(TransferStatus.COMPLETED, null)
        } catch (t: Throwable) { tracker.fail(t, id) }
        finally { sockets.remove(id); runCatching { sock.close() } }
    }

    private suspend fun decideIncoming(req: IncomingRequest): Boolean = incomingMutex.withLock {
        val trusted = db.trusted().get(req.peerFingerprint)
        if (trusted != null && trusted.autoAccept) { db.trusted().touch(req.peerFingerprint, System.currentTimeMillis()); return true }
        val ui = IncomingUi(req, CompletableDeferred())
        incoming.value = ui
        if (current.notifications) TransferNotifications.incoming(app, req.senderName, req.files.size)
        val (accepted, trust) = withTimeoutOrNull(60_000) { ui.decision.await() } ?: (false to false)
        incoming.value = null
        if (accepted && trust) db.trusted().upsert(TrustedDevice(req.peerFingerprint, req.senderName, req.senderType.name, System.currentTimeMillis(), System.currentTimeMillis(), false))
        else if (accepted && trusted != null) db.trusted().touch(req.peerFingerprint, System.currentTimeMillis())
        accepted
    }

    // ---------------------------------------------------------------- sending
    fun send(device: DiscoveredDevice, items: List<SendItem>, transferId: String = UUID.randomUUID().toString()): String {
        lastSend[transferId] = SendRequest(device, items, transferId)
        cancelled.remove(transferId)
        val tracker = Tracker(transferId, Direction.SEND, device.name)
        tracker.fileNames = items.map { it.relativePath }
        tracker.setTotals(items.size, items.sumOf { it.size })
        jobs[transferId] = scope.launch {
            val sock = Socket()
            sockets[transferId] = sock
            try {
                tracker.status(TransferStatus.CONNECTING)
                sock.connect(InetSocketAddress(device.host, device.port), 10_000)
                val sources = FileResolver.toSources(app, items)
                TransferSender(identity, local(), config).send(sock, transferId, sources, device.fingerprintPrefix, tracker.listener) { transferId in cancelled }
                tracker.finish(TransferStatus.COMPLETED, null)
            } catch (t: Throwable) { tracker.fail(t, transferId) }
            finally { sockets.remove(transferId); jobs.remove(transferId); runCatching { sock.close() } }
        }
        return transferId
    }

    fun retry(id: String): String? {
        val r = lastSend[id] ?: return null
        dismiss(id)
        return send(r.device, r.items, r.transferId) // same id => receiver resumes from verified partial data
    }

    fun canRetry(id: String) = lastSend.containsKey(id)

    fun cancel(id: String) {
        cancelled.add(id)
        // Receiver side has no flag: closing the socket aborts blocking IO.
        if (progress.value[id]?.direction == Direction.RECEIVE) runCatching { sockets[id]?.close() }
    }
    fun cancelAll() = progress.value.filter { !it.value.status.terminal }.keys.forEach { cancel(it) }
    fun dismiss(id: String) { progress.update { it - id } }
    fun hasActive() = progress.value.values.any { !it.status.terminal }

    // ---------------------------------------------------------------- trust
    fun trustPeer(fp: String, name: String, type: DeviceType = DeviceType.UNKNOWN) {
        scope.launch { db.trusted().upsert(TrustedDevice(fp, name, type.name, System.currentTimeMillis(), System.currentTimeMillis(), false)) }
    }

    private fun cleanStalePartials() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        partialRoot.listFiles()?.filter { it.isDirectory && it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    fun clearPartials() { partialRoot.deleteRecursively() }

    // ---------------------------------------------------------------- progress tracking
    private inner class Tracker(val id: String, val dir: Direction, var peerName: String) {
        var fileNames: List<String> = emptyList()
        private var fileCount = 0
        private var total = 0L
        private var sas: String? = null
        private var peerFp: String? = null
        private val started = System.currentTimeMillis()
        private val startNanos = System.nanoTime()
        private var lastNanos = startNanos
        private var lastBytes = -1L
        private var baseBytes = -1L
        private var ema = 0.0
        private var lastEmit = 0L

        fun setTotals(count: Int, bytes: Long) {
            fileCount = count
            total = bytes
            val first = fileNames.firstOrNull() ?: ""
            push(TransferStatus.CONNECTING, 0, 0, first, null)
        }

        private fun push(st: TransferStatus, done: Long, idx: Int, file: String, msg: String?) {
            val elapsed = (System.nanoTime() - startNanos) / 1e9
            val moved = if (baseBytes < 0) 0 else done - baseBytes
            val avg = if (elapsed > 0.5 && moved > 0) moved / elapsed else 0.0
            val eta = if (ema > 1 && total > done) ((total - done) / ema).toLong() else null
            val actualFile = file.ifBlank { fileNames.getOrNull(idx) ?: fileNames.firstOrNull() ?: "" }
            progress.update {
                it + (id to TransferProgress(id, dir, peerName, st, fileCount, idx, actualFile, done, total, ema, avg, eta, sas, peerFp, msg, started))
            }
        }
        private fun current() = progress.value[id]

        fun status(s: TransferStatus, m: String? = null) {
            val c = current()
            val f = c?.currentFile?.ifBlank { fileNames.firstOrNull() ?: "" } ?: fileNames.firstOrNull() ?: ""
            push(s, c?.bytesDone ?: 0, c?.fileIndex ?: 0, f, m)
        }

        val listener = object : TransferListener {
            override fun onHandshake(peerFingerprint: String, sas: String) { this@Tracker.sas = sas; peerFp = peerFingerprint; this@Tracker.status(current()?.status ?: TransferStatus.CONNECTING) }
            override fun onManifest(peerName: String, files: List<FileEntry>) {
                this@Tracker.peerName = peerName
                fileCount = files.size
                total = files.sumOf { it.size }
                fileNames = files.map { it.path }
                val first = files.firstOrNull()?.path ?: ""
                push(current()?.status ?: TransferStatus.AWAITING_APPROVAL, 0, 0, first, null)
            }
            override fun onStatus(status: TransferStatus, message: String?) { this@Tracker.status(status, message) }
            override fun onProgress(bytesDone: Long, totalBytes: Long, fileIndex: Int, fileName: String) {
                total = totalBytes
                val now = System.nanoTime()
                if (baseBytes < 0) { baseBytes = bytesDone; lastBytes = bytesDone; lastNanos = now }
                val finalByte = bytesDone >= totalBytes
                if (now - lastNanos >= 250_000_000L || finalByte) {
                    val inst = (bytesDone - lastBytes) / ((now - lastNanos) / 1e9)
                    ema = if (ema == 0.0) inst else ema * 0.7 + inst * 0.3
                    lastBytes = bytesDone; lastNanos = now
                    push(current()?.status ?: TransferStatus.TRANSFERRING, bytesDone, fileIndex, fileName, null)
                } else {
                    val c = current(); if (c != null && (c.currentFile != fileName)) push(c.status, bytesDone, fileIndex, fileName, null)
                }
            }
        }

        fun finish(s: TransferStatus, msg: String?) {
            val c = current()
            val done = if (s == TransferStatus.COMPLETED) total else (c?.bytesDone ?: 0)
            val f = c?.currentFile?.ifBlank { fileNames.firstOrNull() ?: "" } ?: fileNames.firstOrNull() ?: ""
            push(s, done, c?.fileIndex ?: 0, f, msg)
            persist(s, msg, done)
            if (s == TransferStatus.COMPLETED && peerFp != null) scope.launch { db.trusted().touch(peerFp!!, System.currentTimeMillis()) }
            if (current.notifications && s.terminal) TransferNotifications.finished(app, peerName, dir, s, msg)
        }

        fun fail(t: Throwable, id: String) {
            val (s, m) = when {
                t is TransferCancelledException || id in cancelled -> TransferStatus.CANCELLED to "Transfer was cancelled"
                t is TransferRejectedException -> TransferStatus.REJECTED to (if (t.reason.contains("Blocked")) "Blocked by receiver's visibility settings" else "Declined by the other device")
                t is ProtocolException -> TransferStatus.FAILED to (t.message ?: "Transfer error")
                t is IOException || t is CancellationException -> {
                    val raw = t.message ?: ""
                    val friendly = if (raw.contains("failed to connect", true) || raw.contains("Connection refused", true) || raw.contains("timed out", true)) {
                        "Could not reach device. Make sure both devices are on the same Wi-Fi, or connect using 'Enter IP'."
                    } else {
                        "Transfer interrupted. Tap 'Retry' to resume where you left off."
                    }
                    TransferStatus.INTERRUPTED to friendly
                }
                else -> TransferStatus.FAILED to (t.message ?: "Transfer could not be completed")
            }
            finish(s, m)
        }

        private fun persist(s: TransferStatus, msg: String?, done: Long) {
            val rec = TransferRecord(id, dir.name, peerName, peerFp, fileCount, total, done, s.name, msg, started, System.currentTimeMillis(),
                fileNames.take(20).joinToString("\n"))
            scope.launch { db.transfers().upsert(rec) }
        }
    }
}
