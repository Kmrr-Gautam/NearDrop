package com.neardrop.app.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.neardrop.app.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * mDNS/DNS-SD discovery (NsdManager). Works on any shared LAN (including routers with no internet) and inside a Wi-Fi Direct group.
 * TXT record carries only: protocol version, display name, device type and a fingerprint *prefix*; the peer is authenticated by the handshake.
 */
class NsdDiscovery(private val ctx: Context, private val scope: CoroutineScope) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices
    private val byService = ConcurrentHashMap<String, DiscoveredDevice>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
    private var resolver: Job? = null
    @Volatile private var ownFingerprint: String = ""

    fun register(name: String, type: DeviceType, fingerprint: String, port: Int) {
        unregister()
        ownFingerprint = fingerprint
        val info = NsdServiceInfo().apply {
            serviceName = "ND-" + fingerprint.take(8)
            serviceType = Proto.SERVICE_TYPE
            setPort(port)
            setAttribute("v", Proto.VERSION.toString())
            setAttribute("n", name.take(32))
            setAttribute("t", type.name)
            setAttribute("fp", fingerprint.take(16))
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        regListener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun unregister() { regListener?.let { runCatching { nsd.unregisterService(it) } }; regListener = null }

    fun start(ownFp: String) {
        if (discoveryListener != null) return
        ownFingerprint = ownFp
        resolver = scope.launch {
            for (s in queue) { resolveOne(s)?.let { d -> byService[s.serviceName] = d; publish() } }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onServiceFound(s: NsdServiceInfo) { queue.trySend(s) }
            override fun onServiceLost(s: NsdServiceInfo) { byService.remove(s.serviceName); publish() }
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { discoveryListener = null }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        discoveryListener = l
        runCatching { nsd.discoverServices(Proto.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        resolver?.cancel(); resolver = null
        byService.clear(); publish()
    }

    private fun publish() { _devices.value = byService.values.sortedBy { it.name.lowercase() } }

    @Suppress("DEPRECATION")
    private suspend fun resolveOne(s: NsdServiceInfo): DiscoveredDevice? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine { cont ->
            nsd.resolveService(s, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) { if (cont.isActive) cont.resume(null) }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    val a = i.attributes
                    fun attr(k: String) = a[k]?.toString(Charsets.UTF_8)
                    val fp = attr("fp")
                    val host = i.host?.hostAddress
                    val d = if (host == null || (fp != null && ownFingerprint.startsWith(fp, true))) null
                    else DiscoveredDevice(
                        id = i.serviceName, name = attr("n") ?: i.serviceName, type = DeviceType.parse(attr("t")),
                        host = host, port = i.port, via = Via.LAN, fingerprintPrefix = fp,
                    )
                    if (cont.isActive) cont.resume(d)
                }
            })
        }
    }
}

/** BLE presence beacon: advertises/scans a NearDrop service UUID. Carries no data and no identifiers; only reports "NearDrop devices nearby". */
class BleBeacon(private val ctx: Context, private val scope: CoroutineScope) {
    private val _nearby = MutableStateFlow(0)
    val nearby: StateFlow<Int> = _nearby
    private val seen = ConcurrentHashMap<String, Long>()
    private val uuid = ParcelUuid(UUID.fromString(Proto.BLE_SERVICE_UUID))
    private var adv: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var advCb: AdvertiseCallback? = null
    private var scanCb: ScanCallback? = null
    private var pruner: Job? = null

