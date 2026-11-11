package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSourceType
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.shortcuts.BrowserOption
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Real device composition of the source/account controls and the new first-run import action. */
class ShortcutSourcesInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val browsers = listOf(BrowserOption("com.android.chrome", "Chrome", true),
        BrowserOption("com.chrome.beta", "Chrome Beta", true), BrowserOption("org.mozilla.firefox", "Firefox", true))

    @Test fun browserPickerChangesOnlyTheRequestedAccountProvider() {
        val choice = mutableStateOf<String?>("com.android.chrome")
        var dismissed = false
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                BrowserPicker(choice.value, browsers, { choice.value = it }, { dismissed = true })
            }
        }
        compose.onNodeWithTag("browser_com.chrome.beta").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("com.chrome.beta", choice.value); assertTrue(dismissed) }
    }

    @Test fun missingSelectedBrowserRemainsVisibleAndAutomaticCanBeChosenExplicitly() {
        var choice: String? = "com.chrome.beta"
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                BrowserPicker("com.chrome.beta", browsers.filterNot { it.packageName == "com.chrome.beta" }, { choice = it }, {})
            }
        }
        compose.onNodeWithTag("browser_unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("browser_automatic").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(null, choice) }
    }

    @Test fun captureExpressiveOnlineAndOfflineCollectionAndEditor() {
        val resolver = IconResolver(context, IconStore(context), IconProcessor())
        val fixture = listOf(
            BookmarkItem(title = "GitHub · 工作账号", url = "https://github.com", selectedIconSource = IconSourceMode.GENERATED, browserPackage = "com.android.chrome"),
            BookmarkItem(title = "GitHub · 个人账号", url = "https://github.com", selectedIconSource = IconSourceMode.GENERATED, browserPackage = "com.chrome.beta"),
            BookmarkItem(title = "旅行手记", url = "", selectedIconSource = IconSourceMode.GENERATED,
                sourceType = BookmarkSourceType.MHTML, localFileName = "fixture.mhtml", localDisplayName = "京都旅行.mhtml"),
        )
        val state = mutableStateOf(HomeUiState(bookmarks = fixture, loading = false, browsers = browsers))
        val dark = mutableStateOf(false)
        compose.setContent {
            WebShortcutsTheme(darkTheme = dark.value, dynamicColor = false) {
                WebShortcutsApp(state.value, resolver, remember { SnackbarHostState() },
                    onQueryChange = {}, onAdd = {}, onEdit = {}, onOpen = {}, onPin = {}, onUpdateShortcut = {},
                    onRefresh = {}, onRefreshAll = {}, onDelete = {}, onDismissEditor = {}, onTitleChange = {},
                    onUrlChange = {}, onUrlFocusLost = {}, onModeChange = {}, onPickImage = {}, onRefetchIcon = {}, onSave = {},
                    onSourceFilterChange = { state.value = state.value.copy(sourceFilter = it) })
            }
        }
        capture("home_screen", "20_expressive_sources_light.png")
        compose.runOnIdle { dark.value = true }
        capture("home_screen", "21_expressive_sources_dark.png")
        compose.onNodeWithTag("source_filter_LOCAL").performScrollTo().performClick()
        compose.onNodeWithTag("source_filter_LOCAL").assertIsSelected()
        compose.onNodeWithText("京都旅行.mhtml").assertIsDisplayed()
        capture("home_screen", "22_offline_collection.png")
        compose.runOnIdle {
            dark.value = false
            state.value = state.value.copy(editor = EditorUiState(id = fixture.last().id, isNew = false,
                title = "旅行手记", sourceType = BookmarkSourceType.MHTML, localFileName = "fixture.mhtml",
                localDisplayName = "京都旅行.mhtml", mode = IconSourceMode.GENERATED,
                preview = resolver.generated("旅行手记", "")))
        }
        compose.onNodeWithTag("local_document_name").assertIsDisplayed()
        capture("editor_screen", "23_offline_editor.png")
    }

    private fun capture(tag: String, name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
