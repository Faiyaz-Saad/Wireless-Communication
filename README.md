# Wireless Communication App

A cross-platform chat application that works without internet using local router/WiFi network. Built with Kotlin Multiplatform, Compose Multiplatform, and Ktor WebSocket.

## Features

- ✅ Cross-platform (Android + Desktop)
- ✅ Real-time messaging via WebSocket
- ✅ Image sharing with Base64 encoding
- ✅ Local network communication (no internet required)
- ✅ Shared UI code across platforms
- ✅ Auto-discovery ready (NSD implementation can be added)

## Project Structure

```
composeApp/src/
├── commonMain/kotlin/chat/
│   ├── model/Models.kt              # Chat data models
│   ├── transport/
│   │   ├── ChatTransport.kt         # Transport interface
│   │   └── KtorTransport.kt         # Ktor WebSocket implementation
│   ├── platform/PlatformHttpClient.kt # Platform-specific HTTP clients
│   └── ui/ChatScreen.kt             # Shared UI components
├── androidMain/kotlin/
│   ├── chat/platform/PlatformHttpClient.kt # Android HTTP client
│   └── org/example/project/MainActivity.kt # Android entry point
└── jvmMain/kotlin/
    ├── chat/
    │   ├── platform/PlatformHttpClient.kt # Desktop HTTP client
    │   └── server/LocalWebSocketServer.kt # WebSocket server
    └── org/example/project/Main.kt        # Desktop entry point
```

## How to Run

### Desktop (JVM)

1. Open terminal in project root
2. Run: `./gradlew :composeApp:run` (Linux/Mac) or `gradlew :composeApp:run` (Windows)
3. The app will start a local WebSocket server on port 8765
4. Connect to `127.0.0.1:8765` for local testing

### Android

1. Open project in Android Studio
2. Connect Android device or start AVD
3. Select `composeApp` configuration
4. Click Run
5. The app will connect to `127.0.0.1:8765` (ensure desktop server is running)

## Network Setup

### For Local Testing
- Desktop app automatically starts server on port 8765
- Android app connects to `127.0.0.1:8765`
- Both devices must be on same WiFi network

### For Real Wireless Communication
1. **Desktop as Host**: Run desktop app (starts server automatically)
2. **Find Desktop IP**: Check your computer's local IP (e.g., `192.168.1.100`)
3. **Android Connection**: Modify Android code to connect to desktop IP instead of `127.0.0.1`
4. **Multiple Devices**: All devices connect to the same host IP

## Usage

1. **Start Desktop App**: Automatically starts WebSocket server
2. **Start Android App**: Connects to desktop server
3. **Send Messages**: Type text and click "Send"
4. **Send Images**: Click "Image" button to select and send photos
5. **Real-time Chat**: Messages appear instantly on all connected devices

## Technical Details

- **WebSocket Server**: Ktor with CIO engine (Desktop)
- **WebSocket Client**: Ktor with platform-specific engines
- **Serialization**: Kotlinx Serialization (JSON)
- **UI**: Compose Multiplatform
- **Image Handling**: Base64 encoding (suitable for small images)
- **Networking**: Local network only (no internet required)

## Dependencies

- Compose Multiplatform 1.8.2
- Ktor 2.3.12 (Client + Server)
- Kotlinx Serialization 1.6.3
- Kotlinx Coroutines 1.10.2

## Future Enhancements

- [ ] NSD (Network Service Discovery) for auto-discovery
- [ ] Chunked image transfer for large files
- [ ] iOS support
- [ ] Message history persistence
- [ ] User authentication
- [ ] File sharing beyond images

## Troubleshooting

1. **Connection Issues**: Ensure both devices are on same WiFi network
2. **Firewall**: Check if Windows/Mac firewall blocks port 8765
3. **IP Address**: Use `ipconfig` (Windows) or `ifconfig` (Mac/Linux) to find correct IP
4. **Port Conflicts**: Change port in `LocalWebSocketServer(8765)` if needed

## Development Notes

- Image picker on Android uses `ActivityResultContracts.GetContent()`
- Desktop image picker uses `JFileChooser`
- WebSocket messages are JSON-serialized `Envelope` objects
- Server broadcasts messages to all connected clients
- UI is fully shared between Android and Desktop platforms