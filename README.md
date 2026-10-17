<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="100" height="100" alt="NearDrop Logo" style="border-radius: 20%;" />
</p>

<h1 align="center">NearDrop</h1>

<p align="center">
  <strong>Fast, private, and effortless offline file sharing for Android.</strong><br>
  No accounts • No cloud • No tracking • 100% Peer-to-Peer
</p>

<p align="center">
  <a href="https://github.com/Kmrr-Gautam/NearDrop/releases"><img src="https://img.shields.io/badge/Release-v0.1.0-blue.svg?style=for-the-badge" alt="Release" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?style=for-the-badge&logo=android" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin%202.0-purple.svg?style=for-the-badge&logo=kotlin" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=for-the-badge&logo=jetpackcompose" alt="Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg?style=for-the-badge" alt="License" /></a>
</p>

---

## 📥 Download APK

Get the latest pre-compiled, tested APK and start transferring files immediately:

<p align="center">
  <a href="https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.1.0.apk">
    <img src="https://img.shields.io/badge/⬇️%20Direct%20Download-NearDrop%20v0.1.0%20APK-2ea44f?style=for-the-badge&logo=android&logoColor=white" height="42" alt="Download APK" />
  </a>
</p>

* **Direct Repository Download**: [`release/neardrop-v0.1.0.apk`](https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.1.0.apk) *(17.9 MB)*
* **GitHub Releases**: [NearDrop v0.1.0 Releases](https://github.com/Kmrr-Gautam/NearDrop/releases)

---

## ✨ Features

- 👴 **Senior-Friendly & Accessible**: Minimal, clutter-free interface with extra-large touch targets, clear high-contrast typography, and intuitive two-button actions ("Send" and "Receive").
- 📄 **Prominent File Name Display**: The exact file name, format icon (photos, videos, audio, documents), and transfer progress are always clearly visible across every screen.
- 🔒 **100% Private & P2P**: Direct device-to-device transfers over Wi-Fi/LAN. Protected by ECDH (Elliptic Curve Diffie-Hellman) key exchange and AES-256-GCM authenticated encryption. Zero third-party cloud servers or tracking.
- ⚡ **High Performance & Resume**: High-speed raw socket streaming with SHA-256 chunk-level integrity verification. Transfers can be safely retried and resumed from verified checkpoints.
- 🌐 **Flexible Discovery + Direct IP**: Automatic discovery via local network mDNS and Wi-Fi Direct, with full support for manual **Direct IP connection** (`+ Connect by IP`) for cross-subnet and emulator testing.
- 🔄 **Built-in "Check for Updates"**: Check for new software releases directly inside the Settings screen, complete with automatic update checking and release notes.
- 🎨 **Claymorphism Aesthetic**: Soft, tactile design system with dynamic light and dark modes tailored for comfortable viewing.

---

## 📱 Screenshots

<p align="center">
  <img src="docs/images/home.png" width="30%" alt="Home Screen" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/images/send.png" width="30%" alt="Send Screen" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/images/settings.png" width="30%" alt="Settings & Updates" />
</p>
<p align="center">
  <em>Left: Home Screen with Senior-Friendly Controls • Center: Send Screen with File Selection • Right: Settings with "Check for Updates"</em>
</p>

---

## 🚀 Getting Started

### Installation on Android Device

1. Download the [NearDrop v0.1.0 APK](https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.1.0.apk) on your Android device (Android 8.0 Oreo or higher).
2. Open the downloaded `.apk` file from your notification bar or file manager.
3. If prompted, enable **"Allow from this source"** for your browser or file manager.
4. Tap **Install** and open **NearDrop**.

### Sending Files

1. Make sure both devices are connected to the same Wi-Fi network (or mobile hotspot).
2. On the receiving device: Open NearDrop and ensure **"Receive Files"** is toggled ON.
3. On the sending device: Tap **Send Photos & Files**, select the items you wish to transfer, select the recipient device, and tap **Send Now**.
4. The receiver will see an instant prompt displaying the file name and sender name to **Accept** or **Decline**.

> **Tip for Emulators & Cross-Subnet:** If mDNS multicast is blocked on your router or between an Android Emulator and physical phone, tap **`+ Connect by IP`** and enter the other device's IP address and port (default: `45454`).

---

## 🛠️ Building from Source

### Prerequisites
- **Android Studio**: Koala (2024.1.1) or newer
- **Java Development Kit**: JDK 17 or JDK 21
- **Android SDK**: API 34 (Compile & Target)

### Command Line Build

Clone the repository and run:

```bash
# Clone the repository
git clone https://github.com/Kmrr-Gautam/NearDrop.git
cd NearDrop

# Run unit tests (JVM)
./gradlew :app:testDebugUnitTest

# Assemble Debug APK
./gradlew :app:assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧪 Testing & Verification

The codebase includes comprehensive unit and integration test coverage:

| Test Suite | Coverage | Status |
| :--- | :--- | :--- |
| `TransferLoopbackTest` | P2P streaming, packet framing, chunk verification, resume validation | ✅ 8/8 Passed |
| `ProtocolTest` | Message encoding/decoding, handshake payloads, manifest parsing | ✅ 8/8 Passed |
| `SecureHandshakeTest` | ECDH key agreement, AES-256-GCM session key derivation, replay prevention | ✅ 3/3 Passed |
| `NearDropDatabaseTest` | Room database migrations, record persistence, history tracking | ✅ Passed |

Run the test suite:
```bash
./gradlew :app:testDebugUnitTest
```

---

## 🏗️ Architecture & Project Structure

NearDrop is built entirely in modern Kotlin with Jetpack Compose:

```
NearDrop/
├── app/src/main/java/com/neardrop/app/
│   ├── core/          # Protocol models, framing constants, permission handlers
│   ├── security/      # Android Keystore identity, ECDH key exchange, AES-GCM cipher
│   ├── transfer/      # NearDropEngine, streaming sender, receiver, resume logic
│   ├── discovery/     # NetworkMonitor, mDNS advertiser/browser, Wi-Fi Direct
│   ├── data/          # Room database (history), DataStore preferences
│   ├── service/       # Foreground service for uninterrupted background transfers
│   └── ui/
│       ├── components/ # Claymorphism UI library (ClayCard, ClayButton, ClayDialog)
│       ├── screens/    # HomeScreen, SendScreen, SettingsScreen, ProgressScreen
│       └── theme/      # Custom Clay theme with dynamic Light & Dark palettes
├── docs/              # Protocol specifications, testing guides, screenshots
└── release/           # Pre-built release APKs for direct installation
```

---

## 🔒 Security & Privacy Notice

NearDrop is built from the ground up on zero-trust local principles:
- **No telemetry or tracking**: Zero analytics SDKs or remote pings.
- **No cloud relays**: Files never leave your local Wi-Fi / hotspot network.
- **Cryptographic authentication**: Device fingerprints are generated from hardware-backed ECC keys.
- **Explicit consent**: Unrecognized devices require one-tap manual approval before any payload byte is written to storage.

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
Feel free to contribute, open issues, or submit pull requests!
