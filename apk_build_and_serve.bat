@echo off
REM Artist Haven APK Server Launcher for Windows
REM This script builds the APK and starts the download server

setlocal enabledelayedexpansion

REM Colors for output (Windows 10+)
for /F %%A in ('echo prompt $H ^| cmd') do set "BS=%%A"

echo.
echo ╔════════════════════════════════════════════════════════════════╗
echo ║       Artist Haven - APK Build and Server Launcher            ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

REM Check if Python is installed
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Error: Python is not installed or not in PATH
    echo.
    echo Please install Python from: https://www.python.org/
    echo Make sure to check "Add Python to PATH" during installation
    pause
    exit /b 1
)

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ❌ Error: gradlew.bat not found in current directory
    echo Please run this script from the project root
    pause
    exit /b 1
)

REM Get port from argument or use default
set PORT=8000
if not "%1"=="" (
    set PORT=%1
)

echo 📋 Step 1: Building Release APK...
echo.
call gradlew.bat assembleRelease
if errorlevel 1 (
    echo.
    echo ❌ Build failed. Please check the errors above.
    pause
    exit /b 1
)

echo.
echo ✓ APK built successfully!
echo.

REM Check if APK exists
set APK_PATH=app\build\outputs\apk\release\app-release.apk
if not exist "%APK_PATH%" (
    echo ❌ Error: APK not found at %APK_PATH%
    pause
    exit /b 1
)

REM Get file size
for /F %%A in ('dir "%APK_PATH%" ^| find "app-release.apk"') do (
    echo 📦 APK ready: %%A
)

echo.
echo 🚀 Step 2: Starting Download Server...
echo.

REM Get local IP address
for /f "tokens=2 delims=:" %%A in ('ipconfig ^| find "IPv4"') do (
    set IP=%%A
)
set IP=%IP: =%

echo ╔════════════════════════════════════════════════════════════════╗
echo ║           Artist Haven APK Download Server                      ║
echo ╠════════════════════════════════════════════════════════════════╣
echo ║                                                                 ║
echo ║  ✓ Server will start on port %PORT%
echo ║                                                                 ║
echo ║  📱 From your phone, open:                                     ║
echo ║     http://%IP%:%PORT%
echo ║                                                                 ║
echo ║  💻 Or from this computer:                                     ║
echo ║     http://localhost:%PORT%                                    ║
echo ║                                                                 ║
echo ║  📝 Make sure phone is on the same WiFi network                ║
echo ║                                                                 ║
echo ║  Press Ctrl+C to stop the server                               ║
echo ║                                                                 ║
echo ╚════════════════════════════════════════════════════════════════╝
echo.

timeout /t 3

python apk_server.py %PORT%

if errorlevel 1 (
    echo.
    echo ❌ Server failed to start
    echo Try a different port: apk_build_and_serve.bat 8080
    pause
)
