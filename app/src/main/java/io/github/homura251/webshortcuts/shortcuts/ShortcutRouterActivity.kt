package io.github.homura251.webshortcuts.shortcuts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.homura251.webshortcuts.WebShortcutsApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/** Translucent, short-lived entry point used by every pinned bookmark shortcut. */
class ShortcutRouterActivity : ComponentActivity() {
    private var routingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    private fun route(incoming: Intent) {
        routingJob?.cancel()
        routingJob = lifecycleScope.launch {
            try {
                val id = incoming.getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID)
                if (incoming.action != ShortcutIntents.ACTION_OPEN_BOOKMARK || id.isNullOrBlank() || id.length > 128) {
                    message("快捷方式无效，请在 WebShortcuts 中重新添加")
                    return@launch
                }
                val repository = (application as WebShortcutsApplication).container.repository
                val bookmark = repository.find(id)
                when {
                    bookmark == null -> message("此收藏已删除，请在 WebShortcuts 中重新添加")
                    !ChromeTabLauncher.open(this@ShortcutRouterActivity, bookmark.url) -> message("没有可用的浏览器，请先安装 Chrome 或其他浏览器")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message("暂时无法打开收藏，请打开 WebShortcuts 检查本地数据")
            } finally {
                if (isActive) finish()
            }
        }
    }

    private fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
