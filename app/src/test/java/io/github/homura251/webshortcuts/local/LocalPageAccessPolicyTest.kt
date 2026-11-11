package io.github.homura251.webshortcuts.local

import android.content.Context
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPageAccessPolicyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun htmlServesOnePrivateDocumentAndBlocksEveryOtherOriginOrPath() {
        val file = File(context.cacheDir, "safe.html").apply { writeText("<html><body>Only this document</body></html>") }
        val policy = LocalPageAccessPolicy(file, BookmarkSourceType.HTML)
        val response = policy.response(Uri.parse(policy.documentUrl), mainFrame = true)!!
        assertEquals(200, response.statusCode)
        assertTrue(response.data.bufferedReader().use { it.readText() }.contains("Only this document"))
        assertTrue(response.responseHeaders.getValue("Content-Security-Policy").contains("default-src 'none'"))
        assertTrue(policy.isDocument(Uri.parse(policy.documentUrl + "#section")))
        for (uri in listOf("https://example.com/pixel.png", "http://127.0.0.1/", "content://private/bookmarks",
            "file:///data/data/io.github.homura251.webshortcuts/shared_prefs/appearance.xml",
            "https://local.webshortcuts.invalid/safe.html/../other.html", policy.documentUrl + "?other=1")) {
            assertEquals(403, policy.response(Uri.parse(uri), mainFrame = true)!!.statusCode)
            assertEquals(403, policy.response(Uri.parse(uri), mainFrame = false)!!.statusCode)
        }
        assertNull(policy.response(Uri.parse("data:image/png;base64,AAAA"), mainFrame = false))
        assertEquals(403, policy.response(Uri.parse("data:text/html,untrusted"), mainFrame = true)!!.statusCode)
    }

    @Test fun nativeArchiveMayReadOnlyItsExactMainFile() {
        val file = File(context.cacheDir, "safe.mhtml").apply { writeText("archive fixture") }
        val policy = LocalPageAccessPolicy(file, BookmarkSourceType.MHTML)
        assertNull(policy.response(Uri.parse(policy.documentUrl), mainFrame = true))
        assertEquals(403, policy.response(Uri.parse(policy.documentUrl), mainFrame = false)!!.statusCode)
        assertEquals(403, policy.response(Uri.fromFile(File(file.parentFile, "other.mhtml")), mainFrame = true)!!.statusCode)
        assertEquals(403, policy.response(Uri.parse("https://original.example/missing.css"), mainFrame = false)!!.statusCode)
        assertFalse(policy.isExternalLink(Uri.parse("javascript:alert(1)")))
        assertFalse(policy.isExternalLink(Uri.parse("intent://other/#Intent;end")))
        assertTrue(policy.isExternalLink(Uri.parse("https://example.com/article")))
    }

    @Test fun rendererLossReleasesTheViewAndReturnsARecoverableError() {
        val file = File(context.cacheDir, "renderer.html").apply { writeText("<html><body>test</body></html>") }
        var message: String? = null
        val client = LocalPageWebViewClient(LocalPageAccessPolicy(file, BookmarkSourceType.HTML), onError = { message = it })
        val view = OfflineWebView(context)
        val handled = client.onRenderProcessGone(view, object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_BOUND
        })
        assertTrue(handled)
        assertTrue(view.released)
        assertTrue(message.orEmpty().contains("重新加载"))
        view.release() // Compose's eventual release must remain safe after the renderer callback.
    }
}
