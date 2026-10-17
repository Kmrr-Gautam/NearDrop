<p align="center">
  <img src="docs/images/logo.png" width="140" height="140" alt="NearDrop Logo" style="border-radius: 28px;" />
</p>

<h1 align="center">NearDrop</h1>

<p align="center">
  <strong>Fast, private, and effortless offline file sharing for Android.</strong><br>
  Instant In-App Updates • Screen QR Scanner • Zero Cloud Transit • 100% Peer-to-Peer
</p>

<p align="center">
  <a href="https://github.com/Kmrr-Gautam/NearDrop/releases"><img src="https://img.shields.io/badge/Release-v0.2.3-blue.svg?style=for-the-badge" alt="Release" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?style=for-the-badge&logo=android" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin%20%26%20Java-purple.svg?style=for-the-badge&logo=kotlin" alt="Kotlin and Java" /></a>
  <a href="https://fastapi.tiangolo.com"><img src="https://img.shields.io/badge/Backend-FastAPI%20Python-009688.svg?style=for-the-badge&logo=fastapi" alt="FastAPI" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Claymorphism%20Compose-4285F4.svg?style=for-the-badge&logo=jetpackcompose" alt="Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg?style=for-the-badge" alt="License" /></a>
</p>

---

## 📥 Download Latest APK (v0.2.3)

Download the verified release APK directly and start transferring files offline at maximum Wi-Fi speeds:

<p align="center">
  <a href="https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.3.apk">
    <img src="https://img.shields.io/badge/⬇️%20Direct%20Download-NearDrop%20v0.2.3%20APK-2ea44f?style=for-the-badge&logo=android&logoColor=white" height="46" alt="Download APK v0.2.3" />
  </a>
</p>

