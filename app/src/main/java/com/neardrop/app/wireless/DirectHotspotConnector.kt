package com.neardrop.app.wireless

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

object DirectHotspotConnector {

    val isConnecting = MutableStateFlow(false)
    val isConnected = MutableStateFlow(false)
    val connectedSsid = MutableStateFlow<String?>(null)

    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Opens system settings or Quick Settings panel to enable Wi-Fi.
     */
    fun openWifiSettings(context: Context) {
        val attempts = listOf(
            Intent("android.settings.panel.action.WIFI").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("android.settings.panel.action.INTERNET_CONNECTIVITY").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Settings.ACTION_WIFI_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }

    /**
     * Connects to receiver's temporary hotspot without the sender needing to type any password.
     */
    fun connectToHotspot(
        context: Context,
        ssid: String,
        passphrase: String,
        onConnected: (gatewayIp: String?) -> Unit,
        onFailed: (String) -> Unit
    ) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            onFailed("ConnectivityManager is not available")
            return
        }

        disconnect(context)

        isConnecting.value = true
        connectedSsid.value = ssid

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .apply {
                        if (passphrase.isNotBlank()) {
                            setWpa2Passphrase(passphrase)
                        }
                    }
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        try {
                            cm.bindProcessToNetwork(network)
                        } catch (_: Exception) {}

                        isConnecting.value = false
                        isConnected.value = true
                        connectedSsid.value = ssid

                        val linkProps = cm.getLinkProperties(network)
                        val gateway = linkProps?.routes?.firstOrNull { it.isDefaultRoute || it.gateway != null }?.gateway?.hostAddress
                            ?: "192.168.43.1"

                        onConnected(gateway)
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        isConnecting.value = false
                        isConnected.value = false
                        onFailed("Could not connect to hotspot: $ssid")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        isConnecting.value = false
                        isConnected.value = false
                        connectedSsid.value = null
                    }
                }

                activeCallback = callback
                cm.requestNetwork(request, callback)
            } catch (e: Exception) {
                isConnecting.value = false
                onFailed(e.message ?: "Failed to connect to hotspot")
            }
        } else {
            // Android 9 and below
            try {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager == null) {
                    isConnecting.value = false
                    onFailed("WifiManager unavailable")
                    return
                }
                @Suppress("DEPRECATION")
                val conf = android.net.wifi.WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (passphrase.isNotBlank()) {
                        preSharedKey = "\"$passphrase\""
                    } else {
                        allowedKeyManagement.set(android.net.wifi.WifiConfiguration.KeyMgmt.NONE)
                    }
                }
                @Suppress("DEPRECATION")
                val netId = wifiManager.addNetwork(conf)
                @Suppress("DEPRECATION")
                wifiManager.disconnect()
                @Suppress("DEPRECATION")
                wifiManager.enableNetwork(netId, true)
                @Suppress("DEPRECATION")
                wifiManager.reconnect()
                isConnecting.value = false
                isConnected.value = true
                onConnected("192.168.43.1")
            } catch (e: Exception) {
                isConnecting.value = false
                onFailed(e.message ?: "Failed to connect to Wi-Fi")
            }
        }
    }

    fun disconnect(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        activeCallback?.let {
            try {
                cm?.unregisterNetworkCallback(it)
                cm?.bindProcessToNetwork(null)
            } catch (_: Exception) {}
            activeCallback = null
        }
        isConnecting.value = false
        isConnected.value = false
        connectedSsid.value = null
    }
}
