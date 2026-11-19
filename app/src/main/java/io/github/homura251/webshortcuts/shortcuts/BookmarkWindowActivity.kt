package io.github.homura251.webshortcuts.shortcuts

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.ExperimentalMinimizationCallback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.icons.IconResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

/** Keeps the browser's Custom Tab above a bookmark-specific document root, never above MainActivity. */
class BookmarkWindowActivity : ComponentActivity() {
    private val tabSession by viewModels<BookmarkTabSession>()
    private var routingJob: Job? = null
    private var browserOpened = false
    private var leftForBrowser = false
    private var routingGeneration = 0
    private var hasHostedTab = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserOpened = savedInstanceState?.getBoolean(STATE_BROWSER_OPENED) == true
        hasHostedTab = browserOpened || savedInstanceState?.getBoolean(STATE_HAS_HOSTED_TAB) == true
        leftForBrowser = browserOpened
        route(intent, launchBrowser = !browserOpened)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // intoExisting clears the previous tab above this root. Resolve fresh metadata and open
        // the saved address again, including URL or browser edits made since the last visit.
        route(intent)
    }

    override fun onResume() {
        super.onResume()
        // Removing a PiP parent task also removes its floating child. Keep the empty anchor
        // reusable but hide its card and move it behind the underlying app instead.
        val generation = routingGeneration
        if (browserOpened && leftForBrowser) lifecycleScope.launch {
            yield()
            if (generation == routingGeneration && browserOpened && isEmptyHost()) hideHost()
        }
    }

    @Suppress("DEPRECATION")
    private fun hostTask(): ActivityManager.AppTask? = getSystemService(ActivityManager::class.java).appTasks
        .firstOrNull { task -> runCatching { task.taskInfo?.persistentId == taskId }.getOrDefault(false) }

    private fun setHostExcluded(excluded: Boolean) {
        hostTask()?.setExcludeFromRecents(excluded)
    }

    private fun hideHost() {
        setHostExcluded(true)
        moveTaskToBack(true)
    }

    private fun closeHost() {
        if (hasHostedTab) hideHost() else finishAndRemoveTask()
    }

    private fun isEmptyHost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return runCatching { hostTask()?.taskInfo?.numActivities == 1 }.getOrDefault(false)
    }

    override fun onPause() {
        if (browserOpened) leftForBrowser = true
        super.onPause()
    }

    override fun onStop() {
        if (browserOpened) leftForBrowser = true
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_BROWSER_OPENED, browserOpened)
        outState.putBoolean(STATE_HAS_HOSTED_TAB, hasHostedTab)
        super.onSaveInstanceState(outState)
    }

    private fun route(incoming: Intent, launchBrowser: Boolean = true) {
        routingGeneration++
        routingJob?.cancel()
        if (launchBrowser) {
            tabSession.reset()
            browserOpened = false
            leftForBrowser = false
        }
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
                    val callback = hostTask()?.let(tabSession::callbackFor)
                    val result = BrowserLauncher.openInBookmarkTask(this@BookmarkWindowActivity, bookmark, callback)
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
            val size = minOf(artwork.width, artwork.height, 192)
            val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            AdaptiveIconDrawable(null, BitmapDrawable(resources, artwork)).apply {
                setBounds(0, 0, size, size)
                draw(Canvas(icon))
            }
            setTaskDescription(ActivityManager.TaskDescription(bookmark.title, icon))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("BookmarkWindow", "Could not update task artwork: ${failure.javaClass.simpleName}")
            coroutineContext.ensureActive()
            runCatching { setTaskDescription(ActivityManager.TaskDescription(bookmark.title)) }
        }
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

/** Retains the browser callback when Android recreates the bookmark host. */
internal class BookmarkTabSession : ViewModel() {
    private var callback: CustomTabsCallback? = null
    private var restoreJob: Job? = null

    fun reset() {
        callback = null
        restoreJob?.cancel()
    }

    @OptIn(ExperimentalMinimizationCallback::class)
    fun callbackFor(task: ActivityManager.AppTask): CustomTabsCallback = object : CustomTabsCallback() {
        override fun onMinimized(extras: Bundle) {
            if (callback === this) restoreJob?.cancel()
        }

        override fun onUnminimized(extras: Bundle) {
            if (callback !== this) return
            restoreJob?.cancel()
            restoreJob = viewModelScope.launch {
                // Android can deliver this callback before moving the browser back into its task.
                withTimeoutOrNull(2_000) {
                    while (true) {
                        val info = runCatching { task.taskInfo }.getOrNull() ?: return@withTimeoutOrNull
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || info.numActivities > 1) {
                            runCatching { task.setExcludeFromRecents(false) }
                            return@withTimeoutOrNull
                        }
                        delay(50)
                    }
                }
            }
        }
    }.also { callback = it }
}
