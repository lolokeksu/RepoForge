#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$(dirname "$0")/.."

mkdir -p /sdcard/Download/RepoForge

gradle :app:assembleDebug --stacktrace

APK="$(find app/build/outputs/apk/debug -name '*.apk' | head -n 1)"
if [ -z "$APK" ]; then
  echo "APK not found"
  exit 1
fi

cp "$APK" /sdcard/Download/RepoForge/RepoForge-v2.0.0-debug.apk
echo "Exported: /sdcard/Download/RepoForge/RepoForge-v2.0.0-debug.apk"
