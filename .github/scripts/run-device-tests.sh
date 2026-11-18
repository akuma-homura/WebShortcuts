#!/usr/bin/env bash
set -euo pipefail

# Capture before emulator-runner shuts the device down, including when a test fails.
collect_screenshots() {
    mkdir -p device-screenshots
    adb pull /sdcard/Android/data/io.github.homura251.webshortcuts/files/screenshots/. device-screenshots/ || true
    adb shell am clear-debug-app || true
    adb shell rm -f /data/local/tmp/chrome-command-line || true
}
trap collect_screenshots EXIT

# Exercise the real Chrome CCT controls without the emulator's first-run onboarding.
adb shell am set-debug-app --persistent com.android.chrome
adb shell 'echo "chrome --disable-fre --no-first-run --no-default-browser-check" > /data/local/tmp/chrome-command-line'
adb shell am force-stop com.android.chrome
adb shell appops set com.android.chrome PICTURE_IN_PICTURE allow

# UTP otherwise uninstalls the app (and its external files) before the EXIT trap can pull them.
./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest
