# Android networking notes
- `INTERNET` is declared because TCP sockets require it even on networks with no internet.
- Permissions: Android 12+: BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT. Android 13+: NEARBY_WIFI_DEVICES (neverForLocation), POST_NOTIFICATIONS. Android ≤12: ACCESS_FINE_LOCATION (needed by Wi-Fi Direct peer discovery). Files use the Storage Access Framework — no storage permission.
- Foreground service type `dataSync` with FOREGROUND_SERVICE_DATA_SYNC (target SDK 34). Android can still kill the process; transfers then end as "interrupted" and can be resumed.
- Scanning (mDNS, BLE) runs only while the activity is started.
- Wi-Fi Direct: the app asks for `groupOwnerIntent = 0` to usually end up as client and learn the owner IP via `requestConnectionInfo`. If it becomes owner, the other device must send. Wi-Fi Direct to Windows PCs is not assumed; use the LAN path.
- Local-network vs direct: the device card says "Local network" or "Wi-Fi Direct"; the home screen shows an Offline chip when Android has not validated internet access.
- Some routers block mDNS between clients (AP/client isolation); discovery then fails even though Wi-Fi is connected.
