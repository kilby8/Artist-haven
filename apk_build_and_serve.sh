#!/bin/bash

# Artist Haven APK Server Launcher for macOS/Linux
# This script builds the APK and starts the download server

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print header
echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║       Artist Haven - APK Build and Server Launcher            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Error: Python 3 is not installed${NC}"
    echo ""
    echo "Please install Python 3:"
    echo "  macOS (with Homebrew): brew install python3"
    echo "  Ubuntu/Debian: sudo apt-get install python3"
    echo "  Or download from: https://www.python.org/"
    exit 1
fi

# Check if gradlew exists
if [ ! -f "gradlew" ]; then
    echo -e "${RED}❌ Error: gradlew not found in current directory${NC}"
    echo "Please run this script from the project root"
    exit 1
fi

# Get port from argument or use default
PORT=${1:-8000}

# Check if port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${YELLOW}⚠️  Port $PORT is already in use${NC}"
    echo "Try a different port: $0 8080"
    exit 1
fi

echo -e "${BLUE}📋 Step 1: Building Release APK...${NC}"
echo ""

# Build the APK
if ! ./gradlew assembleRelease; then
    echo ""
    echo -e "${RED}❌ Build failed. Please check the errors above.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✓ APK built successfully!${NC}"
echo ""

# Check if APK exists
APK_PATH="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}❌ Error: APK not found at $APK_PATH${NC}"
    exit 1
fi

# Get file size
SIZE=$(du -h "$APK_PATH" | cut -f1)
echo -e "${GREEN}📦 APK ready: $APK_PATH (${SIZE})${NC}"

echo ""
echo -e "${BLUE}🚀 Step 2: Starting Download Server...${NC}"
echo ""

# Get local IP address
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS
    IP=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -n 1)
else
    # Linux
    IP=$(hostname -I | awk '{print $1}')
fi

# Display server info
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║           Artist Haven APK Download Server                      ║"
echo "╠════════════════════════════════════════════════════════════════╣"
echo "║                                                                 ║"
echo "║  ✓ Server starting on port $PORT"
echo "║                                                                 ║"
echo "║  📱 From your phone, open:                                     ║"
echo "║     http://$IP:$PORT"
echo "║                                                                 ║"
echo "║  💻 Or from this computer:                                     ║"
echo "║     http://localhost:$PORT                                     ║"
echo "║                                                                 ║"
echo "║  📝 Make sure phone is on the same WiFi network                ║"
echo "║                                                                 ║"
echo "║  Press Ctrl+C to stop the server                               ║"
echo "║                                                                 ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

sleep 2

# Start the server
python3 apk_server.py $PORT
