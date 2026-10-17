<p align="center">
  <img src="docs/images/logo.png" width="140" height="140" alt="NearDrop Logo" style="border-radius: 28px;" />
</p>

<h1 align="center">NearDrop</h1>

<p align="center">
  <strong>Fast, private, and effortless offline file sharing for Android.</strong><br>
  Instant QR Code Pairing • Zero Cloud • No Tracking • 100% Peer-to-Peer
</p>

<p align="center">
  <a href="https://github.com/Kmrr-Gautam/NearDrop/releases"><img src="https://img.shields.io/badge/Release-v0.2.1-blue.svg?style=for-the-badge" alt="Release" /></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg?style=for-the-badge&logo=android" alt="Platform" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Language-Kotlin%202.0-purple.svg?style=for-the-badge&logo=kotlin" alt="Kotlin" /></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=for-the-badge&logo=jetpackcompose" alt="Compose" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-orange.svg?style=for-the-badge" alt="License" /></a>
</p>

---

## 📥 Download APK

Install the verified release APK and start transferring files instantly:

<p align="center">
  <a href="https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.1.apk">
    <img src="https://img.shields.io/badge/⬇️%20Direct%20Download-NearDrop%20v0.2.1%20APK-2ea44f?style=for-the-badge&logo=android&logoColor=white" height="44" alt="Download APK" />
  </a>
</p>

* **Direct Repository Download**: [`release/neardrop-v0.2.1.apk`](https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.1.apk) *(20 MB)*
* **GitHub Releases**: [NearDrop Releases](https://github.com/Kmrr-Gautam/NearDrop/releases)
* **Developer Profile**: [Gautam Kumar (@Kmrr-Gautam)](https://github.com/Kmrr-Gautam)

---

## 🌟 What's New in v0.2.1

* 🔗 **Developer Profile**: Direct developer GitHub link (`@Kmrr-Gautam`) embedded right in Settings.
* 🚀 **Fluid File Flying Animation**: Physics-inspired parabolic Bezier flight trajectory connecting devices with realistic 3D banking, comet stardust trails, and receiver absorption pulses.
* ✨ **Spotlight File Card**: Clean, prominent typography showing exact file names, vibrant color-coded file badges (Images, Videos, Music, Documents, Archives, APKs), and formatted sizes.
* 🧹 **Zero Clutter**: Completely eliminated redundant cards and repetitive text walls for a modern, focused user experience.
* 📲 **Instant Receiving Screen**: Automatically transitions into the progress page as soon as incoming files are accepted.
* 🔄 **Live In-App Updates**: Built-in GitHub update checker displays a "What's New" popup with one-tap APK installation.

---

## ✨ Features

- ⚡ **Instant QR Code Transfer**: The receiver taps **"Show QR Code"**, the sender taps **"Scan QR"**, and the devices connect immediately without waiting for discovery or typing any IP addresses!
- 🎨 **Animated Parabolic Transfer Flight**: Real-time visual flight paths showing files streaming across devices.
- 👴 **Senior-Friendly & Minimal**: Designed with tactile pastel clay cards, high contrast, extra-large touch targets, and zero jargon.
- 📄 **Prominent File Names & Sizes**: Every screen highlights the exact file names, sizes, and file type icons (photos, videos, audio, documents).
- 🔒 **100% Private & P2P**: Direct Wi-Fi / Local Network transfer using hardware-backed ECDH key agreement and AES-256-GCM authenticated encryption. No accounts, no cloud relays, no tracking.
- 🔄 **Built-in "Check for Updates"**: Software update check integrated directly into Settings with automatic notifications and release notes.
- 🚀 **High Speed & Resumable**: High-throughput raw socket streaming with chunk-level SHA-256 verification and interrupted transfer resume.

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
  <em>1. Home Screen with QR options • 2. Instant QR Receiver • 3. Senior-Friendly Send Screen • 4. Settings with Software Updates</em>
</p>

---

## ⚡ How to Transfer Files via QR Code

1. **Receiver**: Open NearDrop and tap **Show QR Code to Receive**.
2. **Sender**: Tap **Scan QR** (from the Home screen or Send screen) and point the camera at the receiver's screen.
3. The sender device connects in milliseconds and sends the files directly over the high-speed local network!

---

## 🚀 Installation & Build

### Install on Device
1. Download [`neardrop-v0.2.0.apk`](https://github.com/Kmrr-Gautam/NearDrop/raw/main/release/neardrop-v0.2.0.apk) on your Android device (Android 8.0+).
2. Tap the downloaded file to install and launch NearDrop.

### Build from Source
```bash
git clone https://github.com/Kmrr-Gautam/NearDrop.git
cd NearDrop

# Run test suite
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug
```

---

## 🧪 Verified Tests

| Test Suite | Purpose | Status |
| :--- | :--- | :--- |
| `TransferLoopbackTest` | P2P streaming, framing, SHA-256 chunks, resume | ✅ 8/8 Passed |
| `ProtocolTest` | Handshake, message encode/decode, manifest validation | ✅ 8/8 Passed |
| `SecureHandshakeTest` | ECDH key exchange, AES-256-GCM cipher encryption | ✅ 3/3 Passed |
| `NearDropDatabaseTest` | Room database history persistence & migrations | ✅ Passed |

---

## 📄 License

Licensed under the [Apache License, Version 2.0](LICENSE).
