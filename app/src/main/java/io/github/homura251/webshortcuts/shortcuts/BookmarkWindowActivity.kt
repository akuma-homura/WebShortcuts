package io.github.homura251.webshortcuts.shortcuts

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.homura251.webshortcuts.WebShortcutsApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Keeps the browser's Custom Tab above a bookmark-specific document root, never above MainActivity. */
class BookmarkWindowActivity : ComponentActivity() {
    private var routingJob: Job? = null
    private var browserOpened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        browserOpened = savedInstanceState?.getBoolean(STATE_BROWSER_OPENED) == true
        if (!browserOpened) route(intent)
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
        // Returning from the browser must not expose an empty translucent window.
        if (browserOpened) finishAndRemoveTask()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_BROWSER_OPENED, browserOpened)
        super.onSaveInstanceState(outState)
    }

    private fun route(incoming: Intent) {
        routingJob?.cancel()
        browserOpened = false
        routingJob = lifecycleScope.launch {
            try {
                val id = incoming.getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID)
                if (incoming.action != ShortcutIntents.ACTION_OPEN_BOOKMARK || id.isNullOrBlank() || id.length > 128) {
                    fail("快捷方式无效，请在 WebShortcuts 中重新添加")
                    return@launch
                }
                val bookmark = (application as WebShortcutsApplication).container.repository.find(id)
                ensureActive()
                if (bookmark == null || bookmark.isLocal) {
                    fail("此网页收藏已变更，请在 WebShortcuts 中重新打开")
                    return@launch
                }
                @Suppress("DEPRECATION")
                setTaskDescription(ActivityManager.TaskDescription(bookmark.title))
                val result = BrowserLauncher.openInBookmarkTask(this@BookmarkWindowActivity, bookmark)
                when {
                    !result.opened -> fail(result.message ?: "暂时无法打开收藏")
                    result.customTab -> browserOpened = true
                    else -> finishAndRemoveTask()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail("暂时无法打开收藏，请打开 WebShortcuts 检查本地数据")
            }
        }
    }

    private fun fail(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
    }

    private companion object {
        const val STATE_BROWSER_OPENED = "browserOpened"
    }
}
