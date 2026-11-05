#!/usr/bin/env bash
set -euo pipefail

# Capture before emulator-runner shuts the device down, including when a test fails.
collect_screenshots() {
    mkdir -p device-screenshots
    adb pull /sdcard/Android/data/io.github.homura251.webshortcuts/files/screenshots/. device-screenshots/ || true
}
trap collect_screenshots EXIT

# UTP otherwise uninstalls the app (and its external files) before the EXIT trap can pull them.
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest
