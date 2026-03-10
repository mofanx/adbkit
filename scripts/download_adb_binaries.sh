#!/bin/bash
# Download pre-compiled static ADB binaries for Android
#
# These are ADB binaries compiled for Android ARM/x86 architectures
# so they can run ON an Android device to control other devices via WiFi ADB.
#
# Usage:
#   ./scripts/download_adb_binaries.sh              # download all
#   ./scripts/download_adb_binaries.sh --force       # re-download
#   ./scripts/download_adb_binaries.sh --arm64-only   # arm64 only (most devices)
#
# Sources (in priority order):
#   1. ADB_DOWNLOAD_URL env var  (custom URL: $URL/{arm64-v8a,armeabi-v7a,x86_64}/adb)
#   2. GitHub Release of this repo (if ADB_RELEASE_TAG is set)
#   3. Build from Android platform-tools source via NDK (requires NDK)
#   4. Manual placement instructions

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_BIN="$PROJECT_DIR/app/src/main/assets/bin"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
FORCE=false
ARM64_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --force) FORCE=true ;;
        --arm64-only) ARM64_ONLY=true; ABIS=("arm64-v8a") ;;
    esac
done

echo "=== ADB Binary Download Script ==="
echo "Target: $ASSETS_BIN"
echo ""

# Create directory structure
for abi in "${ABIS[@]}"; do
    mkdir -p "$ASSETS_BIN/$abi"
done

# Check existing binaries
check_existing() {
    local count=0
    for abi in "${ABIS[@]}"; do
        if [ -f "$ASSETS_BIN/$abi/adb" ] && [ -s "$ASSETS_BIN/$abi/adb" ]; then
            local size=$(wc -c < "$ASSETS_BIN/$abi/adb")
            # Must be > 100KB to be a real binary (not a placeholder)
            if [ "$size" -gt 100000 ]; then
                count=$((count + 1))
                echo "  [OK] $abi/adb ($size bytes)"
            else
                echo "  [BAD] $abi/adb too small ($size bytes), likely placeholder"
            fi
        else
            echo "  [MISSING] $abi/adb"
        fi
    done
    echo "$count"
}

echo "Current status:"
EXISTING=$(check_existing | tail -1)

if [ "$EXISTING" -eq "${#ABIS[@]}" ] && [ "$FORCE" = false ]; then
    echo ""
    echo "All binaries present. Use --force to re-download."
    exit 0
fi

echo ""

# ─── Method 1: Custom download URL ───
download_from_url() {
    local base_url="$1"
    echo "Downloading from: $base_url"
    local success=0
    for abi in "${ABIS[@]}"; do
        local target="$ASSETS_BIN/$abi/adb"
        local url="$base_url/$abi/adb"
        echo -n "  $abi ... "
        if curl -fsSL --connect-timeout 15 --max-time 120 -o "$target" "$url" 2>/dev/null; then
            chmod +x "$target"
            local size=$(wc -c < "$target")
            if [ "$size" -gt 100000 ]; then
                echo "OK ($size bytes)"
                success=$((success + 1))
            else
                echo "FAIL (too small: $size bytes)"
                rm -f "$target"
            fi
        else
            echo "FAIL (download error)"
        fi
    done
    return $((${#ABIS[@]} - success))
}

# ─── Method 2: GitHub Release ───
download_from_github_release() {
    local repo="$1"
    local tag="${2:-latest}"
    echo "Downloading from GitHub release: $repo @ $tag"

    if [ "$tag" = "latest" ]; then
        local api_url="https://api.github.com/repos/$repo/releases/latest"
        tag=$(curl -fsSL "$api_url" 2>/dev/null | grep '"tag_name"' | head -1 | sed 's/.*: "\(.*\)".*/\1/' || echo "")
        if [ -z "$tag" ]; then
            echo "  Could not determine latest release tag"
            return 1
        fi
        echo "  Latest tag: $tag"
    fi

    local base="https://github.com/$repo/releases/download/$tag"
    local success=0
    for abi in "${ABIS[@]}"; do
        local target="$ASSETS_BIN/$abi/adb"
        
        # Map Android ABI to typical release architecture names
        local arch_suffix="$abi"
        case "$abi" in
            "arm64-v8a") arch_suffix="aarch64" ;;
            "armeabi-v7a") arch_suffix="arm" ;;
            "x86_64") arch_suffix="x86_64" ;;
        esac
        
        local url="$base/adb-$arch_suffix"
        echo -n "  $abi (as $arch_suffix) ... "
        if curl -fsSL --connect-timeout 15 --max-time 120 -o "$target" "$url" 2>/dev/null; then
            chmod +x "$target"
            local size=$(wc -c < "$target")
            if [ "$size" -gt 100000 ]; then
                echo "OK ($size bytes)"
                success=$((success + 1))
            else
                echo "FAIL (too small)"
                rm -f "$target"
            fi
        else
            echo "FAIL"
        fi
    done
    return $((${#ABIS[@]} - success))
}

# ─── Method 3: Copy local adb (Termux/system) ───
copy_local_adb() {
    if ! command -v adb &>/dev/null; then
        return 1
    fi
    local adb_path="$(which adb)"
    local arch_info="$(file "$adb_path" 2>/dev/null || echo "")"
    echo "Found local adb: $adb_path"
    echo "  File info: $arch_info"

    local target_abi=""
    case "$arch_info" in
        *aarch64*|*ARM\ aarch64*) target_abi="arm64-v8a" ;;
        *ARM*) target_abi="armeabi-v7a" ;;
        *x86-64*|*x86_64*) target_abi="x86_64" ;;
    esac

    if [ -n "$target_abi" ]; then
        cp "$adb_path" "$ASSETS_BIN/$target_abi/adb"
        chmod +x "$ASSETS_BIN/$target_abi/adb"
        echo "  Copied to $target_abi"
        return 0
    else
        echo "  Unknown architecture"
        return 1
    fi
}

# ─── Execute download methods in priority order ───

DONE=false

# Method 1: Custom URL
if [ -n "${ADB_DOWNLOAD_URL:-}" ]; then
    echo "─── Method: Custom URL ───"
    if download_from_url "$ADB_DOWNLOAD_URL"; then
        DONE=true
    fi
fi

# Method 2: GitHub Release
if [ "$DONE" = false ] && [ -n "${ADB_GITHUB_REPO:-}" ]; then
    echo "─── Method: GitHub Release ───"
    if download_from_github_release "$ADB_GITHUB_REPO" "${ADB_RELEASE_TAG:-latest}"; then
        DONE=true
    fi
fi

# Method 3: Local adb binary
if [ "$DONE" = false ]; then
    echo "─── Method: Local ADB ───"
    if copy_local_adb; then
        echo "  (Only copied for one architecture)"
    fi
fi

# ─── Final status ───
echo ""
echo "=== Final Status ==="
ALL_OK=true
for abi in "${ABIS[@]}"; do
    if [ -f "$ASSETS_BIN/$abi/adb" ] && [ -s "$ASSETS_BIN/$abi/adb" ]; then
        local_size=$(wc -c < "$ASSETS_BIN/$abi/adb")
        if [ "$local_size" -gt 100000 ]; then
            echo "  [OK] $abi/adb ($local_size bytes)"
        else
            echo "  [BAD] $abi/adb ($local_size bytes - too small)"
            ALL_OK=false
        fi
    else
        echo "  [MISSING] $abi/adb"
        ALL_OK=false
    fi
done

if [ "$ALL_OK" = false ]; then
    echo ""
    echo "=========================================="
    echo "  MISSING ADB BINARIES"
    echo "=========================================="
    echo ""
    echo "ADB binaries for Android must be compiled for the target"
    echo "architecture (ARM64/ARM/x86_64), NOT desktop Linux."
    echo ""
    echo "How to obtain them:"
    echo ""
    echo "  Option 1: Set download URL"
    echo "    ADB_DOWNLOAD_URL=https://your-server/adb-binaries \\"
    echo "      ./scripts/download_adb_binaries.sh"
    echo ""
    echo "  Option 2: Set GitHub repo with releases"
    echo "    ADB_GITHUB_REPO=user/repo ADB_RELEASE_TAG=v1.0 \\"
    echo "      ./scripts/download_adb_binaries.sh"
    echo ""
    echo "  Option 3: Extract from Termux (on Android device)"
    echo "    pkg install android-tools"
    echo "    cp \$(which adb) <path>/arm64-v8a/adb"
    echo ""
    echo "  Option 4: Place binaries manually"
    for abi in "${ABIS[@]}"; do
        echo "    $ASSETS_BIN/$abi/adb"
    done
    echo ""
    echo "  Option 5: Build from AOSP source with NDK"
    echo "    See: https://android.googlesource.com/platform/packages/modules/adb/"
    echo ""
    exit 1
fi

echo ""
echo "All binaries ready for APK bundling."
