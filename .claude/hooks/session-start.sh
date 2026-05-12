#!/bin/bash
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# ── Android SDK ────────────────────────────────────────────────────────────────
ANDROID_SDK_DIR="$HOME/android-sdk"

if [ ! -d "$ANDROID_SDK_DIR/cmdline-tools" ]; then
  echo "Installing Android SDK command-line tools..."
  CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  TMPZIP="/tmp/cmdline-tools.zip"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$TMPZIP"
  mkdir -p "$ANDROID_SDK_DIR/cmdline-tools"
  unzip -q "$TMPZIP" -d "$ANDROID_SDK_DIR/cmdline-tools"
  # The zip extracts to "cmdline-tools/", rename to "latest"
  mv "$ANDROID_SDK_DIR/cmdline-tools/cmdline-tools" "$ANDROID_SDK_DIR/cmdline-tools/latest"
  rm "$TMPZIP"
fi

export ANDROID_HOME="$ANDROID_SDK_DIR"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# Accept licenses and install required SDK components
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" --sdk_root="$ANDROID_HOME" > /dev/null

# Persist ANDROID_HOME for the session
echo "export ANDROID_HOME=\"$ANDROID_HOME\"" >> "$CLAUDE_ENV_FILE"
echo "export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\"" >> "$CLAUDE_ENV_FILE"

# ── Gradle ─────────────────────────────────────────────────────────────────────
chmod +x ./gradlew

# Warm up Gradle dependency and task configuration cache (skip native Rust build)
./gradlew dependencies -PskipFilmrBuild=true --no-daemon -q
