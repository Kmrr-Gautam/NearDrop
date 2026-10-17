# Security model
- Identity: ECDSA P-256 key in Android Keystore (non-exportable). No passwords or raw private keys are stored in preferences or Room.
- Session: mutual signature over the handshake transcript, ephemeral ECDH (forward secrecy), HKDF-style derivation with HMAC-SHA256, AES-256-GCM frames with counters (replay/reorder protection), 8 MiB frame cap, socket timeouts. Only JCA primitives; no custom cryptography.
- Trust: a device is trusted only after an explicit user action — ticking "Trust this device" on an incoming request, or "Trust this device" on the sender's progress screen. Compare the 6-digit security code on both screens first: it detects a man-in-the-middle. Trust is keyed on the identity fingerprint. Remove one device or clear all under Settings → Trusted devices.
- Unknown senders always get an Accept/Reject prompt. Auto-accept is a per-device opt-in on trusted devices only. Visibility "Trusted" refuses unknown fingerprints after authentication; "Off" closes the listening socket.
- The mDNS fingerprint prefix lets the sender detect a swapped identity for a device it discovered; it is not a substitute for comparing the code (an attacker can copy the TXT record).
- Receiver hardening: path traversal rejection, size/count validation, free-space check, partial files isolated per transfer, SHA-256 per file, files never auto-opened/installed.
- No internet is used. There is no cloud or relay fallback in the code.
- Known limits: BLE beacon reveals that a NearDrop device is nearby (no ID); mDNS TXT reveals device name/type to the local network; the fingerprint prefix is public.
