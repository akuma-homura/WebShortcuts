package io.github.homura251.webshortcuts.shortcuts

import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.annotation.OptIn
import androidx.browser.customtabs.ExperimentalMinimizationCallback
import java.time.Duration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@OptIn(ExperimentalMinimizationCallback::class)
class BookmarkTabSessionTest {
    @Test fun waitsForTheRestoredTabToRejoinItsTask() {
        val session = BookmarkTabSession()
        val task = ShadowAppTask.newInstance()
        val info = ActivityManager.RecentTaskInfo().apply { numActivities = 1 }
        val shadow = shadowOf(task)
        shadow.setTaskInfo(info)
        task.setExcludeFromRecents(true)

        session.callbackFor(task).onUnminimized(Bundle())
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(shadow.isExcludedFromRecents)

        info.numActivities = 2
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertFalse(shadow.isExcludedFromRecents)
    }

    @Test fun aReplacedSessionCannotRestoreTheCard() {
        val session = BookmarkTabSession()
        val task = ShadowAppTask.newInstance()
        val info = ActivityManager.RecentTaskInfo().apply { numActivities = 1 }
        val shadow = shadowOf(task)
        shadow.setTaskInfo(info)
        task.setExcludeFromRecents(true)
        val oldCallback = session.callbackFor(task)
        oldCallback.onUnminimized(Bundle())
        shadowOf(Looper.getMainLooper()).idle()

        session.reset()
        session.callbackFor(task)
        info.numActivities = 2
        oldCallback.onUnminimized(Bundle())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertTrue(shadow.isExcludedFromRecents)
    }
}
