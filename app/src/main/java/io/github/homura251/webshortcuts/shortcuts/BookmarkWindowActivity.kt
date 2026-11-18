package io.github.homura251.webshortcuts.shortcuts

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.icons.IconResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Keeps the browser's Custom Tab above a bookmark-specific document root, never above MainActivity. */
class BookmarkWindowActivity : ComponentActivity() {
    private var routingJob: Job? = null
    private var returnJob: Job? = null
    private var browserOpened = false
    private var hasHostedTab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserOpened = savedInstanceState?.getBoolean(STATE_BROWSER_OPENED) == true
        hasHostedTab = browserOpened || savedInstanceState?.getBoolean(STATE_HAS_HOSTED_TAB) == true
        // Recreating an Activity clears its task description. Refresh the artwork while leaving
        // an already hosted browser alone, including when it is floating in another task.
        route(intent, launchBrowser = !browserOpened)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // intoExisting clears the previous tab above this root. Resolve fresh metadata and open
        // the saved address again, including URL or browser edits made since the last visit.
        route(intent)
    }

    override fun onPostResume() {
        super.onPostResume()
        // Removing a PiP parent task also removes its floating child. Keep the empty anchor
        // reusable but hide its card and move it behind the underlying app instead.
        if (!browserOpened) return
        returnJob?.cancel()
        returnJob = lifecycleScope.launch {
            val fastPollUntil = SystemClock.uptimeMillis() + 500
            // The system can resume this Activity before publishing the detached tab's task info.
            while (!isEmptyHost() || !hideHost()) {
                delay(if (SystemClock.uptimeMillis() < fastPollUntil) 16 else 250)
            }
        }
    }

    private fun setHostExcluded(excluded: Boolean) {
        try {
            getSystemService(ActivityManager::class.java).appTasks.firstOrNull { task ->
                runCatching { task.taskInfo?.baseIntent?.filterEquals(intent) == true }.getOrDefault(false)
            }?.setExcludeFromRecents(excluded)
        } catch (failure: Exception) {
            Log.w("BookmarkWindow", "Could not update Recents visibility: ${failure.javaClass.simpleName}")
        }
    }

    private fun hideHost(): Boolean {
        if (!moveTaskToBack(true)) return false
        setHostExcluded(true)
        return true
    }

    private fun closeHost() {
        if (hasHostedTab) hideHost() else finishAndRemoveTask()
    }

    private fun isEmptyHost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return getSystemService(ActivityManager::class.java).appTasks.any { task ->
            val info = runCatching { task.taskInfo }.getOrNull()
            info != null && info.taskId == taskId && info.numActivities == 1
        }
    }

    override fun onPause() {
        returnJob?.cancel()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_BROWSER_OPENED, browserOpened)
        outState.putBoolean(STATE_HAS_HOSTED_TAB, hasHostedTab)
        super.onSaveInstanceState(outState)
    }

    private fun route(incoming: Intent, launchBrowser: Boolean = true) {
        returnJob?.cancel()
        routingJob?.cancel()
        if (launchBrowser) browserOpened = false
        routingJob = lifecycleScope.launch {
            try {
                val id = incoming.getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID)
                if (incoming.action != ShortcutIntents.ACTION_OPEN_BOOKMARK || id.isNullOrBlank() || id.length > 128) {
                    if (launchBrowser) fail("快捷方式无效，请在 WebShortcuts 中重新添加")
                    return@launch
                }
                val container = (application as WebShortcutsApplication).container
                val bookmark = container.repository.find(id)
                ensureActive()
                if (bookmark == null || bookmark.isLocal) {
                    if (launchBrowser) fail("此网页收藏已变更，请在 WebShortcuts 中重新打开")
                    return@launch
                }
                if (launchBrowser) {
                    val result = BrowserLauncher.openInBookmarkTask(this@BookmarkWindowActivity, bookmark)
                    if (!result.customTab) {
                        if (result.opened) closeHost() else fail(result.message ?: "暂时无法打开收藏")
                        return@launch
                    }
                    browserOpened = true
                    hasHostedTab = true
                    setHostExcluded(false)
                }
                refreshTaskDescription(bookmark, container.iconResolver)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("BookmarkWindow", "Window launch failed: ${failure.javaClass.simpleName}")
                if (launchBrowser) fail("暂时无法打开收藏，请打开 WebShortcuts 检查本地数据")
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun refreshTaskDescription(bookmark: BookmarkItem, resolver: IconResolver) {
        try {
            val artwork = resolver.resolve(bookmark)
            coroutineContext.ensureActive()
            setBookmarkTaskDescription(bookmark.title, artwork)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("BookmarkWindow", "Could not update task artwork: ${failure.javaClass.simpleName}")
            coroutineContext.ensureActive()
            runCatching { setTaskDescription(ActivityManager.TaskDescription(bookmark.title)) }
        }
    }

    @Suppress("DEPRECATION")
    private fun setBookmarkTaskDescription(title: String, artwork: Bitmap) {
        // TaskDescription accepts a bitmap on older Android versions. Render the same system
        // mask used by adaptive shortcut icons before handing that bitmap to Recents.
        val size = minOf(artwork.width, artwork.height, 192)
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        AdaptiveIconDrawable(null, BitmapDrawable(resources, artwork)).apply {
            setBounds(0, 0, size, size)
            draw(Canvas(icon))
        }
        setTaskDescription(ActivityManager.TaskDescription(title, icon))
    }

    private fun fail(message: String) {
        Log.w("BookmarkWindow", message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        closeHost()
    }

    private companion object {
        const val STATE_BROWSER_OPENED = "browserOpened"
        const val STATE_HAS_HOSTED_TAB = "hasHostedTab"
    }
}
