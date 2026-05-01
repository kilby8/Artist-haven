# 📱 Artist Haven APK Download & Testing Guide

## Overview
This guide helps you serve the Artist Haven APK from your development machine to test on Android devices.

---

## 🚀 Quick Start

### Option 1: Use Python Server (Easiest)

1. **Build the APK:**
   ```bash
   # From project root
   ./gradlew assembleRelease
   ```

2. **Start the Server:**
   ```bash
   python3 apk_server.py
   # Or specify a port:
   python3 apk_server.py 8080
   ```

3. **Access from Your Phone:**
   - On your phone's browser, go to: `http://<your-computer-ip>:8000`
   - Find your computer's IP:
     - **Windows:** Run `ipconfig` in PowerShell
     - **Mac/Linux:** Run `ifconfig` or `hostname -I`
   - Download and install the APK

### Option 2: Use Local File Server

**Windows (PowerShell):**
```powershell
# Install dependencies if needed
pip install http-server

# Serve files
python -m http.server 8000
```

**Mac/Linux:**
```bash
python3 -m http.server 8000
```

### Option 3: Using Android Studio

1. Build > Build Bundle(s) / APK(s) > Build APK(s)
2. Connect device via USB
3. Locate APK at: `app/build/outputs/apk/release/app-release.apk`
4. Right-click APK in file explorer → Open with → Android Device (if available)

---

## 📋 Build Instructions

### Build Release APK (with optimization)
```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Build Debug APK (for development)
```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Install Directly to Connected Device
```bash
# Release build
./gradlew installRelease

# Debug build
./gradlew installDebug
```

### Clean Build
```bash
./gradlew clean assembleRelease
```

---

## 🔗 Server Setup Details

### Files Included
- **`APK_DOWNLOAD_SERVER.html`** - Beautiful download page
  - Shows app version and build info
  - Installation instructions
  - Download button with status feedback
  - Features list

- **`apk_server.py`** - Python HTTP server
  - Serves HTML and APK files
  - Handles `/apk/` route for downloads
  - Custom logging with timestamps
  - Path traversal protection

### Configuration

Edit `APK_DOWNLOAD_SERVER.html` to customize:

```javascript
const APK_CONFIG = {
    versionName: "1.0",      // Change to your version
    versionCode: 1,          // Increment after each build
    packageName: "com.artisthaven.app",
    apkPath: "/apk/app-release.apk",  // Path to APK file
    minApi: 26,
    targetApi: 34
};
```

### Port Configuration

Default port: `8000`

To use a different port:
```bash
python3 apk_server.py 8080
```

Then access: `http://your-ip:8080`

---

## 📱 Installation on Android Device

### Prerequisites
- Android 8.0+ (API 26+) - as per Artist Haven requirements
- USB Debugging enabled (for direct ADB install)
- OR: Manual installation from downloaded APK

### Method 1: Manual Download & Install

1. **Enable Unknown Sources:**
   - Settings → Apps & Notifications → Special Permissions → Install Unknown Apps
   - Select your browser and toggle ON

2. **Download APK:**
   - Open `http://your-computer-ip:8000` on your phone
   - Tap "Download APK for Testing"

3. **Install:**
   - Open Downloads folder
   - Tap the APK file
   - Tap "Install"
   - Wait for installation to complete

4. **Launch:**
   - Go to Apps
   - Find "Artist Haven"
   - Tap to launch

### Method 2: ADB Command Line Install

```bash
# Ensure device is connected via USB
adb devices

# Install APK
adb install -r app/build/outputs/apk/release/app-release.apk

# Or uninstall first, then install
adb uninstall com.artisthaven.app
adb install app/build/outputs/apk/release/app-release.apk
```

### Method 3: Android Studio Installation

1. Connect device via USB
2. Select "Run 'app'" in Android Studio
3. Select your device when prompted
4. APK builds and installs automatically

---

## 🔍 Troubleshooting

### APK Not Found Error
```
Solution: Build the APK first
./gradlew assembleRelease
```

### "Cannot Install from Unknown Source"
```
Solution: Enable Unknown Sources in Settings
Settings → Security → Unknown Sources (or Apps & Notifications → Special Permissions)
```

