package io.github.homura251.webshortcuts.shortcuts

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.homura251.webshortcuts.MainActivity
import io.github.homura251.webshortcuts.WebShortcutsApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Keeps the browser's Custom Tab above a bookmark-specific document root, never above MainActivity. */
class BookmarkWindowActivity : ComponentActivity() {
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

    override fun onResume() {
        super.onResume()
        // Removing a PiP parent task also removes its floating child. Keep the empty anchor
        // reusable but hide its card and move it behind the underlying app instead.
        val generation = routingGeneration
        if (browserOpened && leftForBrowser) lifecycleScope.launch {
            yield()
            if (generation == routingGeneration && browserOpened && isEmptyHost()) hideHost(revealCollection = true)
        }
    }

    private fun setHostExcluded(excluded: Boolean) {
        getSystemService(ActivityManager::class.java).appTasks.firstOrNull { task ->
            runCatching { task.taskInfo?.baseIntent?.filterEquals(intent) == true }.getOrDefault(false)
        }?.setExcludeFromRecents(excluded)
    }

    private fun hideHost(revealCollection: Boolean = false) {
        setHostExcluded(true)
        // Moving this task back animates the collection in again. If it is already visible
        // through our transparent window, reveal that same task without a second transition.
        if (!revealCollection || !MainActivity.returnToVisibleCollection(this)) moveTaskToBack(true)
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
                val icon = container.iconResolver.resolve(bookmark)
                ensureActive()
                @Suppress("DEPRECATION")
                setTaskDescription(ActivityManager.TaskDescription(bookmark.title, icon))
                if (!launchBrowser) return@launch
                val result = BrowserLauncher.openInBookmarkTask(this@BookmarkWindowActivity, bookmark)
                when {
                    !result.opened -> fail(result.message ?: "暂时无法打开收藏")
                    result.customTab -> {
                        browserOpened = true
                        hasHostedTab = true
                        setHostExcluded(false)
                    }
                    else -> closeHost()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("BookmarkWindow", "Window launch failed: ${failure.javaClass.simpleName}")
                if (launchBrowser) fail("暂时无法打开收藏，请打开 WebShortcuts 检查本地数据")
            }
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
