package io.github.homura251.webshortcuts.ui

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.data.*
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import java.io.File

@RunWith(AndroidJUnit4::class)
class TagEntryInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val tags = listOf(BookmarkTag("docs", "Docs"), BookmarkTag("work", "Work"))

    @Test fun captureCompactTagPanelsInBothThemesAndLargeFont() {
        var panel by mutableIntStateOf(0)
        var dark by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        val choices = listOf(BookmarkTag("docs", "开发文档"), BookmarkTag("work", "工作"),
            BookmarkTag("read", "稍后阅读"), BookmarkTag("reference", "设计参考资料"))
        val home = HomeUiState(tags = choices, newTagName = "灵感")
        compose.setContent { WebShortcutsTheme(darkTheme = dark, dynamicColor = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                Surface(Modifier.fillMaxSize().testTag("tag_entry_capture"), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (panel == 1) "标签管理" else if (panel == 2) "筛选标签" else "收藏标签", style = MaterialTheme.typography.titleMedium)
                        when (panel) {
                            1 -> TagManagerContent(home, {}, { _, _ -> }, {})
                            2 -> TagFilterContent(home) {}
                            else -> TagEditorContent(choices, TagEditorUiState(setOf("one"), selectedIds = setOf("docs"),
                                newNames = listOf("待处理"), pendingName = "灵感"), {}, {}, {}, {})
                        }
                    }
                }
            }
        } }
        capture("14_tag_editor_compact.png")
        compose.runOnIdle { panel = 1; dark = true }
        capture("15_tag_manager_dark.png")
        compose.runOnIdle { panel = 0; dark = false; fontScale = 2f }
        capture("16_tag_editor_large_font.png")
        compose.runOnIdle { panel = 2; fontScale = 1f }
        capture("17_tag_filter_compact.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("tag_entry_capture").assertIsDisplayed().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun imeAddsConsecutiveNamesWithoutAPlusButtonAndKeepsFocus() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added)
        compose.onNodeWithTag("new_draft_tag").performTextInput("新标签")
        compose.onNodeWithTag("new_draft_tag").performImeAction()
        compose.onNodeWithTag("new_draft_tag").assertIsFocused().performTextInput("Second")
        compose.onNodeWithTag("new_draft_tag").performImeAction()
        assertEquals(listOf("新标签", "Second"), added)
        assertEquals("", sheet.value.pendingName)
        compose.onNodeWithTag("draft_tag_新标签").assertIsDisplayed()
        compose.onNodeWithTag("create_draft_tag").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun hardwareEnterSubmitsExactlyOnce() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added)
        compose.onNodeWithTag("new_draft_tag").performTextInput("Hardware")
        compose.onNodeWithTag("new_draft_tag").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("Hardware"), added)
        compose.onNodeWithTag("new_draft_tag").assertIsFocused()
    }

    @Test fun saveReceivesUnsubmittedNewTextAndSearchNeverCreatesATag() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        var saved = ""
        editor(sheet, added, onApply = { saved = sheet.value.pendingName })
        compose.onNodeWithTag("edit_tag_search").performTextInput("Work")
        compose.onNodeWithTag("edit_tag_search").performImeAction()
        assertTrue(added.isEmpty())
        compose.onNodeWithTag("new_draft_tag").performTextInput("Pending")
        compose.onNodeWithTag("apply_tags").performClick()
        assertEquals("Pending", saved)
        assertTrue(added.isEmpty())
    }

    @Test fun chineseCompositionIsNotSubmittedUntilCommitted() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        val connection = AtomicReference<InputConnection>()
        editor(sheet, added, onConnection = connection::set)
        compose.onNodeWithTag("new_draft_tag").performClick()
        compose.waitUntil(5000) { connection.get() != null }
        compose.runOnIdle { assertTrue(connection.get().setComposingText("zhong", 1)) }
        compose.onNodeWithTag("apply_tags").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(connection.get().performEditorAction(EditorInfo.IME_ACTION_DONE)) }
        compose.waitForIdle()
        assertTrue(added.isEmpty())
        compose.onNodeWithTag("apply_tags").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(connection.get().commitText("中", 1)) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(connection.get().performEditorAction(EditorInfo.IME_ACTION_DONE)) }
        compose.waitForIdle()
        assertEquals(listOf("中"), added)
    }

    @Test fun managerKeepsSearchWhileCreatingAndRestoresFocusAfterAsyncSave() {
        var home by mutableStateOf(HomeUiState(tags = tags))
        val added = mutableListOf<String>()
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            Column(Modifier.requiredSize(360.dp, 480.dp)) {
                TagManagerContent(home, { added += it; home = home.copy(tagSaving = true) }, { _, _ -> }, {},
                    onNewNameChange = { home = home.copy(newTagName = it) })
            }
        } }
        compose.onNodeWithTag("manage_tag_search").performTextInput("Work")
        compose.onNodeWithTag("manage_tag_search").performImeAction()
        assertTrue(added.isEmpty())
        compose.onNodeWithTag("manage_tag_name").performTextInput("Fresh")
        compose.onNodeWithTag("manage_tag_name").performImeAction()
        assertEquals(listOf("Fresh"), added)
        compose.onNodeWithTag("manage_tag_name").assertIsNotEnabled()
        compose.runOnIdle { home = home.copy(tags = tags.reversed() + BookmarkTag("fresh", "Fresh"), newTagName = "", tagSaving = false) }
        compose.onNodeWithTag("manage_tag_name").assertIsFocused()
        compose.onNodeWithTag("manage_tag_search").assertTextContains("Work")
        compose.onNodeWithText("Docs").assertDoesNotExist()
        compose.onNodeWithTag("create_tag").assertDoesNotExist()
    }

    @Test fun choicesUseMultipleColumnsAndCardShowsAllShortTagsThatFit() {
        val short = (0..3).map { BookmarkTag("tag-$it", "Tag $it") }
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            Column(Modifier.requiredSize(360.dp, 480.dp)) {
                BookmarkTagRow(short, emptySet(), {})
                TagEditorContent(tags, TagEditorUiState(setOf("one")), {}, {}, {}, {})
            }
        } }
        short.forEach { compose.onNodeWithTag("card_tag_${it.id}").assertIsDisplayed() }
        compose.onNodeWithTag("more_tags").assertDoesNotExist()
        val first = compose.onNodeWithTag("edit_tag_docs").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("edit_tag_work").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top)
        assertTrue(first.left < second.left)
        val density = compose.density.density
        assertTrue(first.height / density >= 48f)
    }

    @Test fun largeFontKeepsTheNewInputAndSaveReachableAndRemovalHasNoNewInput() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added, fontScale = 2f)
        compose.onNodeWithTag("new_draft_tag").assertIsDisplayed().performTextInput("大字体")
        compose.onNodeWithTag("new_draft_tag").performImeAction()
        assertEquals(listOf("大字体"), added)
        compose.onNodeWithTag("apply_tags").assertIsDisplayed()
        compose.runOnIdle { sheet.value = sheet.value.copy(mode = TagEditMode.REMOVE) }
        compose.onNodeWithTag("new_draft_tag").assertDoesNotExist()
        compose.onNodeWithTag("apply_tags").assertIsDisplayed()
    }

    @Test fun longTagsRemainUsableAtLargeFontWithOverflow() {
        val longTags = (0..9).map { BookmarkTag("long-$it", "包含很多文字的标签名称 $it") }
        var opened = ""
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Column(Modifier.requiredSize(320.dp, 480.dp)) { BookmarkTagRow(longTags, emptySet(), { opened = it }) }
            }
        } }
        compose.onNodeWithTag("more_tags").assertIsDisplayed().performClick()
        compose.onNodeWithTag("all_card_tag_long-0").assertIsDisplayed().performClick()
        assertEquals("long-0", opened)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun editor(sheet: MutableState<TagEditorUiState>, added: MutableList<String>, fontScale: Float = 1f,
        onApply: () -> Unit = {}, onConnection: ((InputConnection) -> Unit)? = null) {
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            val content: @Composable () -> Unit = {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Column(Modifier.requiredSize(320.dp, 480.dp)) {
                        TagEditorContent(tags, sheet.value, {}, {}, { raw ->
                            added += raw
                            sheet.value = sheet.value.copy(newNames = sheet.value.newNames + raw, pendingName = "")
                        }, onApply, onPendingChange = { sheet.value = sheet.value.copy(pendingName = it) })
                    }
                }
            }
            if (onConnection == null) content()
            else {
                // Own the test IME session so the OS cannot finish composition between assertions.
                val interceptor = remember(onConnection) {
                    PlatformTextInputInterceptor { request, _ ->
                        onConnection(request.createInputConnection(EditorInfo()))
                        awaitCancellation()
                    }
                }
                InterceptPlatformTextInput(interceptor, content)
            }
        } }
    }
}
