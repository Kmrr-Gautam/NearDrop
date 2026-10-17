package com.neardrop.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neardrop.app.NearDropApplication
import com.neardrop.app.core.*
import com.neardrop.app.data.Settings
import com.neardrop.app.service.TransferService
import com.neardrop.app.transfer.FileResolver
import com.neardrop.app.transfer.SendItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val engine = (app as NearDropApplication).engine
    private val ctx = app

    val settings = engine.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val settingsLoaded = MutableStateFlow(false)
    val devices = engine.devices
    val progress = engine.progress.map { it.values.sortedByDescending { p -> p.startedAt } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val incoming = engine.incoming
    val history = engine.db.transfers().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trusted = engine.db.trusted().observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hasInternet = engine.network.hasInternet
    val hasLan = engine.network.hasWifiOrEthernet
    val bleNearby = engine.ble.nearby
    val p2pPeers = engine.wifiDirect.peers
    val p2pMessage = engine.wifiDirect.message
    val p2pGroupHost = engine.wifiDirect.groupHost
    val p2pIsOwner = engine.wifiDirect.isGroupOwner
    val receiving = engine.receiving
    val localIp = engine.localIp
    val selectedDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val selectedItems = MutableStateFlow<List<SendItem>>(emptyList())

    init {
        viewModelScope.launch {
            engine.settings.flow.collect { s ->
                settingsLoaded.value = true
                val want = s.onboardingDone && s.visibility != Visibility.OFF
                engine.setReceiving(want)
                engine.refreshAdvertisement()
                if (want) TransferService.start(ctx)
            }
        }
    }

    fun startDiscovery() = engine.startDiscovery()
    fun stopDiscovery() = engine.stopDiscovery()

    fun addManualDevice(host: String, port: Int = Proto.DEFAULT_PORT, name: String? = null): DiscoveredDevice {
        val dev = engine.addManualDevice(host, port, name)
        selectedDevice.value = dev
        return dev
    }


    fun setName(v: String) = viewModelScope.launch { engine.settings.setDeviceName(v) }
    fun setVisibility(v: Visibility) = viewModelScope.launch { engine.settings.setVisibility(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { engine.settings.setTheme(v) }
    fun setNotifications(v: Boolean) = viewModelScope.launch { engine.settings.setNotifications(v) }
    fun finishOnboarding(name: String) = viewModelScope.launch { engine.settings.setDeviceName(name); engine.settings.setOnboardingDone(true) }

    fun addFiles(uris: List<Uri>) { selectedItems.update { cur -> (cur + FileResolver.resolveFiles(ctx, uris)).distinctBy { it.uri } } }
    fun addTree(uri: Uri) { viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { val items = FileResolver.resolveTree(ctx, uri); selectedItems.update { (it + items).distinctBy { i -> i.uri } } } }
    fun removeItem(i: Int) = selectedItems.update { it.filterIndexed { n, _ -> n != i } }
    fun clearSelection() { selectedItems.value = emptyList() }

    fun send(): String? {
        val d = selectedDevice.value ?: return null
        val items = selectedItems.value.ifEmpty { return null }
        TransferService.start(ctx)
        val id = engine.send(d, items)
        selectedItems.value = emptyList()
        return id
    }

    fun respond(accept: Boolean, trust: Boolean) { engine.incoming.value?.decision?.complete(accept to trust) }
    fun cancel(id: String) = engine.cancel(id)
    fun retry(id: String): String? { TransferService.start(ctx); return engine.retry(id) }
    fun canRetry(id: String) = engine.canRetry(id)
    fun dismiss(id: String) = engine.dismiss(id)
    fun trustPeer(fp: String, name: String) = engine.trustPeer(fp, name)
    fun removeTrusted(fp: String) = viewModelScope.launch { engine.db.trusted().delete(fp) }
    fun clearTrusted() = viewModelScope.launch { engine.db.trusted().clear() }
    fun setAutoAccept(fp: String, on: Boolean) = viewModelScope.launch { engine.db.trusted().setAutoAccept(fp, on) }
    fun clearHistory() = viewModelScope.launch { engine.db.transfers().clear() }
    fun clearPartials() = engine.clearPartials()
    fun scanWifiDirect() = engine.wifiDirect.discover()
    fun connectWifiDirect(addr: String) = engine.wifiDirect.connect(addr)
    fun disconnectWifiDirect() = engine.wifiDirect.disconnect()
    val receivedPath: String get() = engine.receivedDir.absolutePath
    val fingerprint: String get() = engine.identity.fingerprint
    val listenPort get() = engine.listenPort
    val p2pSupported get() = engine.wifiDirect.supported
    val bleEnabled get() = engine.ble.bluetoothEnabled
}
