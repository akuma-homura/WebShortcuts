package io.github.homura251.webshortcuts.shortcuts;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** A standalone test-APK browser; no app or Kotlin runtime dependencies in its own process. */
public class WindowBrowserFixture extends Activity {
    public static final String ACTION_RESUMED = "io.github.homura251.webshortcuts.test.WINDOW_RESUMED";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText(getIntent().getDataString());
        text.setId(12345);
        text.setContentDescription("Minimize fixture");
        text.setOnClickListener(view -> enterPictureInPictureMode(new PictureInPictureParams.Builder().build()));
        setContentView(text);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (!focused) return;
        sendBroadcast(new Intent(ACTION_RESUMED).setPackage("io.github.homura251.webshortcuts")
            .putExtra("taskId", getTaskId()).putExtra("url", getIntent().getDataString())
            .putExtra("customTab", getIntent().hasExtra("android.support.customtabs.extra.SESSION")));
    }
}
