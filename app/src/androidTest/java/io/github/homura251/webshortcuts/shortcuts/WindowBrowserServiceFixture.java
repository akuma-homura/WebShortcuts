package io.github.homura251.webshortcuts.shortcuts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class WindowBrowserServiceFixture extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
