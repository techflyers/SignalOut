<p align="center">
    <img src="https://upload.wikimedia.org/wikipedia/commons/a/a2/Bluetooth_Chat_Interface.png" alt="Signalout Android Logo" width="480">
</p>

> [!WARNING]
> This is an experimental software based on [bitchat-android](https://github.com/permissionlesstech/bitchat-android) Expect bugs and instability.

# SignalOut for Android

A secure, decentralized, peer-to-peer messaging app that works over Bluetooth mesh networks. No internet required for mesh chats, no servers, no phone numbers - just pure encrypted communication. SignalOut also supports geohash channels, which use an internet connection to connect you with others in your geographic area.

This project is a fork of the original project bitchat with a WhatsApp-style UI redesign and new features. It maintains protocol compatibility where applicable.

## Install SignalOut

You can download the latest version of SignalOut for Android from the [GitHub Releases page]

**Instructions:**

1.  **Download the APK:** On your Android device, navigate to the link above and download the latest `.apk` file. Open it.
2.  **Allow Unknown Sources:** On some devices, before you can install the APK, you may need to enable "Install from unknown sources" in your device's settings. This is typically found under **Settings > Security** or **Settings > Apps & notifications > Special app access**.
3.  **Install:** Open the downloaded `.apk` file to begin the installation.

## License

This project is released into the public domain. See the [LICENSE](LICENSE.md) file for details.

## Features

- **✅ WhatsApp UI Redesign**: Modern WhatsApp-like interface including dark/light themes, typography, custom message bubbles, doodle patterns, and redesigned chat headers/inputs.
- **✅ Voice & Video Calling over WIFI Direct**: Real-time voice and video calling powered by WebRTC.
- **✅ File & Media Attachments**: Support for sending documents, images, and audio directly in chats.
- **✅ Cross-Platform Compatible**: Full protocol compatibility with iOS SignalOut
- **✅ Decentralized Mesh Network**: Automatic peer discovery and multi-hop message relay over Bluetooth LE
- **✅ End-to-End Encryption**: X25519 key exchange + AES-256-GCM for private messages
- **✅ Channel-Based Chats**: Topic-based group messaging with optional password protection
- **✅ Store & Forward**: Messages cached for offline peers and delivered when they reconnect
- **✅ Privacy First**: No accounts, no phone numbers, no persistent identifiers
- **✅ IRC-Style Commands**: Familiar `/join`, `/msg`, `/who` style interface
- **✅ Message Retention**: Optional channel-wide message saving controlled by channel owners
- **✅ Emergency Wipe**: Triple-tap logo to instantly clear all data
- **✅ Modern Android UI**: Jetpack Compose with Material Design 3
- **✅ Dark/Light Themes**: Terminal-inspired aesthetic matching iOS version
- **✅ Battery Optimization**: Adaptive scanning and power management

## Android Setup

### Prerequisites

- **Android Studio**: Arctic Fox (2020.3.1) or newer
- **Android SDK**: API level 26 (Android 8.0) or higher
- **Kotlin**: 1.8.0 or newer
- **Gradle**: 7.0 or newer

### Build Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/techflyers/signalout.git
   cd signalout
   ```

2. **Open in Android Studio:**
   ```bash
   # Open Android Studio and select "Open an Existing Project"
   # Navigate to the signalout directory
   ```

3. **Build the project:**
   ```bash
   ./gradlew build
   ```

4. **Install on device:**
   ```bash
   ./gradlew installDebug
   ```

### Development Build

For development builds with debugging enabled:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release Build

For production releases:

```bash
./gradlew assembleRelease
```

## Android-Specific Requirements

### Permissions

The app requires the following permissions (automatically requested):

- **Bluetooth**: Core BLE functionality
- **Location**: Required for BLE scanning on Android
- **Network**: Expand your mesh through public internet relays
- **Notifications**: Message alerts and background updates

### Hardware Requirements

- **Bluetooth LE (BLE)**: Required for mesh networking
- **Android 8.0+**: API level 26 minimum
- **RAM**: 2GB recommended for optimal performance

## Usage

### Basic Commands

- `/j #channel` - Join or create a channel
- `/m @name message` - Send a private message
- `/w` - List online users
- `/channels` - Show all discovered channels
- `/block @name` - Block a peer from messaging you
- `/block` - List all blocked peers
- `/unblock @name` - Unblock a peer
- `/clear` - Clear chat messages
- `/pass [password]` - Set/change channel password (owner only)
- `/transfer @name` - Transfer channel ownership
- `/save` - Toggle message retention for channel (owner only)

### Getting Started

1. **Install the app** on your Android device (requires Android 8.0+)
2. **Grant permissions** for Bluetooth and location when prompted
3. **Launch signalout** - it will auto-start mesh networking
4. **Set your nickname** or use the auto-generated one
5. **Connect automatically** to nearby iOS and Android signalout users
6. **Join a channel** with `/j #general` or start chatting in public
7. **Messages relay** through the mesh network to reach distant peers

### Android UI Features

- **Jetpack Compose UI**: Modern Material Design 3 interface
- **Dark/Light Themes**: Terminal-inspired aesthetic matching iOS
- **Haptic Feedback**: Vibrations for interactions and notifications
- **Adaptive Layout**: Optimized for various Android screen sizes
- **Message Status**: Real-time delivery and read receipts
- **RSSI Indicators**: Signal strength colors for each peer

### Channel Features

- **Password Protection**: Channel owners can set passwords with `/pass`
- **Message Retention**: Owners can enable mandatory message saving with `/save`
- **@ Mentions**: Use `@nickname` to mention users (with autocomplete)
- **Ownership Transfer**: Pass control to trusted users with `/transfer`

## Security & Privacy

### Encryption
- **Private Messages**: X25519 key exchange + AES-256-GCM encryption
- **Channel Messages**: Argon2id password derivation + AES-256-GCM
- **Digital Signatures**: Ed25519 for message authenticity
- **Forward Secrecy**: New key pairs generated each session

### Privacy Features
- **No Registration**: No accounts, emails, or phone numbers required
- **Ephemeral by Default**: Messages exist only in device memory
- **Cover Traffic**: Random delays and dummy messages prevent traffic analysis
- **Emergency Wipe**: Triple-tap logo to instantly clear all data
- **Bundled Tor Support**: Built-in Tor network integration for enhanced privacy when internet connectivity is available

## Performance & Efficiency

### Message Compression
- **LZ4 Compression**: Automatic compression for messages >100 bytes
- **30-70% bandwidth savings** on typical text messages
- **Smart compression**: Skips already-compressed data

### Battery Optimization
- **Adaptive Power Modes**: Automatically adjusts based on battery level
  - Performance mode: Full features when charging or >60% battery
  - Balanced mode: Default operation (30-60% battery)
  - Power saver: Reduced scanning when <30% battery
  - Ultra-low power: Emergency mode when <10% battery
- **Background efficiency**: Automatic power saving when app backgrounded
- **Configurable scanning**: Duty cycle adapts to battery state

### Network Efficiency
- **Optimized Bloom filters**: Faster duplicate detection with less memory
- **Message aggregation**: Batches small messages to reduce transmissions
- **Adaptive connection limits**: Adjusts peer connections based on power mode

## Technical Architecture

### Binary Protocol
signalout uses an efficient binary protocol optimized for Bluetooth LE:
- Compact packet format with 1-byte type field
- TTL-based message routing (max 7 hops)
- Automatic fragmentation for large messages
- Message deduplication via unique IDs

### Mesh Networking
- Each device acts as both client and peripheral
- Automatic peer discovery and connection management
- Store-and-forward for offline message delivery
- Adaptive duty cycling for battery optimization

### Android-Specific Optimizations
- **Coroutine Architecture**: Asynchronous operations for mesh networking
- **Kotlin Coroutines**: Thread-safe concurrent mesh operations
- **EncryptedSharedPreferences**: Secure storage for user settings
- **Lifecycle-Aware**: Proper handling of Android app lifecycle
- **Battery Optimization**: Foreground service and adaptive scanning

## Android Technical Architecture

### Core Components

1. **SignaloutApplication.kt**: Application-level initialization and dependency injection
2. **MainActivity.kt**: Main activity handling permissions and UI hosting
3. **ChatViewModel.kt**: MVVM pattern managing app state and business logic
4. **BluetoothMeshService.kt**: Core BLE mesh networking (central + peripheral roles)
5. **EncryptionService.kt**: Cryptographic operations using BouncyCastle
6. **BinaryProtocol.kt**: Binary packet encoding/decoding matching iOS format
7. **ChatScreen.kt**: Jetpack Compose UI with Material Design 3

### Dependencies

- **Jetpack Compose**: Modern declarative UI
- **BouncyCastle**: Cryptographic operations (X25519, Ed25519, AES-GCM)
- **Nordic BLE Library**: Reliable Bluetooth LE operations
- **Kotlin Coroutines**: Asynchronous programming
- **LZ4**: Message compression (when enabled)
- **EncryptedSharedPreferences**: Secure local storage
- **WebRTC**: Voice and video calling

## Publishing to Google Play

### Preparation

1. **Update version information:**
   ```kotlin
   // In app/build.gradle.kts
   defaultConfig {
       versionCode = 2  // Increment for each release
       versionName = "1.1.0"  // User-visible version
   }
   ```

2. **Create a signed release build:**
   ```bash
   ./gradlew assembleRelease
   ```

3. **Generate app bundle (recommended for Play Store):**
   ```bash
   ./gradlew bundleRelease
   ```


## Future Implementations

1. **LoRA**: Planned support for long-range transmission of emergency messages using LoRA technology.
2. **AI Routing**: Using AI to route messages based on priority, urgency of message, availability of peers and network conditions through the mesh network.


---
*Created using Antigravity.*
