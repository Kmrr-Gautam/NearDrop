# Testing
## Automated (not yet run by the author — no toolchain was available)
- JVM (`./gradlew :app:testDebugUnitTest`): path sanitiser, manifest validation, collision naming, checksum/prefix hashing, handshake (SAS agreement, framing, garbage input), loopback transfers (multi-file, folders, empty file, 5 MB file, reject, visibility block, wrong fingerprint, malicious path, interrupted resume, resume with changed source, collisions).
- Instrumented (`connectedDebugAndroidTest`): Room history/trust persistence, permission list.
- Not covered yet: foreground service lifecycle, activity recreation, Wi-Fi Direct, BLE, low-storage behaviour on a real device, tampered-ciphertext injection.
## Manual acceptance (two phones)
1. Install on both; finish onboarding and grant permissions.
2. Turn mobile data off and disconnect Wi-Fi from internet (or use a router with the WAN unplugged). Both phones on that Wi-Fi. Phone B shows up on A under Nearby devices.
3. A: Send → pick a file → B shows the request with a security code; check the codes match; Accept.
4. Compare SHA-256 of source and received file (e.g. `sha256sum` via adb pull).
5. Reject test; unknown sender must always prompt. Trust a device, enable auto-accept, resend.
6. Interrupt (toggle Wi-Fi mid-transfer), then Retry on the sender's progress screen — it should resume from > 0.
7. Wi-Fi Direct: turn Wi-Fi networks off, Scan → Connect on the client, accept the system prompt, then send to the "Wi-Fi Direct peer".
8. Bluetooth off, permissions denied, Wi-Fi off: the home screen shows the corresponding state.
Record real throughput yourself; none is claimed here.
