#!/usr/bin/env bash
set -euo pipefail

# Capture before emulator-runner shuts the device down, including when a test fails.
collect_screenshots() {
    mkdir -p device-screenshots
    adb pull /sdcard/Android/data/io.github.homura251.webshortcuts/files/screenshots/. device-screenshots/ || true
}
trap collect_screenshots EXIT

./gradlew --no-daemon :app:connectedDebugAndroidTest
