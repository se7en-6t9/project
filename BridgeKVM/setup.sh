#!/usr/bin/env bash
set -euo pipefail

# Headless Android SDK + Java 17 setup for Codespaces.

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

sudo apt-get update -y
sudo apt-get install -y --no-install-recommends \
  openjdk-17-jdk \
  wget \
  unzip \
  ca-certificates \
  libstdc++6 \
  libgcc-s1 \
  libc6 \
  zlib1g \
  libncurses6 \
  libtinfo6 \
  libssl3

mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

if [[ ! -d "$ANDROID_SDK_ROOT/cmdline-tools/latest" ]]; then
  tmpdir="$(mktemp -d)"
  wget -q "$CMDLINE_TOOLS_URL" -O "$tmpdir/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmpdir/$CMDLINE_TOOLS_ZIP" -d "$tmpdir"
  mv "$tmpdir/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmpdir"
fi

export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

yes | sdkmanager --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null

sdkmanager --sdk_root="$ANDROID_SDK_ROOT" \
  "platform-tools" \
  "build-tools;34.0.0" \
  "platforms;android-34"

# Persist env for future shells.
if ! grep -q "ANDROID_SDK_ROOT" "$HOME/.bashrc"; then
  {
    echo "export ANDROID_SDK_ROOT=\"$ANDROID_SDK_ROOT\""
    echo "export ANDROID_HOME=\"$ANDROID_SDK_ROOT\""
    echo "export JAVA_HOME=\"/usr/lib/jvm/java-17-openjdk-amd64\""
    echo "export PATH=\"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:\$PATH\""
  } >> "$HOME/.bashrc"
fi

# Optional: verify Gradle wrapper sees Java 17.
if [[ -f "./android/gradlew" ]]; then
  ./android/gradlew --version || true
fi

echo "Android SDK installed at $ANDROID_SDK_ROOT"