package com.neardrop.app.wireless;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class WirelessReadinessManager {

    public static class ReadinessState {
        public boolean isWifiEnabled;
        public boolean hasLocationPermission;
        public boolean hasNearbyPermission;
        public boolean isHotspotSupported;
        public boolean hasInternet;

        public boolean isReadyForDirectTransfer() {
            return isWifiEnabled && hasLocationPermission && hasNearbyPermission;
        }
    }

    public static ReadinessState checkReadiness(Context context) {
        ReadinessState state = new ReadinessState();

        // 1. Wi-Fi Check
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        state.isWifiEnabled = wifiManager != null && wifiManager.isWifiEnabled();

        // 2. Location Permission Check (Required for Wi-Fi Direct on Android 10-12)
        state.hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        // 3. Nearby Wi-Fi Devices Permission Check (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            state.hasNearbyPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED;
        } else {
            state.hasNearbyPermission = true;
        }

        // 4. Local-Only Hotspot check
        state.isHotspotSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

        // 5. Internet Connectivity Check (separated from local Wi-Fi)
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            Network net = cm.getActiveNetwork();
            if (net != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                state.hasInternet = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }

        return state;
    }
}
