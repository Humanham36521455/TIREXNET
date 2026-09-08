#!/usr/bin/env bash
set -euo pipefail

# Build script for mirrlyengine native library and debug APK.
# Usage: run from the repository root (where mirrlyengine and V2rayNG folders are).
# Example: bash scripts/build_mirrly_and_apk.sh

# --- settings (edit if needed) ---
REPO_ROOT="$(pwd)"
MIRRLY_DIR="$REPO_ROOT/mirrlyengine"
ANDROID_APP_DIR="$REPO_ROOT/V2rayNG"
OUT_LIBS_DIR="$REPO_ROOT/libs"
NDK_VER="29.0.14206865"
ABIS=(arm64-v8a armeabi-v7a x86 x86_64)

# --- environment checks ---
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "ERROR: ANDROID_HOME or ANDROID_SDK_ROOT is not set. Set it to your Android SDK path."
  exit 1
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  echo "WARNING: ANDROID_NDK_HOME not set. Will try to use ANDROID_HOME/ndk/$NDK_VER if exists."
  if [ -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}/ndk/$NDK_VER" ]; then
    export ANDROID_NDK_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/ndk/$NDK_VER"
    echo "Set ANDROID_NDK_HOME=${ANDROID_NDK_HOME}"
  else
    echo "ERROR: ANDROID_NDK_HOME not set and NDK not found at expected path."
    exit 1
  fi
fi

if ! command -v rustup >/dev/null 2>&1; then
  echo "Rust not found. Installing rustup + stable toolchain..."
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
  source "$HOME/.cargo/env"
fi

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "Installing cargo-ndk..."
  cargo install cargo-ndk --locked
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "ERROR: adb not found in PATH. Install Android platform-tools and ensure 'adb' is available."
  exit 1
fi

# --- build native ---
echo "=== Building mirrlyengine (native) for ABIs: ${ABIS[*]} ==="
mkdir -p "$OUT_LIBS_DIR"
pushd "$MIRRLY_DIR" >/dev/null
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android || true
cargo ndk -t aarch64-linux-android -t armv7-linux-androideabi -t i686-linux-android -t x86_64-linux-android -o "$OUT_LIBS_DIR" build --release
popd >/dev/null

echo "Native build done. libs placed under: $OUT_LIBS_DIR"

# --- copy .so into jniLibs ---
echo "=== Copying .so into Android app jniLibs ==="
JNI_LIBS_DIR="$ANDROID_APP_DIR/app/src/main/jniLibs"
rm -rf "$JNI_LIBS_DIR"
mkdir -p "$JNI_LIBS_DIR"
for abi in "${ABIS[@]}"; do
  case "$abi" in
    arm64-v8a) so_src="$OUT_LIBS_DIR/aarch64-linux-android/release/libmirrlyengine.so" ;;
    armeabi-v7a) so_src="$OUT_LIBS_DIR/armv7-linux-androideabi/release/libmirrlyengine.so" ;;
    x86) so_src="$OUT_LIBS_DIR/i686-linux-android/release/libmirrlyengine.so" ;;
    x86_64) so_src="$OUT_LIBS_DIR/x86_64-linux-android/release/libmirrlyengine.so" ;;
    *) echo "Unknown abi $abi"; exit 1 ;;
  esac

  if [ -f "$so_src" ]; then
    mkdir -p "$JNI_LIBS_DIR/$abi"
    cp "$so_src" "$JNI_LIBS_DIR/$abi/libmirrlyengine.so"
    echo "Copied $so_src -> $JNI_LIBS_DIR/$abi/"
  else
    echo "WARNING: Expected .so not found for $abi: $so_src (continuing)"
  fi
done

# --- copy libs to app/libs (workflow style) ---
rm -rf "$ANDROID_APP_DIR/app/libs"
cp -r "$OUT_LIBS_DIR" "$ANDROID_APP_DIR/app/libs"

echo "Copied libs to $ANDROID_APP_DIR/app/libs"

# --- prepare local.properties ---
echo "sdk.dir=${ANDROID_HOME:-$ANDROID_SDK_ROOT}" > "$ANDROID_APP_DIR/local.properties"
echo "Wrote local.properties to $ANDROID_APP_DIR/local.properties"

# --- build and install debug apk ---
pushd "$ANDROID_APP_DIR" >/dev/null
chmod +x ./gradlew || true
./gradlew clean
./gradlew assembleDebug -x lint
./gradlew installDebug || true
popd >/dev/null

# --- verify .so in APK ---
APK_PATH="$(find $ANDROID_APP_DIR/app/build/outputs/apk -type f -name '*debug*.apk' | head -n 1 || true)"
if [ -n "$APK_PATH" ]; then
  echo "APK built at: $APK_PATH"
  echo "Contents of APK libs related to mirrlyengine:"
  unzip -l "$APK_PATH" | grep libmirrlyengine || true
else
  echo "WARNING: Could not find built debug APK under $ANDROID_APP_DIR/app/build/outputs/apk"
fi

# --- done ---
echo
echo "=== BUILD AND INSTALL FINISHED ==="
echo "If install succeeded, open the app on device and use menu: Import -> Mirrly TG Proxy to open the dialog."
echo "To stream logcat for NativeProxy messages (run in separate terminal):"
echo "  adb logcat | grep -i NativeProxy"
echo
echo "Done."