    val bluetoothEnabled: Boolean get() = adapter()?.isEnabled == true
    private fun adapter() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun start(advertise: Boolean) {
        val a = adapter() ?: return
        if (!a.isEnabled || !Perms.hasBle(ctx) || scanCb != null) return
        runCatching {
            if (advertise) {
                adv = a.bluetoothLeAdvertiser
                advCb = object : AdvertiseCallback() {}
                adv?.startAdvertising(
                    AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED).setConnectable(false).setTimeout(0).build(),
                    AdvertiseData.Builder().setIncludeDeviceName(false).addServiceUuid(uuid).build(), advCb)
            }
            scanner = a.bluetoothLeScanner
            scanCb = object : ScanCallback() {
                override fun onScanResult(type: Int, r: ScanResult) { seen[r.device.address] = System.currentTimeMillis(); emit() }
            }
            scanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(uuid).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), scanCb)
            pruner = scope.launch { while (isActive) { delay(5_000); emit() } }
        }
    }

    private fun emit() {
        val cutoff = System.currentTimeMillis() - 20_000
        seen.entries.removeIf { it.value < cutoff }
        _nearby.value = seen.size
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { scanCb?.let { scanner?.stopScan(it) } }
        runCatching { advCb?.let { adv?.stopAdvertising(it) } }
        scanCb = null; advCb = null; pruner?.cancel(); seen.clear(); _nearby.value = 0
    }
}

/** Wi-Fi Direct (WifiP2pManager). After a group forms, a client gets the group owner IP; the NearDrop port is then dialled directly. */
class WifiDirectManager(private val ctx: Context) {
    private val mgr = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    val supported: Boolean get() = mgr != null && ctx.packageManager.hasSystemFeature("android.hardware.wifi.direct")
    val peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    /** Group owner address, set only when this device is a *client* of a formed group. */
    val groupHost = MutableStateFlow<String?>(null)
    val isGroupOwner = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    @SuppressLint("MissingPermission")
    fun start() {
        if (!supported || !Perms.hasWifiDirect(ctx) || receiver != null) return
        channel = mgr!!.initialize(ctx, ctx.mainLooper, null)
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (!Perms.hasWifiDirect(ctx)) return
                when (i.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> mgr?.requestPeers(channel) { peers.value = it.deviceList.toList() }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> mgr?.requestConnectionInfo(channel) { info ->
                        isGroupOwner.value = info.groupFormed && info.isGroupOwner
                        groupHost.value = if (info.groupFormed && !info.isGroupOwner) info.groupOwnerAddress?.hostAddress else null
                    }
                }
            }
        }
        receiver = r
        ContextCompat.registerReceiver(ctx, r, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION); addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    @SuppressLint("MissingPermission")
    fun discover() {
        if (!supported) { message.value = "This device does not support Wi-Fi Direct"; return }
        if (!Perms.hasWifiDirect(ctx)) { message.value = "Nearby devices permission is required"; return }
        start()
        mgr?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { message.value = null }
            override fun onFailure(reason: Int) { message.value = "Wi-Fi Direct scan failed (code $reason). Is Wi-Fi on?" }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!Perms.hasWifiDirect(ctx)) return
        val cfg = WifiP2pConfig().apply { deviceAddress = address; groupOwnerIntent = 0 } // prefer to be client so we learn the owner IP
        mgr?.connect(channel, cfg, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { message.value = "Connecting… accept the prompt on the other device" }
            override fun onFailure(reason: Int) { message.value = "Wi-Fi Direct connect failed (code $reason)" }
        })
    }

    fun disconnect() {
        mgr?.removeGroup(channel, null); groupHost.value = null; isGroupOwner.value = false
    }

    fun stop() {
        receiver?.let { runCatching { ctx.unregisterReceiver(it) } }; receiver = null
        runCatching { mgr?.stopPeerDiscovery(channel, null) }
    }
}

class NetworkMonitor(ctx: Context) {
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    /** true only when Android has validated real internet access. LAN-only transfers work regardless. */
    val hasInternet = MutableStateFlow(check())
    val hasWifiOrEthernet = MutableStateFlow(checkLan())

    val localIp = MutableStateFlow(findLocalIp())

    private fun check(): Boolean = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    private fun checkLan(): Boolean = cm.getNetworkCapabilities(cm.activeNetwork)?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true

    private fun findLocalIp(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces.asSequence()) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses.asSequence()) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Throwable) { null }
    }

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                hasInternet.value = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                hasWifiOrEthernet.value = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                localIp.value = findLocalIp()
            }
            override fun onLost(n: Network) {
                hasInternet.value = false
                hasWifiOrEthernet.value = false
                localIp.value = findLocalIp()
            }
        })
    }
}

