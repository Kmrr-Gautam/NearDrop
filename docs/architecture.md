# Architecture
- **UI** (Compose) → `MainViewModel` → `NearDropEngine` (singleton in `NearDropApplication`).
- **Engine** owns: `NsdDiscovery`, `BleBeacon`, `WifiDirectManager`, `NetworkMonitor`, the listening `ServerSocket`, running jobs, trust (Room), history (Room), settings (DataStore).
- **Transfer core** (`transfer/TransferCore.kt`) is Android-free: `TransferSender` / `TransferReceiver` operate on a `Socket`, `SendSource` and `java.io.File`, which is why they are covered by JVM loopback tests.
- **Service**: `TransferService` (foreground, `dataSync`) mirrors engine state into a notification and stops itself when idle. The engine itself lives in the process; if Android kills the process, transfers stop and can be resumed.
- Memory: file data flows through one 256 KiB buffer (configurable) per transfer; received data is written straight to `<n>.part` files.
## Transport decisions
| Path | Status | Notes |
|---|---|---|
| LAN via mDNS (NsdManager) | implemented | Works with no internet; reported as "Local network" |
| Wi-Fi Direct (WifiP2pManager) | implemented (client side) | Reported as "Wi-Fi Direct" |
| BLE | presence only | no file data, no identifiers |
| Nearby Connections / browser | not implemented | |
TCP is used (not QUIC): it is available on every transport above and in a future desktop client. A browser/PWA cannot open raw TCP sockets; it would need a WebSocket (or WebRTC) front-end speaking the same framed protocol — not provided.
