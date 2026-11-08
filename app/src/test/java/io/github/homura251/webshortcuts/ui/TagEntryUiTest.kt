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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private val tags = listOf(BookmarkTag("docs", "Docs"), BookmarkTag("work", "Work"))

    @Test fun imeSearchFiltersTheSingleInputWithoutAdding() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithTag("new_draft_tag").assertDoesNotExist()
        compose.onNodeWithTag("edit_tag_search").performTextInput("Work")
        compose.onNodeWithTag("edit_tag_docs").assertDoesNotExist()
        compose.onNodeWithTag("edit_tag_work").assertIsDisplayed()
        compose.onNodeWithTag("edit_tag_search").performImeAction()
        compose.onNodeWithTag("edit_tag_search").assertIsNotFocused().assertTextContains("Work")
        compose.onNodeWithTag("tag_pending_save_hint").assertTextEquals("保存时选择「Work」")
        assertTrue(added.isEmpty())
        assertTrue(sheet.value.selectedIds.isEmpty())
    }

    @Test fun plusAddsConsecutiveNamesAndKeepsFocus() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added)
        compose.onNodeWithTag("create_draft_tag").assertIsNotEnabled()
        compose.onNodeWithTag("edit_tag_search").performTextInput("新标签")
        compose.onNodeWithTag("create_draft_tag").performClick()
        compose.onNodeWithTag("edit_tag_search").assertIsFocused().performTextInput("Second")
        compose.onNodeWithTag("create_draft_tag").performClick()
        assertEquals(listOf("新标签", "Second"), added)
        assertEquals("", sheet.value.pendingName)
        compose.onNodeWithTag("draft_tag_新标签").assertIsDisplayed()
        compose.onNodeWithTag("create_draft_tag").assertIsNotEnabled()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun hardwareEnterAndNumpadEnterSearchWithoutAdding() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added)
        compose.onNodeWithTag("edit_tag_search").performTextInput("Work")
        compose.onNodeWithTag("edit_tag_search").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithTag("edit_tag_search").assertIsNotFocused().performClick()
        compose.onNodeWithTag("edit_tag_search").performKeyInput { pressKey(Key.NumPadEnter) }
        compose.onNodeWithTag("edit_tag_search").assertIsNotFocused()
        assertEquals("Work", sheet.value.pendingName)
        assertTrue(added.isEmpty())
    }

    @Test fun saveReceivesUnconfirmedTextAndShowsItsEffect() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        var saved = ""
        editor(sheet, added, onApply = { saved = sheet.value.pendingName })
        compose.onNodeWithTag("edit_tag_search").performTextInput("Pending")
        compose.onNodeWithTag("tag_pending_save_hint").assertTextEquals("保存时添加「Pending」")
        compose.onNodeWithTag("edit_tag_search").performImeAction()
        assertTrue(added.isEmpty())
        compose.onNodeWithTag("apply_tags").performClick()
        assertEquals("Pending", saved)
        assertTrue(added.isEmpty())
    }

    @Test fun chineseCompositionDisablesAddAndSaveAndSearchDoesNotAdd() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        val connection = AtomicReference<InputConnection>()
        editor(sheet, added, onConnection = connection::set)
        compose.onNodeWithTag("edit_tag_search").performClick()
        compose.waitUntil(5000) { connection.get() != null }
        compose.runOnIdle { assertTrue(connection.get().setComposingText("zhong", 1)) }
        compose.onNodeWithTag("apply_tags").assertIsNotEnabled()
        compose.onNodeWithTag("create_draft_tag").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(connection.get().performEditorAction(EditorInfo.IME_ACTION_SEARCH)) }
        compose.waitForIdle()
        compose.onNodeWithTag("edit_tag_search").assertIsFocused()
        compose.onNodeWithTag("apply_tags").assertIsNotEnabled()
        assertTrue(added.isEmpty())
        compose.runOnIdle { assertTrue(connection.get().commitText("中", 1)) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(connection.get().performEditorAction(EditorInfo.IME_ACTION_SEARCH)) }
        compose.waitForIdle()
        compose.onNodeWithTag("edit_tag_search").assertIsNotFocused()
        assertTrue(added.isEmpty())
        compose.onNodeWithTag("create_draft_tag").performClick()
        assertEquals(listOf("中"), added)
    }

    @Test fun managerUsesOneInputAndRestoresFocusAfterAsyncAdd() {
        var home by mutableStateOf(HomeUiState(tags = tags))
        val added = mutableListOf<String>()
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            Column(Modifier.requiredSize(360.dp, 480.dp)) {
                TagManagerContent(home, { added += it; home = home.copy(tagSaving = true) }, { _, _ -> }, {},
                    onNewNameChange = { home = home.copy(newTagName = it) })
            }
        } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        compose.onNodeWithTag("manage_tag_name").assertDoesNotExist()
        compose.onNodeWithTag("manage_tag_search").performTextInput("Work")
        compose.onNodeWithTag("manage_tag_search").performImeAction()
        assertTrue(added.isEmpty())
        compose.onNodeWithText("Docs").assertDoesNotExist()
        compose.onNodeWithTag("manage_tag_search").performTextReplacement("Fresh")
        compose.onNodeWithTag("create_tag").performClick()
        assertEquals(listOf("Fresh"), added)
        compose.onNodeWithTag("create_tag").assertIsNotEnabled().performTouchInput { click() }
        compose.onNodeWithTag("manage_tag_search").assertIsNotEnabled()
        assertEquals(1, added.size)
        compose.runOnIdle { home = home.copy(tags = tags.reversed() + BookmarkTag("fresh", "Fresh"), newTagName = "", tagSaving = false) }
        compose.onNodeWithTag("manage_tag_search").assertIsFocused()
        assertEquals("", home.newTagName)
        compose.onNodeWithText("Docs").assertIsDisplayed()
        compose.onNodeWithTag("create_tag").assertIsNotEnabled()
    }

    @Test fun selectedAndDraftTagsHaveIndependentRemoveButtons() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one"), selectedIds = setOf("docs"),
            newNames = listOf("Pending"), pendingName = "Elsewhere"))
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        editor(sheet, added, onRemove = { removed += it })
        // Chosen tags remain reachable even when the search does not match them.
        compose.onNodeWithTag("edit_tag_docs_remove").assertIsDisplayed()
        val removeBounds = compose.onNodeWithTag("edit_tag_docs_remove").fetchSemanticsNode().boundsInRoot
        assertTrue(removeBounds.width / compose.density.density >= 48f)
        assertTrue(removeBounds.height / compose.density.density >= 48f)
        compose.runOnIdle { sheet.value = sheet.value.copy(saving = true) }
        compose.onNodeWithTag("edit_tag_docs_remove").assertIsNotEnabled()
        compose.runOnIdle { sheet.value = sheet.value.copy(saving = false) }
        compose.onNodeWithTag("edit_tag_docs_remove").performTouchInput { click() }
        assertEquals(listOf("docs"), removed)
        assertTrue(sheet.value.selectedIds.isEmpty())
        assertEquals("", sheet.value.pendingName)
        compose.onNodeWithTag("edit_tag_docs").assertIsDisplayed().assertIsOff()
        compose.onNodeWithTag("edit_tag_docs_remove").assertDoesNotExist()
        compose.onNodeWithTag("edit_tag_work_remove").assertDoesNotExist()
        compose.onNodeWithTag("draft_tag_Pending_remove").performTouchInput { click() }
        assertTrue(sheet.value.newNames.isEmpty())
        compose.onNodeWithTag("draft_tag_Pending").assertDoesNotExist()
        assertTrue(added.isEmpty())
    }

    @Test fun selectingATagClearsTheSearchBeforeSave() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        var saved = "not saved"
        editor(sheet, added, onApply = { saved = sheet.value.pendingName })
        compose.onNodeWithTag("edit_tag_search").performTextInput("do")
        compose.onNodeWithTag("edit_tag_docs").performClick()
        assertEquals(setOf("docs"), sheet.value.selectedIds)
        compose.onNodeWithTag("tag_pending_save_hint").assertDoesNotExist()
        compose.onNodeWithTag("apply_tags").performClick()
        assertEquals("", saved)
        assertTrue(added.isEmpty())
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
        assertTrue(first.height / compose.density.density >= 48f)
    }

    @Test fun largeFontKeepsAddAndSaveReachableAndRemovalOnlySearches() {
        val sheet = mutableStateOf(TagEditorUiState(setOf("one")))
        val added = mutableListOf<String>()
        editor(sheet, added, fontScale = 2f)
        compose.onNodeWithTag("edit_tag_search").assertIsDisplayed().performTextInput("大字体")
        compose.onNodeWithTag("create_draft_tag").assertIsDisplayed().performClick()
        assertEquals(listOf("大字体"), added)
        compose.onNodeWithTag("apply_tags").assertIsDisplayed()
        compose.runOnIdle { sheet.value = sheet.value.copy(mode = TagEditMode.REMOVE, pendingName = "", newNames = emptyList()) }
        compose.onNodeWithTag("create_draft_tag").assertDoesNotExist()
        compose.onNodeWithTag("tag_pending_save_hint").assertDoesNotExist()
        compose.onNodeWithTag("edit_tag_search").performTextInput("Work")
        compose.onNodeWithTag("edit_tag_search").performImeAction()
        compose.onNodeWithTag("edit_tag_work").assertIsDisplayed()
        compose.onNodeWithTag("edit_tag_docs").assertDoesNotExist()
        compose.onNodeWithTag("apply_tags").assertIsDisplayed()
        assertEquals(listOf("大字体"), added)
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
        onApply: () -> Unit = {}, onRemove: (String) -> Unit = {}, onConnection: ((InputConnection) -> Unit)? = null) {
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            val content: @Composable () -> Unit = {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Column(Modifier.requiredSize(320.dp, 480.dp)) {
                        TagEditorContent(tags, sheet.value, { id ->
                            val current = sheet.value
                            sheet.value = current.copy(selectedIds = if (id in current.selectedIds) current.selectedIds - id else current.selectedIds + id,
                                pendingName = "")
                        }, { name -> sheet.value = sheet.value.copy(newNames = sheet.value.newNames - name, pendingName = "") }, { raw ->
                            added += raw
                            sheet.value = sheet.value.copy(newNames = sheet.value.newNames + raw, pendingName = "")
                        }, onApply, onPendingChange = { sheet.value = sheet.value.copy(pendingName = it) }, onRemoveSelected = { id ->
                            onRemove(id)
                            sheet.value = sheet.value.copy(selectedIds = sheet.value.selectedIds - id, pendingName = "")
                        })
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
