#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$(dirname "$0")/.."

if [ ! -f local.properties ]; then
  if [ -f "$HOME/projects/GPD/local.properties" ]; then
    cp "$HOME/projects/GPD/local.properties" ./local.properties
  else
    echo "local.properties not found. Copy your Android SDK local.properties into the project root."
    exit 1
  fi
fi

gradle --stop || true
rm -rf app/build build .gradle
gradle :app:assembleDebug --stacktrace

APK="$(find app/build/outputs/apk/debug -name '*.apk' | head -n 1)"
if [ -z "$APK" ]; then
  echo "APK not found"
  exit 1
fi

su -c "cp '$APK' /data/local/tmp/repoforge-debug.apk && chmod 644 /data/local/tmp/repoforge-debug.apk && pm install -r /data/local/tmp/repoforge-debug.apk && rm -f /data/local/tmp/repoforge-debug.apk"
am force-stop app.repoforge || true
monkey -p app.repoforge -c android.intent.category.LAUNCHER 1
