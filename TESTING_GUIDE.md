# Testing Guide for Android Host Fix

## What We Fixed
1. **Foreground Service**: Moved Ktor server to a foreground service with persistent notification
2. **Network Binding**: Server binds to `0.0.0.0` so other devices can connect  
3. **UDP Discovery**: Re-enabled broadcasting from the service using subnet broadcast address
4. **Permission Handling**: Added runtime permission requests for notifications and Wi-Fi
5. **Retry Logic**: Client connection retries 5 times to avoid race conditions
6. **Error Handling**: Better error messages and state management

## Testing Steps

### 1. Build and Install
```bash
# Try building with Android Studio or command line
./gradlew assembleDebug

# Or install directly if you have a device connected
./gradlew installDebug
```

### 2. Test Hosting (Single Device)
1. **Launch the app** on your Android device
2. **Tap "Host on this device"** button
3. **Grant permissions** when prompted:
   - POST_NOTIFICATIONS (Android 13+)
   - NEARBY_WIFI_DEVICES (Android 13+) or ACCESS_FINE_LOCATION (Android 12-)
4. **Expected behavior**:
   - App should NOT crash
   - Notification should appear: "Hosting chat server - Running on [LAN_IP]:9876"
   - UI should switch to chat screen
   - No "Failed to connect" error

### 3. Test Multi-Device Connection
1. **On second device**: Launch the app
2. **Wait for discovery**: Should automatically find the host device
3. **Or manually connect**: Enter the host IP shown in the notification
4. **Expected behavior**:
   - Both devices should connect to chat
   - Messages should sync between devices

### 4. Troubleshooting

#### If app still crashes on "Host" button:
- Check logcat for specific error messages
- Try on different Android version/device
- Some OEMs may block foreground services

#### If "Failed to connect" appears:
- Wait 2-3 seconds after tapping "Host"
- Check if notification shows correct IP
- Try restarting the app

#### If devices don't discover each other:
- Ensure both devices are on same Wi-Fi network
- Some routers block UDP broadcasts
- Use manual IP entry as fallback

## Key Files Modified
- `MainActivity.kt`: Permission handling, retry logic
- `HostService.kt`: Foreground service with UDP broadcasting  
- `AndroidManifest.xml`: Added required permissions

## Expected Results
✅ No crashes when pressing "Host on this device"
✅ Persistent notification with LAN IP
✅ Successful client connection with retry
✅ Multi-device discovery and connection
✅ Proper error handling and user feedback
