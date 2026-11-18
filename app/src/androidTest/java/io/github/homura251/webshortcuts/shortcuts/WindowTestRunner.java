package io.github.homura251.webshortcuts.shortcuts;

import android.content.ComponentName;
import android.os.ParcelFileDescriptor;
import androidx.test.runner.AndroidJUnitRunner;
import java.io.InputStream;

/** Register fixture filters normally at installation, then keep them off outside their own test. */
public class WindowTestRunner extends AndroidJUnitRunner {
    @Override public void onStart() {
        disableFixture(WindowBrowserFixture.class);
        disableFixture(WindowBrowserServiceFixture.class);
        super.onStart();
    }

    private void disableFixture(Class<?> type) {
        String component = new ComponentName(getContext(), type).flattenToString();
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                getUiAutomation().executeShellCommand("pm disable " + component))) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { /* Wait for package-manager state to be committed. */ }
        } catch (Exception failure) {
            throw new IllegalStateException("Could not disable the test browser fixture", failure);
        }
    }
}
