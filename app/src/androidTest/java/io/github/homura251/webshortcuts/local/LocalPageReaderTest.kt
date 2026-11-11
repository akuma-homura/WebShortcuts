package io.github.homura251.webshortcuts.local

import android.graphics.Bitmap
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
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
        ServerSocket(0).use { server ->
            server.soTimeout = 2_000
            val observer = thread {
                try { server.accept().use { connections.incrementAndGet() } }
                catch (_: SocketTimeoutException) { /* Expected: offline reader cannot connect. */ }
            }
            val bytes = fixture("standalone.html").toString(Charsets.UTF_8)
                .replace("https://network.invalid/missing.png", "http://127.0.0.1:${server.localPort}/missing.png").toByteArray()
            val bookmark = save(bytes, BookmarkSourceType.HTML, "旅行手记")
            open(bookmark)
            val dom = documentState()
            assertTrue(dom.getString("body").contains("即使没有网络"))
            assertEquals("旅行手记 · 离线网页", dom.getString("title"))
            assertFalse(dom.getBoolean("scriptRan"))
            observer.join(3_000)
            assertEquals(0, connections.get())
            capture("24_local_html_reader.png")
        }
    }

    @Test fun nativeMhtmlRendersTheEmbeddedImageAndStylesheetOffline() {
        val bookmark = save(fixture("complete.mhtml"), BookmarkSourceType.MHTML, "完整网页归档")
        open(bookmark)
        val dom = documentState()
        assertTrue(dom.getString("body").contains("文字、图片和样式"))
        assertEquals("MHTML 离线归档", dom.getString("title"))
        assertTrue("Embedded MHTML image must be decoded", dom.getInt("imageWidth") > 0)
        assertEquals("rgb(0, 106, 96)", dom.getString("headingColor"))
        assertFalse(dom.getBoolean("scriptRan"))
        capture("25_local_mhtml_reader.png")
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

    private fun documentState(): JSONObject {
        val result = AtomicReference<String>()
        val complete = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val view = findWebView(activity.window.decorView) ?: error("Reader WebView not found")
            assertFalse("Production reader must leave scripts disabled", view.settings.javaScriptEnabled)
            assertTrue(view.settings.blockNetworkLoads)
            assertFalse(view.settings.allowContentAccess)
            // Test-only DOM inspection after the passive document has finished loading. Enabling
            // evaluation here does not re-run script elements parsed while scripts were disabled.
            view.settings.javaScriptEnabled = true
            view.evaluateJavascript("""JSON.stringify({title:document.title,body:document.body.innerText,scriptRan:window.importedScriptRan===true,imageWidth:document.getElementById('archived-image')?.naturalWidth||0,headingColor:getComputedStyle(document.querySelector('h1')).color})""") { value ->
                view.settings.javaScriptEnabled = false
                result.set(value)
                complete.countDown()
            }
        }
        assertTrue("WebView DOM response timed out", complete.await(10, TimeUnit.SECONDS))
        return JSONObject(JSONTokener(result.get()).nextValue() as String)
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findWebView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val screenshot = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        val directory = File(checkNotNull(application.getExternalFilesDir(null)), "screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }

    private fun fixture(name: String) = instrumentation.context.assets.open("local/$name").use { it.readBytes() }
}
