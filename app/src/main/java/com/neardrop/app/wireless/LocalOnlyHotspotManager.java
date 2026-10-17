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

                    mainHandler.post(() -> callback.onHotspotStarted(finalSsid, finalPass));
                }

                @Override
                public void onStopped() {
                    super.onStopped();
                    reservation = null;
                    mainHandler.post(callback::onHotspotStopped);
                }

                @Override
                public void onFailed(int reason) {
                    super.onFailed(reason);
                    reservation = null;
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
    }

    public boolean isRunning() {
        return reservation != null;
    }
}
