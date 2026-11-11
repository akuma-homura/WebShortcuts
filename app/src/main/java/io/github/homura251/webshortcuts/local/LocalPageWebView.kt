package io.github.homura251.webshortcuts.local

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import java.io.ByteArrayInputStream
import java.io.File

/** Idempotent disposal also covers renderer crashes before Compose releases its AndroidView. */
class OfflineWebView(context: Context) : WebView(context) {
    var released = false
        private set

    fun release(removeFromParent: Boolean = false) {
        if (released) return
        released = true
        stopLoading()
        webViewClient = WebViewClient()
        if (removeFromParent) (parent as? ViewGroup)?.removeView(this)
        removeAllViews()
        destroy()
    }
}

/**
 * HTML is served at one intercepted HTTPS address. MHTML uses WebView's native archive reader;
 * only that one private archive may be opened through file://. No other path is ever delegated.
 */
class LocalPageAccessPolicy(val file: File, val sourceType: BookmarkSourceType) {
    val documentUrl: String = if (sourceType == BookmarkSourceType.MHTML) {
        Uri.fromFile(file).toString()
    } else {
        "https://local.webshortcuts.invalid/${Uri.encode(file.name)}/index.html"
    }

    fun isDocument(uri: Uri): Boolean = uri.buildUpon().fragment(null).build().toString() == documentUrl

    fun isExternalLink(uri: Uri): Boolean = uri.scheme?.lowercase() in setOf("http", "https") && !isDocument(uri)

    fun response(uri: Uri, mainFrame: Boolean): WebResourceResponse? {
        if (isDocument(uri)) {
            if (sourceType == BookmarkSourceType.MHTML) return if (mainFrame) null else blocked()
            return runCatching {
                WebResourceResponse("text/html", LocalPagePolicy.htmlEncoding(file), 200, "OK", mapOf(
                    "Cache-Control" to "no-store",
                    "Content-Security-Policy" to "default-src 'none'; img-src data:; style-src 'unsafe-inline' data:; font-src data:; media-src data:; frame-src 'none'; connect-src 'none'; form-action 'none'; base-uri 'none'; sandbox",
                    "X-Content-Type-Options" to "nosniff",
                ), file.inputStream())
            }.getOrElse { blocked() }
        }
        // Embedded data resources are self-contained. They cannot fetch, execute scripts, or read
        // files because WebSettings and (for HTML) CSP independently prohibit those capabilities.
        if (!mainFrame && uri.scheme == "data") return null
        return blocked()
    }

    private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Offline only",
        mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
}

/** All web-origin requests stay offline, including redirects and missing archive resources. */
class LocalPageWebViewClient(
    private val policy: LocalPageAccessPolicy,
    private val onLoading: (Boolean) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onExternalLink: (String) -> Unit = {},
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        policy.response(request.url, request.isForMainFrame)

    @Suppress("DEPRECATION")
    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? =
        policy.response(Uri.parse(url), policy.isDocument(Uri.parse(url)))

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (policy.isDocument(request.url)) return false
        if (request.isForMainFrame && request.hasGesture() && policy.isExternalLink(request.url)) {
            onExternalLink(request.url.toString().take(8192))
        }
        return true
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val uri = Uri.parse(url)
        if (policy.isDocument(uri)) return false
        if (policy.isExternalLink(uri)) onExternalLink(url.take(8192))
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        onLoading(true)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onLoading(false)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            onLoading(false)
            onError("无法显示本地网页。文件可能已损坏，或当前 Android System WebView 不支持此归档。")
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        if (view is OfflineWebView) view.release(removeFromParent = true)
        else {
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        onLoading(false)
        onError(if (detail.didCrash()) "网页渲染器意外关闭，请重新加载本地副本。" else "设备内存不足，网页已暂停。请关闭其他应用后重新加载。")
        return true
    }
}

/** Passive document reading deliberately has no JavaScript bridge, scripts, or online fallback. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
fun configureOfflineWebView(view: WebView, sourceType: BookmarkSourceType, darkTheme: Boolean = false) {
    view.settings.apply {
        javaScriptEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        domStorageEnabled = false
        databaseEnabled = false
        setGeolocationEnabled(false)
        allowContentAccess = false
        allowFileAccess = sourceType == BookmarkSourceType.MHTML
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        blockNetworkLoads = true
        // Archive images retain their original HTTPS locations, but are read from the MHTML
        // container. blockNetworkLoads and the client stop real network I/O without hiding them.
        blockNetworkImage = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_NO_CACHE
        defaultTextEncodingName = "UTF-8"
        textZoom = (view.resources.configuration.fontScale * 100).toInt().coerceAtLeast(1)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
        mediaPlaybackRequiresUserGesture = true
        safeBrowsingEnabled = true
        if (Build.VERSION.SDK_INT >= 33) isAlgorithmicDarkeningAllowed = darkTheme
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
    view.setDownloadListener { _, _, _, _, _ -> /* Downloads require an explicit browser action. */ }
}
