# Future Windows / PWA clients
- Implement `docs/protocol.md` exactly. A Windows client needs: mDNS (`_neardrop._tcp`), a long-term ECDSA P-256 key (store in DPAPI/TPM), ECDH P-256, HMAC-SHA256, AES-256-GCM, SHA-256. Rust crates (`p256`, `aes-gcm`, `hmac`, `sha2`) match these primitives; the Kotlin transfer core is a reference to port.
- Suggested path: build a Rust crate implementing handshake, framing, manifest validation and resume, test it against the Android app's JVM loopback tests (shared test vectors still to be written), then optionally bind it back into Android via UniFFI.
- PWA: browsers cannot open raw TCP or advertise mDNS. A PWA needs a different carrier (e.g. WebSocket/WebRTC data channel) carrying the same encrypted frames, plus a manual pairing step (QR with host/port/fingerprint). The Android app does not expose such a listener yet.
- Do not claim Windows support until tested against a real client.