### Port Already in Use
```bash
# Error: Address already in use
# Solution: Use a different port
python3 apk_server.py 8080  # Try 8080, 9000, etc.
```

### Cannot Access from Another Device
```
1. Check firewall isn't blocking port 8000
2. Verify you're on the same network
3. Use the correct IP address:
   - Windows: ipconfig (look for IPv4 Address)
   - Mac/Linux: ifconfig (look for inet addr)
4. Try: http://192.168.x.x:8000 (not localhost)
```

### Installation Fails with "App Conflicts"
```bash
# Uninstall existing version first
adb uninstall com.artisthaven.app
adb install app/build/outputs/apk/release/app-release.apk
```

### Device Not Detected
```bash
# Check connection
adb devices

# Reconnect device and enable USB Debugging:
Settings → Developer Options → USB Debugging
```

---

## 📊 APK Details

- **Package Name:** `com.artisthaven.app`
- **Min SDK:** API 26 (Android 8.0)
- **Target SDK:** API 34 (Android 15)
- **Typical Size:** 15-30 MB (depending on resources)
- **Architecture:** Universal (arm64-v8a, armeabi-v7a)

---

## 🎯 Features Included in Latest Build

- ✨ 50+ Unique Brush Styles
- 🎨 Advanced Brush Library UI
- 📊 Real-time Pressure Sensitivity
- 🌈 Professional Shader Effects
- 🖼️ Layer Management System
- ↩️ Undo/Redo Support
- 💾 Project Save/Load
- 🎨 Color Picker
- 📱 Optimized for Stylus & Touch

---

## 📝 Testing Checklist

- [ ] APK builds without errors
- [ ] File size is reasonable (< 50MB)
- [ ] Installs on Android 8.0+
- [ ] App launches without crash
- [ ] Brush library loads
- [ ] Can select and use brushes
- [ ] Pressure sensitivity works (if stylus available)
- [ ] Layers can be created/deleted
- [ ] Undo/Redo functional
- [ ] Colors can be picked and applied
- [ ] Canvas is responsive

---

## 🔧 Development Workflow

### Typical Testing Loop:
1. Make code changes
2. Run: `./gradlew assembleRelease`
3. Uninstall old APK: `adb uninstall com.artisthaven.app`
4. Install new: `adb install app/build/outputs/apk/release/app-release.apk`
5. Test on device
6. Repeat

### Quick Scripts

**build_and_install.sh** (Mac/Linux):
```bash
#!/bin/bash
./gradlew assembleRelease && \
adb uninstall com.artisthaven.app && \
adb install app/build/outputs/apk/release/app-release.apk && \
echo "✓ Installation complete"
```

**build_and_install.bat** (Windows):
```batch
@echo off
call gradlew.bat assembleRelease
adb uninstall com.artisthaven.app
adb install app\build\outputs\apk\release\app-release.apk
echo Installation complete
```

---

## 🌐 Network Sharing

### Share Download Link via QR Code
Generate QR code for: `http://your-ip:8000`
- Use: https://qrcode.tec-it.com/
- Scan with phone to quickly access download

### Share via Text/Email
```
Download Artist Haven APK for testing:
http://192.168.1.100:8000
```

---

## 📚 Additional Resources

- [Android Developers - Build an APK](https://developer.android.com/studio/build/building-cmdline)
- [ADB Command Reference](https://developer.android.com/studio/command-line/adb)
- [Android SDK Platform Tools](https://developer.android.com/studio/releases/platform-tools)
- [Gradle Build Tool Documentation](https://docs.gradle.org/)

---

## ✅ Summary

You now have:
1. **HTML Download Page** - Beautiful UI for downloading APK
2. **Python Server** - Easy-to-use local HTTP server
3. **Build Instructions** - Multiple ways to build the APK
4. **Installation Guide** - Step-by-step for getting APK on device
5. **Troubleshooting** - Solutions for common issues

**Next Steps:**
1. Build: `./gradlew assembleRelease`
2. Server: `python3 apk_server.py`
3. Download: `http://your-computer-ip:8000`
4. Install and test! 🚀
