"""Verify the merged APK rather than relying on the source manifest alone."""
import os
from pathlib import Path
import subprocess

sdk = Path(os.environ["ANDROID_HOME"])
apk = Path("app/build/outputs/apk/debug/app-debug.apk")
permissions = subprocess.check_output(
    [str(sdk / "build-tools/35.0.0/aapt"), "dump", "permissions", str(apk)], text=True
)
print(permissions)
forbidden = (
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_AUDIO",
)
assert "android.permission.INTERNET" in permissions, "Network permission missing"
assert not any(name in permissions for name in forbidden), "A dependency added broad media/storage access"
