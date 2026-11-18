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
adb root
adb wait-for-device
adb shell am set-debug-app --persistent com.android.chrome
# The bundled M124 predates complete field-trial rollout; enable the native implementation for CI.
adb shell 'echo "chrome --disable-fre --no-first-run --no-default-browser-check --enable-features=CCTMinimized --disable-low-end-device-mode" > /data/local/tmp/chrome-command-line'
adb shell am force-stop com.android.chrome
adb shell appops set com.android.chrome PICTURE_IN_PICTURE allow

# Cached flags take effect in the next Chrome process. Wait for native startup to persist
# the enabled value on this disposable, rootable google_apis emulator before restarting it.
adb shell am start -W -a android.intent.action.VIEW -d about:blank -n com.android.chrome/com.google.android.apps.chrome.Main
chrome_ready=false
for attempt in {1..60}; do
    if adb shell 'grep -l '\''CCTMinimized" value="true"'\'' /data/user/0/com.android.chrome/shared_prefs/*.xml' 2>/dev/null | grep -q .; then
        chrome_ready=true
        break
    fi
    sleep 1
done
if [[ "$chrome_ready" != true ]]; then
    echo 'Chrome did not initialize its native minimization feature cache' >&2
    exit 1
fi
adb shell am force-stop com.android.chrome

# UTP otherwise uninstalls the app (and its external files) before the EXIT trap can pull them.
./gradlew --daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest
