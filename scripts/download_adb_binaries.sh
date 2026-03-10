#!/bin/bash
# Download pre-compiled static ADB binaries for Android
#
# Binaries are placed in jniLibs/<abi>/libadb.so so that Android's
# PackageManager extracts them to nativeLibraryDir (which has exec permission).
# This avoids the W^X / noexec restriction on app private directories (Android 10+).
#
# Usage:
#   ./scripts/download_adb_binaries.sh              # download all ABIs
#   ./scripts/download_adb_binaries.sh --force       # re-download even if present
#   ./scripts/download_adb_binaries.sh --arm64-only   # arm64 only (most devices)
#
# Sources (in priority order):
#   1. ADB_DOWNLOAD_URL env var  (custom URL: $URL/adb-{aarch64,arm,x86_64})
#   2. GitHub Release (ADB_GITHUB_REPO + ADB_RELEASE_TAG)
#   3. Local adb binary (Termux)
#   4. Manual placement instructions

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JNILIBS="$PROJECT_DIR/app/src/main/jniLibs"

ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")
FORCE=false

# Map Android ABI -> release file architecture suffix
abi_to_arch() {
    case "$1" in
        "arm64-v8a")   echo "aarch64" ;;
        "armeabi-v7a") echo "arm" ;;
        "x86_64")      echo "x86_64" ;;
        "x86")         echo "i686" ;;
        *)             echo "$1" ;;
    esac
}

for arg in "$@"; do
    case "$arg" in
        --force) FORCE=true ;;
        --arm64-only) ABIS=("arm64-v8a") ;;
    esac
done

echo "=== ADB Binary Download Script ==="
echo "Target: $JNILIBS/<abi>/libadb.so"
echo ""

# Create directory structure
for abi in "${ABIS[@]}"; do
    mkdir -p "$JNILIBS/$abi"
done

# Check existing binaries
check_existing() {
    local count=0
    for abi in "${ABIS[@]}"; do
        local f="$JNILIBS/$abi/libadb.so"
        if [ -f "$f" ] && [ -s "$f" ]; then
            local size=$(wc -c < "$f")
            if [ "$size" -gt 100000 ]; then
                count=$((count + 1))
                echo "  [OK] $abi/libadb.so ($size bytes)"
            else
                echo "  [BAD] $abi/libadb.so too small ($size bytes)"
            fi
        else
            echo "  [MISSING] $abi/libadb.so"
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
        local target="$JNILIBS/$abi/libadb.so"
        local arch=$(abi_to_arch "$abi")
        local url="$base_url/adb-$arch"
        echo -n "  $abi ($arch) ... "
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
        local target="$JNILIBS/$abi/libadb.so"
        local arch=$(abi_to_arch "$abi")
        local url="$base/adb-$arch"
        echo -n "  $abi ($arch) ... "
        if curl -fsSL -L --connect-timeout 15 --max-time 120 -o "$target" "$url" 2>/dev/null; then
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
        cp "$adb_path" "$JNILIBS/$target_abi/libadb.so"
        chmod +x "$JNILIBS/$target_abi/libadb.so"
        echo "  Copied to $target_abi/libadb.so"
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
    local_f="$JNILIBS/$abi/libadb.so"
    if [ -f "$local_f" ] && [ -s "$local_f" ]; then
        local_size=$(wc -c < "$local_f")
        if [ "$local_size" -gt 100000 ]; then
            echo "  [OK] $abi/libadb.so ($local_size bytes)"
        else
            echo "  [BAD] $abi/libadb.so ($local_size bytes - too small)"
            ALL_OK=false
        fi
    else
        echo "  [MISSING] $abi/libadb.so"
        ALL_OK=false
    fi
done

if [ "$ALL_OK" = false ]; then
    echo ""
    echo "=========================================="
    echo "  MISSING ADB BINARIES"
    echo "=========================================="
    echo ""
    echo "ADB binaries must be compiled for Android (ARM64/ARM/x86_64)."
    echo "They are named libadb.so and placed in jniLibs/ so Android"
    echo "extracts them to nativeLibraryDir (which has exec permission)."
    echo ""
    echo "How to obtain them:"
    echo ""
    echo "  Option 1: Set download URL"
    echo "    ADB_DOWNLOAD_URL=https://your-server/bins \\"
    echo "      ./scripts/download_adb_binaries.sh"
    echo "    (expects files: \$URL/adb-aarch64, adb-arm, adb-x86_64)"
    echo ""
    echo "  Option 2: Set GitHub repo with releases"
    echo "    ADB_GITHUB_REPO=mofanx/adb ADB_RELEASE_TAG=v1.0 \\"
    echo "      ./scripts/download_adb_binaries.sh"
    echo "    (expects release assets: adb-aarch64, adb-arm, adb-x86_64)"
    echo ""
    echo "  Option 3: Extract from Termux (on Android device)"
    echo "    pkg install android-tools"
    echo "    cp \$(which adb) <project>/app/src/main/jniLibs/arm64-v8a/libadb.so"
    echo ""
    echo "  Option 4: Place binaries manually"
    for abi in "${ABIS[@]}"; do
        echo "    $JNILIBS/$abi/libadb.so"
    done
    echo ""
    exit 1
fi

echo ""
echo "All binaries ready for APK bundling (jniLibs -> nativeLibraryDir)."
