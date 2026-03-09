#!/bin/bash
# Download ADB binaries for Android from android-tools static builds
# These are ARM/x86 builds of adb that can run ON Android devices
#
# Usage: ./scripts/download_adb_binaries.sh
#
# The script downloads pre-built adb binaries and places them in
# app/src/main/assets/bin/<abi>/ for bundling with the APK.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_BIN="$PROJECT_DIR/app/src/main/assets/bin"

# android-tools version from Termux or static builds
# Update these URLs to point to your trusted binary source
ADB_VERSION="35.0.2"

# Architecture mapping
declare -A ABI_MAP=(
    ["arm64-v8a"]="aarch64"
    ["armeabi-v7a"]="arm"
    ["x86_64"]="x86_64"
    ["x86"]="i686"
)

echo "=== ADB Binary Download Script ==="
echo "Target: $ASSETS_BIN"
echo ""

# Create directory structure
for abi in "${!ABI_MAP[@]}"; do
    mkdir -p "$ASSETS_BIN/$abi"
done

# Check if binaries already exist
EXISTING=0
for abi in "${!ABI_MAP[@]}"; do
    if [ -f "$ASSETS_BIN/$abi/adb" ]; then
        EXISTING=$((EXISTING + 1))
        echo "[OK] $abi/adb exists ($(wc -c < "$ASSETS_BIN/$abi/adb") bytes)"
    fi
done

if [ "$EXISTING" -eq "${#ABI_MAP[@]}" ]; then
    echo ""
    echo "All binaries already present. Use --force to re-download."
    if [ "$1" != "--force" ]; then
        exit 0
    fi
fi

echo ""
echo "Downloading ADB binaries..."
echo "Note: You need to provide adb binaries compiled for Android."
echo ""
echo "Options to obtain Android-compatible adb binaries:"
echo "1. Build from AOSP source (most reliable)"
echo "2. Extract from Termux android-tools package:"
echo "   pkg install android-tools"
echo "   cp \$(which adb) ."
echo "3. Use pre-built static binaries from trusted GitHub repos"
echo ""

# Try to download from a common source
# Using Android SDK Platform-Tools won't work (those are for desktop OS)
# We need Android-native adb binaries

DOWNLOAD_BASE_URL="${ADB_DOWNLOAD_URL:-}"

if [ -n "$DOWNLOAD_BASE_URL" ]; then
    for abi in "${!ABI_MAP[@]}"; do
        arch="${ABI_MAP[$abi]}"
        target="$ASSETS_BIN/$abi/adb"
        url="$DOWNLOAD_BASE_URL/$arch/adb"
        echo "Downloading $abi ($arch) from $url ..."
        if curl -fsSL -o "$target" "$url"; then
            chmod +x "$target"
            echo "[OK] $abi/adb downloaded ($(wc -c < "$target") bytes)"
        else
            echo "[FAIL] Could not download $abi/adb"
        fi
    done
else
    echo "No ADB_DOWNLOAD_URL set. Attempting to use local Termux adb if available..."
    
    # If running on Android with Termux, try to copy the local adb
    if command -v adb &> /dev/null; then
        ADB_PATH="$(which adb)"
        ADB_ARCH="$(file "$ADB_PATH" 2>/dev/null | grep -oE 'ARM|aarch64|x86.64|80386' || echo "unknown")"
        echo "Found local adb: $ADB_PATH (arch: $ADB_ARCH)"
        
        case "$ADB_ARCH" in
            *aarch64*|*ARM*)
                cp "$ADB_PATH" "$ASSETS_BIN/arm64-v8a/adb"
                chmod +x "$ASSETS_BIN/arm64-v8a/adb"
                echo "[OK] Copied to arm64-v8a"
                ;;
            *x86.64*)
                cp "$ADB_PATH" "$ASSETS_BIN/x86_64/adb"
                chmod +x "$ASSETS_BIN/x86_64/adb"
                echo "[OK] Copied to x86_64"
                ;;
        esac
    else
        echo ""
        echo "=========================================="
        echo "  MANUAL SETUP REQUIRED"
        echo "=========================================="
        echo ""
        echo "Please place adb binaries manually:"
        echo "  $ASSETS_BIN/arm64-v8a/adb    (for 64-bit ARM devices)"
        echo "  $ASSETS_BIN/armeabi-v7a/adb  (for 32-bit ARM devices)"
        echo "  $ASSETS_BIN/x86_64/adb       (for x86_64 emulators)"
        echo ""
        echo "Or set ADB_DOWNLOAD_URL environment variable and re-run."
        echo ""
        
        # Create placeholder files so the build doesn't crash
        for abi in "${!ABI_MAP[@]}"; do
            if [ ! -f "$ASSETS_BIN/$abi/adb" ]; then
                touch "$ASSETS_BIN/$abi/.gitkeep"
            fi
        done
    fi
fi

echo ""
echo "=== Done ==="
echo "Binary status:"
for abi in "${!ABI_MAP[@]}"; do
    if [ -f "$ASSETS_BIN/$abi/adb" ] && [ -s "$ASSETS_BIN/$abi/adb" ]; then
        echo "  [OK] $abi/adb ($(wc -c < "$ASSETS_BIN/$abi/adb") bytes)"
    else
        echo "  [MISSING] $abi/adb"
    fi
done
