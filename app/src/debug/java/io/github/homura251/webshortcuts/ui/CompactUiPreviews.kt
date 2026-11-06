package io.github.homura251.webshortcuts.ui

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.LayoutMode
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import java.io.File

@Preview(name = "Compact list", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactListPreview() = CollectionPreview()

@Preview(name = "Compact grid", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactGridPreview() = CollectionPreview(grid = true)

@Preview(name = "Compact dark", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactDarkPreview() = CollectionPreview(dark = true)

@Preview(name = "Narrow large font", widthDp = 320, heightDp = 720, fontScale = 2f, showBackground = true)
@Composable
fun CompactLargeFontPreview() = CollectionPreview(grid = true)

@Preview(name = "Automatic icon editor", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactEditorPreview() = CollectionPreview(editor = IconSourceMode.AUTO)

@Preview(name = "Custom icon editor", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
fun CompactCustomEditorPreview() = CollectionPreview(editor = IconSourceMode.CUSTOM)

@Preview(name = "Editor large font", widthDp = 320, heightDp = 720, fontScale = 2f, showBackground = true)
@Composable
fun CompactEditorLargeFontPreview() = CollectionPreview(editor = IconSourceMode.AUTO)

@Preview(name = "Appearance settings", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun CompactAppearancePreview() {
    WebShortcutsTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            AppearanceContent(AppearancePreferences(dynamicColor = false), {}, {})
        }
    }
}

@Preview(name = "Appearance large font", widthDp = 320, heightDp = 720, fontScale = 2f, showBackground = true)
@Composable
fun CompactAppearanceLargeFontPreview() = CompactAppearancePreview()

@Preview(name = "Crop large font", widthDp = 320, heightDp = 720, fontScale = 2f, showBackground = true)
@Composable
fun CompactCropLargeFontPreview() = FreeCropEditorPreview()

@Composable
private fun CollectionPreview(grid: Boolean = false, dark: Boolean = false, editor: IconSourceMode? = null) {
    val context = LocalContext.current
    val resolver = remember(context) {
        // Layoutlib has no application storage; these fixtures never load user data or use the network.
        val previewContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir"), "webshortcuts-ui-preview")
        }
        IconResolver(previewContext, IconStore(previewContext), IconProcessor())
    }
    val timestamp = remember { System.currentTimeMillis() - 3_600_000 }
    val bookmarks = remember(timestamp) {
        listOf(
            BookmarkItem(id = "preview-docs", title = "Android 开发文档", url = "https://developer.android.com/", lastFetchedAt = timestamp),
            BookmarkItem(id = "preview-openai", title = "OpenAI", url = "https://openai.com/", selectedIconSource = IconSourceMode.CUSTOM),
            BookmarkItem(id = "preview-github", title = "GitHub 项目与代码", url = "https://github.com/", selectedIconSource = IconSourceMode.GENERATED),
            BookmarkItem(id = "preview-material", title = "Material Design", url = "https://m3.material.io/", lastFetchedAt = timestamp),
            BookmarkItem(id = "preview-kotlin", title = "Kotlin 文档", url = "https://kotlinlang.org/", lastFetchedAt = timestamp),
            BookmarkItem(id = "preview-compose", title = "Jetpack Compose", url = "https://developer.android.com/compose/", selectedIconSource = IconSourceMode.GENERATED),
        )
    }
    val source = remember {
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).drawCircle(256f, 256f, 150f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(43, 83, 164) })
        }
    }
    val appearance = AppearancePreferences(dynamicColor = false, layoutMode = if (grid) LayoutMode.GRID else LayoutMode.LIST)
    val state = HomeUiState(loading = false, bookmarks = bookmarks, editor = editor?.let { mode ->
        EditorUiState(id = "preview-editor", isNew = false, title = "Android 开发文档", url = "https://developer.android.com/",
            mode = mode, preview = source, sourceLabel = "Web App Manifest · 512×512",
            sourceUrl = "https://developer.android.com/static/images/logos/android.svg", lastFetchedAt = timestamp,
            hasCustomImage = mode == IconSourceMode.CUSTOM, canCrop = mode == IconSourceMode.CUSTOM,
            cropSource = if (mode == IconSourceMode.CUSTOM) source else null)
    })
    WebShortcutsTheme(darkTheme = dark, dynamicColor = false) {
        WebShortcutsApp(state, resolver, remember { SnackbarHostState() },
            onQueryChange = {}, onAdd = {}, onEdit = {}, onOpen = {}, onPin = {}, onUpdateShortcut = {},
            onRefresh = {}, onRefreshAll = {}, onDelete = {}, onDismissEditor = {}, onTitleChange = {},
            onUrlChange = {}, onUrlFocusLost = {}, onModeChange = {}, onPickImage = {}, onRefetchIcon = {},
            onSave = {}, appearance = appearance)
    }
}
