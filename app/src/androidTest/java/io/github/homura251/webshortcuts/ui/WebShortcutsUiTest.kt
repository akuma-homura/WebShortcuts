package io.github.homura251.webshortcuts.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Covers the first-run action and the editor's two potentially destructive interactions. */
class WebShortcutsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun resolver() = IconResolver(context, IconStore(context), IconProcessor())

    @Test
    fun emptyLibraryOffersAWorkingAddAction() {
        var added = false
        show(HomeUiState(loading = false), onAdd = { added = true })
        compose.onNodeWithText("添加第一个网站").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(added) }
    }

    @Test
    fun closingDirtyEditorRequiresDiscardAndCanContinueEditing() {
        var dismissed = false
        show(
            HomeUiState(loading = false, editor = EditorUiState(id = "test", isNew = true, title = "我的网站", dirty = true)),
            onDismiss = { dismissed = true },
        )
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("放弃这次修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.runOnIdle { assertEquals(false, dismissed) }
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun selectingCustomModeRequestsAnExplicitModeChange() {
        var selected: IconSourceMode? = null
        show(
            HomeUiState(loading = false, editor = EditorUiState(id = "test", isNew = true)),
            onMode = { selected = it },
        )
        compose.onNodeWithText("自定义").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(IconSourceMode.CUSTOM, selected) }
    }

    private fun show(
        state: HomeUiState,
        onAdd: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onMode: (IconSourceMode) -> Unit = {},
    ) {
        val iconResolver = resolver()
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                WebShortcutsApp(
                    state = state,
                    iconResolver = iconResolver,
                    snackbarHostState = remember { SnackbarHostState() },
                    onQueryChange = {},
                    onAdd = onAdd,
                    onEdit = {},
                    onOpen = {},
                    onPin = {},
                    onUpdateShortcut = {},
                    onRefresh = {},
                    onRefreshAll = {},
                    onDelete = {},
                    onDismissEditor = onDismiss,
                    onTitleChange = {},
                    onUrlChange = {},
                    onUrlFocusLost = {},
                    onModeChange = onMode,
                    onPickImage = {},
                    onRefetchIcon = {},
                    onSave = {},
                )
            }
        }
    }
}
