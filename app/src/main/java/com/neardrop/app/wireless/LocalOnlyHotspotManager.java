package com.neardrop.app.wireless;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

public class LocalOnlyHotspotManager {

    public interface HotspotCallback {
        void onHotspotStarted(String ssid, String passphrase);
        void onHotspotFailed(int reason);
        void onHotspotStopped();
    }

    private final Context context;
    private final WifiManager wifiManager;
    private WifiManager.LocalOnlyHotspotReservation reservation;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentSsid = "";
    private String currentPassphrase = "";

    public LocalOnlyHotspotManager(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
    }

    @SuppressLint("MissingPermission")
    public void startHotspot(HotspotCallback callback) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            callback.onHotspotFailed(-1);
            return;
        }

        if (wifiManager == null) {
            callback.onHotspotFailed(-2);
            return;
        }

        try {
            wifiManager.startLocalOnlyHotspot(new WifiManager.LocalOnlyHotspotCallback() {
                @Override
                public void onStarted(WifiManager.LocalOnlyHotspotReservation res) {
                    super.onStarted(res);
                    reservation = res;
                    String ssid = "";
                    String passphrase = "";

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (res.getSoftApConfiguration() != null) {
                            ssid = res.getSoftApConfiguration().getSsid();
                            passphrase = res.getSoftApConfiguration().getPassphrase();
                        }
                    } else {
                        @SuppressWarnings("deprecation")
                        android.net.wifi.WifiConfiguration config = res.getWifiConfiguration();
                        if (config != null) {
                            ssid = config.SSID;
                            passphrase = config.preSharedKey;
                        }
                    }

                    final String finalSsid = ssid != null ? ssid : "NearDrop-Direct";
                    final String finalPass = passphrase != null ? passphrase : "";
                    currentSsid = finalSsid;
                    currentPassphrase = finalPass;

                    mainHandler.post(() -> callback.onHotspotStarted(finalSsid, finalPass));
                }

                @Override
                public void onStopped() {
                    super.onStopped();
                    reservation = null;
                    currentSsid = "";
                    currentPassphrase = "";
                    mainHandler.post(callback::onHotspotStopped);
                }

                @Override
                public void onFailed(int reason) {
                    super.onFailed(reason);
                    reservation = null;
                    currentSsid = "";
                    currentPassphrase = "";
                    mainHandler.post(() -> callback.onHotspotFailed(reason));
                }
            }, mainHandler);
        } catch (Exception e) {
            callback.onHotspotFailed(-3);
        }
    }

    public void stopHotspot() {
        if (reservation != null) {
            try {
                reservation.close();
            } catch (Exception ignored) {}
            reservation = null;
        }
        currentSsid = "";
        currentPassphrase = "";
    }

    public boolean isRunning() {
        return reservation != null;
    }

    public String getCurrentSsid() {
        return currentSsid;
    }

    public String getCurrentPassphrase() {
        return currentPassphrase;
    }

    public String getHotspotIpAddress() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    java.net.NetworkInterface intf = interfaces.nextElement();
                    String name = intf.getName().toLowerCase();
                    if (name.contains("ap") || name.contains("swlan") || name.contains("wlan") || name.contains("hotspot")) {
                        java.util.Enumeration<java.net.InetAddress> addrs = intf.getInetAddresses();
                        while (addrs.hasMoreElements()) {
                            java.net.InetAddress addr = addrs.nextElement();
                            if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                                return addr.getHostAddress();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.43.1";
    }
}
