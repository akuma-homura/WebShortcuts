package io.github.homura251.webshortcuts.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import io.github.homura251.webshortcuts.ui.components.ExpressiveAlertDialog
import io.github.homura251.webshortcuts.ui.components.ExpressiveLoading
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Non-exported reader. Every entry contains a bookmark ID, never an external file path or URL. */
class LocalPageActivity : ComponentActivity() {
    private var bookmarkId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bookmarkId = intent.getStringExtra(EXTRA_BOOKMARK_ID)
        val container = (application as WebShortcutsApplication).container
        setContent {
            val appearance by container.themePreferences.state.collectAsStateWithLifecycle()
            val dark = appearance.themeMode.isDark(isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            WebShortcutsTheme(darkTheme = dark, dynamicColor = appearance.dynamicColor, palette = appearance.palette) {
                LocalPageScreen(bookmarkId, dark, onClose = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bookmarkId = intent.getStringExtra(EXTRA_BOOKMARK_ID)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LocalPageScreen(id: String?, darkTheme: Boolean, onClose: () -> Unit) {
        val container = (application as WebShortcutsApplication).container
        var page by remember(id) { mutableStateOf<Pair<BookmarkItem, File>?>(null) }
        var error by remember(id) { mutableStateOf<String?>(null) }
        var reading by remember(id) { mutableStateOf(true) }
        var loading by remember(id) { mutableStateOf(true) }
        var reload by remember(id) { mutableIntStateOf(0) }
        var externalLink by remember(id) { mutableStateOf<String?>(null) }
        var webView by remember(id) { mutableStateOf<WebView?>(null) }
        val owner = LocalLifecycleOwner.current

        LaunchedEffect(id, reload) {
            page = null
            error = null
            reading = true
            loading = true
            try {
                require(!id.isNullOrBlank() && id.length <= 128) { "本地网页快捷方式无效" }
                val bookmark = container.repository.find(id) ?: error("此收藏已删除，请返回首页重新导入")
                require(bookmark.sourceType != BookmarkSourceType.WEB) { "此收藏是在线网页，请从首页打开" }
                val file = withContext(Dispatchers.IO) {
                    LocalPageStore(this@LocalPageActivity).resolve(bookmark.localFileName.orEmpty())
                } ?: error("找不到本地副本，请返回首页重新导入文件")
                page = bookmark to file
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "无法打开本地网页"
            } finally {
                reading = false
            }
        }

        DisposableEffect(owner, webView) {
            val current = webView
            val observer = LifecycleEventObserver { _, event ->
                if ((current as? OfflineWebView)?.released == true) return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_RESUME -> current?.onResume()
                    Lifecycle.Event.ON_PAUSE -> current?.onPause()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }

        BackHandler {
            val current = webView
            if (current?.canGoBack() == true) current.goBack() else onClose()
        }
        Scaffold(
            modifier = Modifier.testTag("local_page_reader"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(page?.first?.title ?: stringResource(R.string.local_reader_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.local_reader_offline), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.local_reader_close)) } },
                    actions = { IconButton(onClick = { reload++ }, enabled = !reading) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.local_reader_reload))
                    } },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    reading -> ExpressiveLoading(Modifier.align(Alignment.Center))
                    error != null -> Column(Modifier.align(Alignment.Center).padding(28.dp).testTag("local_page_error"),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.local_reader_unavailable), style = MaterialTheme.typography.titleLarge)
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        ExpressiveTonalButton(onClick = onClose) { Text(stringResource(R.string.local_reader_return)) }
                    }
                    page != null -> {
                        val (bookmark, file) = page!!
                        val background = MaterialTheme.colorScheme.surface.toArgb()
                        key(id, reload, file.path) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize().testTag("local_webview"),
                                factory = { context ->
                                    OfflineWebView(context).apply {
                                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                        setBackgroundColor(background)
                                        configureOfflineWebView(this, bookmark.sourceType, darkTheme)
                                        val access = LocalPageAccessPolicy(file, bookmark.sourceType)
                                        webViewClient = LocalPageWebViewClient(access,
                                            onLoading = { loading = it }, onError = { error = it }, onExternalLink = { externalLink = it })
                                        webView = this
                                        loadUrl(access.documentUrl)
                                    }
                                },
                                onRelease = { view ->
                                    if (webView === view) webView = null
                                    view.release()
                                },
                                update = { view ->
                                    if (!view.released) {
                                        view.setBackgroundColor(background)
                                        if (android.os.Build.VERSION.SDK_INT >= 33) view.settings.isAlgorithmicDarkeningAllowed = darkTheme
                                    }
                                },
                            )
                        }
                        if (loading) ExpressiveLoading(Modifier.align(Alignment.TopCenter).testTag("local_page_loading"))
                    }
                }
            }
        }
        externalLink?.let { link ->
            ExpressiveAlertDialog(
                onDismissRequest = { externalLink = null },
                title = { Text(stringResource(R.string.local_open_link_title)) },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.local_open_link_message))
                    Text(link.take(1024), maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                } },
                confirmButton = { ExpressiveTextButton(onClick = {
                    externalLink = null
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE))
                    } catch (_: Exception) {
                        Toast.makeText(this@LocalPageActivity, R.string.local_no_browser, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.local_open_link_confirm)) } },
                dismissButton = { ExpressiveTextButton(onClick = { externalLink = null }) { Text(stringResource(R.string.local_open_link_cancel)) } },
            )
        }
    }

    companion object {
        const val EXTRA_BOOKMARK_ID = "io.github.homura251.webshortcuts.LOCAL_BOOKMARK_ID"
        fun intent(context: Context, bookmarkId: String): Intent = Intent(context, LocalPageActivity::class.java)
            .putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
    }
}
