# 🚀 Artist Haven APK Download - Quick Reference

## ⚡ Fastest Way to Start

### Windows
```powershell
.\apk_build_and_serve.bat
```

### macOS/Linux
```bash
chmod +x apk_build_and_serve.sh
./apk_build_and_serve.sh
```

---

## 📱 What You Get

1. **APK_DOWNLOAD_SERVER.html** - Beautiful download page
   - Shows version info
   - Download button
   - Installation instructions
   - Features list

2. **apk_server.py** - Python HTTP server
   - Serves APK files
   - Lightweight and cross-platform

3. **Helper Scripts**
   - `apk_build_and_serve.bat` - Windows automation
   - `apk_build_and_serve.sh` - Mac/Linux automation

4. **APK_DOWNLOAD_GUIDE.md** - Complete documentation

---

## 🎯 Usage

### Step 1: Build & Start Server
```bash
# Windows
.\apk_build_and_serve.bat

# Mac/Linux
./apk_build_and_serve.sh
```

### Step 2: Open on Phone
1. Connect phone to same WiFi as your computer
2. Open browser and go to: `http://<your-computer-ip>:8000`
3. Click "Download APK for Testing"
4. Install and launch

### Step 3: Test on Device
- Open Artist Haven
- Test brush library
- Try drawing
- Test features

---

## 🔧 Manual Commands

### Build Only
```bash
./gradlew assembleRelease
```

### Start Server Only
```bash
python3 apk_server.py
# Or with custom port:
python3 apk_server.py 8080
```

### Direct Install (ADB)
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📋 Keyboard Shortcuts

On the download page:
- Press **D** to download APK
- Press **B** to build locally

---

## 🔍 Troubleshooting

| Problem | Solution |
|---------|----------|
| "Python not found" | Install Python from python.org |
| "Port already in use" | Use different port: `apk_build_and_serve.bat 8080` |
| "Cannot access from phone" | Check firewall, verify WiFi connection |
| "APK not found" | Run build first: `./gradlew assembleRelease` |
| "Installation fails" | Uninstall first: `adb uninstall com.artisthaven.app` |

---

## 📊 Key Information

- **Package:** `com.artisthaven.app`
- **Min Android:** 8.0 (API 26)
- **Target Android:** 15 (API 34)
- **Typical Size:** 15-30 MB

---

## ✨ Features to Test

- [ ] 50+ brush library loads
- [ ] Brush selection works
- [ ] Pressure sensitivity (with stylus)
- [ ] Layer management
- [ ] Undo/Redo
- [ ] Color picker
- [ ] Drawing feels smooth
- [ ] No crashes on startup

---

## 🌐 Network Access

### Find Your Computer's IP

**Windows:**
```powershell
ipconfig
# Look for "IPv4 Address" like 192.168.1.100
```

**Mac/Linux:**
```bash
ifconfig
# Look for "inet" address like 192.168.1.100
```

Then on phone: `http://192.168.1.100:8000`

---

## 💡 Pro Tips

1. **Auto-refresh**: Browser will auto-check APK availability
2. **QR Code**: Generate QR for http://your-ip:8000 to share
3. **Status Messages**: Page shows if APK is ready or not
4. **File Size**: Automatically displays after download check
5. **Last Updated**: Shows build timestamp

---

## 📞 Support

Check **APK_DOWNLOAD_GUIDE.md** for detailed troubleshooting and advanced options.

Need help? Run the helper scripts with verbose output to debug.

---

**Ready to test?** Run: `./apk_build_and_serve.bat` or `./apk_build_and_serve.sh`