* **Direct Repository Download**: [`release/neardrop-v0.2.3.apk`](https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.3.apk) *(14.2 MB)*
* **GitHub Releases**: [NearDrop Releases](https://github.com/Kmrr-Gautam/NearDrop/releases)
* **Developer Profile**: [Gautam Kumar (@Kmrr-Gautam)](https://github.com/Kmrr-Gautam)

---

## 🌟 What's New in v0.2.3

* ⚡ **Seamless In-App Update Downloader**:
  - Download and install updates directly inside NearDrop without leaving the app or opening a browser!
  - Real-time Clay progress bar with live percentage and downloaded byte counters (`MB / MB`).
  - Automatic Android Package Installer prompt launch as soon as the download finishes.
  - Smart cache detection: if the update package is already downloaded, tap **Install Now** immediately.
* 📱 **Screen & Monitor QR Scanner**:
  - Rewritten CameraX image analysis pipeline eliminating row-stride distortion.
  - Instant zero-latency QR code detection on PC screens, laptop monitors, and mobile displays with dual binarizers.
* 🌐 **Device Local IP Display**:
  - Displays the device's local network IP address directly in **Settings > About & Network** with a 1-tap copy button.
* ☁️ **Complete FastAPI Cloud Control Plane (`neardrop-backend/`)**:
  - Python 3.11+ backend with JWT authentication, device token management, 8-character transfer session codes, and offline history synchronization (`/api/v1/transfers/sync`).
  - **Zero-File-Transit Architecture**: File payloads transfer 100% phone-to-phone; zero file bytes pass through the cloud server.
* 🎨 **Parabolic File Flying Animation**:
  - Fluid Bezier flight curve connecting devices with realistic 3D banking, stardust particle trails, and receiver absorption pulses.
* 🔗 **Developer Profile**:
  - One-tap link to developer GitHub profile ([@Kmrr-Gautam](https://github.com/Kmrr-Gautam)) embedded directly in Settings.

---

## ✨ Key Features

- ⚡ **Instant QR Code Pairing**: The receiver taps **"Show QR Code"**, the sender taps **"Scan QR"**, and the devices connect immediately without waiting for discovery or typing any IP addresses.
- 🔒 **100% Private & P2P**: Direct Wi-Fi / Local Network transfer using hardware-backed ECDH key agreement and AES-256-GCM authenticated encryption. Works 100% offline with zero internet required.
- 🎨 **Claymorphic UI**: Beautiful pastel cards, high contrast, extra-large touch targets, and tactile clay buttons designed for effortless use by all ages.
- 📄 **Prominent File Cards**: Shows exact file names, sizes, and file type badges (photos, videos, music, documents, archives, APKs).
- 🚀 **High Speed & Resumable**: High-throughput raw socket streaming with length-prefixed framing (`DirectProtocol.java`), chunk-level SHA-256 verification, and interrupted transfer resume.
- 🔄 **Built-in In-App Updates**: Live cache-busting check against GitHub with direct in-app download and installation.

---

## 📱 Screenshots

<p align="center">
  <img src="docs/images/home.png" width="23%" alt="Home Screen" />
  &nbsp;
  <img src="docs/images/qr_receive.png" width="23%" alt="Receive by QR Code" />
  &nbsp;
  <img src="docs/images/send.png" width="23%" alt="Send Files Screen" />
  &nbsp;
  <img src="docs/images/settings.png" width="23%" alt="Settings & Updates" />
</p>
<p align="center">
  <em>1. Home Screen with QR Options • 2. Instant QR Receiver • 3. Clean Send Screen • 4. Settings with In-App Updates & IP Copy</em>
</p>

---

## ⚡ How to Transfer Files via QR Code

1. **Receiver**: Open NearDrop and tap **Show QR Code to Receive**.
2. **Sender**: Tap **Scan QR** (from the Home screen or Send screen) and point the camera at the receiver's screen.
3. The sender connects in milliseconds and streams the files directly over the high-speed local network!

---

## 🏗️ System Architecture

```
┌────────────────────────────────────────────────────────┐
│                   NearDrop Ecosystem                   │
├──────────────────────────┬─────────────────────────────┤
│   Android Client App     │   FastAPI Cloud Backend     │
│   (Java + Kotlin Compose)│   (neardrop-backend/)       │
├──────────────────────────┼─────────────────────────────┤
│ • Local Wi-Fi Direct     │ • JWT Auth & Refresh        │
│ • Local-Only Hotspot     │ • Device Token Registration │
│ • Direct TCP Sockets     │ • 8-Char Session Codes      │
│ • AES-256-GCM Encryption │ • Transfer Event History    │
│ • Room Local Database    │ • Offline Metadata Sync     │
│ • In-App Update Engine   │ • Zero File Bytes Transit   │
└──────────────────────────┴─────────────────────────────┘
```

---

## 🚀 Building & Running

### 1. Android Application

#### Prerequisites
- Android Studio Ladybug or newer
- JDK 17 or 21
- Android SDK 34 (minSdk 26, Android 8.0+)

#### Install on Device
1. Download [`neardrop-v0.2.3.apk`](https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.3.apk) directly to your Android device.
2. Tap the downloaded file to install and open NearDrop.

#### Build from Source
```bash
# Clone the repository
git clone https://github.com/Kmrr-Gautam/NearDrop.git
cd NearDrop

# Run test suite
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Build production release APK
./gradlew assembleRelease
```
The compiled APK will be generated at `app/build/outputs/apk/release/`.

---

### 2. FastAPI Backend (`neardrop-backend/`)

#### Prerequisites
- Python 3.11 or newer

#### Setup & Run
```bash
cd neardrop-backend

# Create virtual environment
python -m venv venv
# Linux / macOS:
source venv/bin/activate
# Windows:
.\venv\Scripts\activate

# Install pinned dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env

# Run database migrations
alembic upgrade head

# Start Uvicorn development server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

#### Interactive Documentation
- **Swagger UI**: `http://127.0.0.1:8000/docs`
- **ReDoc**: `http://127.0.0.1:8000/redoc`

#### Run Backend Tests
```bash
pytest
```

---

## 🧪 Verified Tests

| Test Suite | Purpose | Status |
| :--- | :--- | :--- |
| `TransferLoopbackTest` | P2P streaming, framing, SHA-256 chunks, resume | ✅ Passed |
| `ProtocolTest` | Handshake, message encode/decode, manifest validation | ✅ Passed |
| `SecureHandshakeTest` | ECDH key exchange, AES-256-GCM cipher encryption | ✅ Passed |
| `NearDropDatabaseTest` | Room database history persistence & sync queue | ✅ Passed |
| `neardrop-backend/tests` | Auth, devices, transfer sessions, offline sync | ✅ Passed |

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).

---

<p align="center">
  Crafted with ❤️ by <a href="https://github.com/Kmrr-Gautam">Gautam Kumar</a>
</p>
