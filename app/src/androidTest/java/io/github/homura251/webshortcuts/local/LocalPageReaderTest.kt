package io.github.homura251.webshortcuts.local

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.ThemeMode
import java.io.File
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real System WebView coverage: archived images and CSS must render without a network request. */
class LocalPageReaderTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as WebShortcutsApplication
    private var scenario: ActivityScenario<LocalPageActivity>? = null

    @Before fun prepare() = runBlocking {
        application.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.container.repository.load()
        application.container.themePreferences.update(AppearancePreferences(themeMode = ThemeMode.LIGHT, dynamicColor = false))
    }

    @After fun close() { scenario?.close() }

    @Test fun readsUnicodeHtmlOfflineAndDoesNotExecuteImportedScriptsOrFetchRemoteImages() {
        val connections = AtomicInteger(0)
        val observing = AtomicBoolean(true)
        val observerFailure = AtomicReference<Throwable>()
        ServerSocket(0).use { server ->
            server.soTimeout = 250
            val observer = thread(name = "offline-network-observer", isDaemon = true) {
                while (observing.get()) {
                    try { server.accept().use { connections.incrementAndGet() } }
                    catch (_: SocketTimeoutException) { /* Keep observing through cold WebView startup. */ }
                    catch (failure: Exception) {
                        if (observing.get()) observerFailure.set(failure)
                        break
                    }
                }
            }
            try {
                val bytes = fixture("standalone.html").toString(Charsets.UTF_8)
                    .replace("https://network.invalid/missing.png", "http://127.0.0.1:${server.localPort}/missing.png").toByteArray()
                val bookmark = save(bytes, BookmarkSourceType.HTML, "旅行手记")
                open(bookmark)
                assertPassiveReader("旅行手记 · 离线网页")
                assertPageContains("即使没有网络")
                assertPageContains("脚本未执行")
                capture("24_local_html_reader.png").recycle()
            } finally {
                observing.set(false)
                server.close()
                observer.join(3_000)
            }
            assertNull("Network observer failed", observerFailure.get())
            assertEquals(0, connections.get())
        }
    }

    @Test fun nativeMhtmlRendersTheEmbeddedImageAndStylesheetOffline() {
        val bookmark = save(fixture("complete.mhtml"), BookmarkSourceType.MHTML, "完整网页归档")
        open(bookmark)
        assertPassiveReader("MHTML 离线归档")
        assertPageContains("文字、图片和样式")
        assertPageContains("脚本未执行")
        val rendered = capture("25_local_mhtml_reader.png")
        try {
            // These opaque colors exist only in the embedded PNG and external archived CSS.
            // A missing/blocked resource cannot be mistaken for a successful title-only load.
            assertTrue("Embedded MHTML PNG must actually render", countColor(rendered, Color.rgb(241, 60, 138)) > 256)
            assertTrue("Embedded MHTML stylesheet must actually render", countColor(rendered, Color.rgb(65, 105, 225)) > 256)
        } finally { rendered.recycle() }
    }

    @Test fun missingPrivateCopyShowsAnActionableError() {
        val bookmark = save(fixture("standalone.html"), BookmarkSourceType.HTML, "文件已移除")
        assertTrue(LocalPageStore(application).delete(bookmark.localFileName!!))
        scenario = ActivityScenario.launch(LocalPageActivity.intent(application, bookmark.id))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("local_page_error").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("找不到本地副本，请返回首页重新导入文件").assertIsDisplayed()
    }

    private fun save(bytes: ByteArray, type: BookmarkSourceType, title: String): BookmarkItem = runBlocking {
        val name = LocalPageStore(application).restoreBytes(if (type == BookmarkSourceType.MHTML) "fixture.mhtml" else "fixture.html", bytes)
        BookmarkItem(title = title, url = "", sourceType = type, localFileName = name,
            localDisplayName = if (type == BookmarkSourceType.MHTML) "complete.mhtml" else "standalone.html",
            selectedIconSource = IconSourceMode.GENERATED).also { application.container.repository.save(it) }
    }

    private fun open(bookmark: BookmarkItem) {
        scenario = ActivityScenario.launch(LocalPageActivity.intent(application, bookmark.id))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("local_webview").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("local_page_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("local_page_reader").assertIsDisplayed()
    }

    private fun assertPassiveReader(expectedTitle: String) {
        scenario!!.onActivity { activity ->
            val view = findWebView(activity.window.decorView) ?: error("Reader WebView not found")
            assertEquals(expectedTitle, view.title)
            assertFalse("Production reader must leave scripts disabled", view.settings.javaScriptEnabled)
            assertTrue(view.settings.blockNetworkLoads)
            assertFalse(view.settings.allowContentAccess)
        }
    }

    /** Native find works with JavaScript disabled and the document sandbox still active. */
    private fun assertPageContains(text: String) {
        val count = AtomicInteger(0)
        val complete = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val view = findWebView(activity.window.decorView) ?: error("Reader WebView not found")
            view.setFindListener { _, numberOfMatches, done ->
                if (done) {
                    count.set(numberOfMatches)
                    view.setFindListener(null)
                    view.clearMatches()
                    complete.countDown()
                }
            }
            view.findAllAsync(text)
        }
        assertTrue("WebView text lookup timed out", complete.await(10, TimeUnit.SECONDS))
        assertTrue("Rendered document is missing: $text", count.get() > 0)
    }

    private fun awaitRenderedFrame() {
        val complete = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val view = findWebView(activity.window.decorView) ?: error("Reader WebView not found")
            view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    view.postInvalidateOnAnimation()
                    view.postOnAnimation { view.postOnAnimation { complete.countDown() } }
                }
            })
        }
        assertTrue("WebView did not draw its completed document", complete.await(10, TimeUnit.SECONDS))
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun capture(name: String): Bitmap {
        awaitRenderedFrame()
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val directory = File(checkNotNull(application.getExternalFilesDir(null)), "screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        return screenshot
    }

    private fun countColor(bitmap: Bitmap, expected: Int): Int {
        val readable = if (bitmap.config == Bitmap.Config.HARDWARE) {
            requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        } else bitmap
        return try {
            val pixels = IntArray(readable.width * readable.height)
            readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
            pixels.count { pixel ->
                kotlin.math.abs(Color.red(pixel) - Color.red(expected)) <= 3 &&
                    kotlin.math.abs(Color.green(pixel) - Color.green(expected)) <= 3 &&
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(expected)) <= 3
            }
        } finally { if (readable !== bitmap) readable.recycle() }
    }

    private fun fixture(name: String) = instrumentation.context.assets.open("local/$name").use { it.readBytes() }
}
