package io.github.homura251.webshortcuts.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.LayoutMode
import io.github.homura251.webshortcuts.ui.theme.ThemeMode
import io.github.homura251.webshortcuts.ui.theme.ThemePalette
import io.github.homura251.webshortcuts.ui.theme.ThemePreferences
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppearanceInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before @After fun clearAppearance() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun appearanceSheetChangesPersistAndPaletteSelectionLeavesWallpaperMode() {
        val preferences = ThemePreferences(context)
        compose.setContent {
            val appearance by preferences.state.collectAsState()
            WebShortcutsTheme(darkTheme = appearance.themeMode.isDark(false),
                dynamicColor = appearance.dynamicColor, palette = appearance.palette) {
                TestApp(HomeUiState(loading = false), appearance, preferences::update)
            }
        }
        compose.onNodeWithTag("open_appearance").performClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) compose.onNodeWithTag("dynamic_color").assertIsToggleable()
        compose.onNodeWithTag("palette_ocean").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("theme_dark").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("layout_grid").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle {
            val expected = AppearancePreferences(ThemeMode.DARK, false, ThemePalette.OCEAN, LayoutMode.GRID)
            assertEquals(expected, preferences.state.value)
            assertEquals(expected, ThemePreferences(context).state.value)
        }
    }

    @Test fun gridPlacesTwoBookmarksInOneRowAndKeepsTheSiteActionsAccessible() {
        val preferences = mutableStateOf(AppearancePreferences(dynamicColor = false))
        val bookmarks = listOf(
            BookmarkItem(id = "grid-first", title = "GitHub", url = "https://github.com", selectedIconSource = IconSourceMode.GENERATED),
            BookmarkItem(id = "grid-second", title = "Android", url = "https://developer.android.com", selectedIconSource = IconSourceMode.GENERATED),
        )
        var edited: BookmarkItem? = null
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                TestApp(HomeUiState(bookmarks = bookmarks, loading = false), preferences.value,
                    onAppearanceChange = { preferences.value = it }, onEdit = { edited = it })
            }
        }
        compose.onNodeWithContentDescription("切换到网格视图").performClick()
        compose.runOnIdle { assertEquals(LayoutMode.GRID, preferences.value.layoutMode) }
        compose.onNodeWithTag("bookmark_collection").performScrollToNode(hasTestTag("bookmark_card_grid-first"))
        val first = compose.onNodeWithTag("bookmark_card_grid-first").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("bookmark_card_grid-second").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(first.right < second.left)
        compose.onNodeWithContentDescription("GitHub 的操作").performClick()
        compose.onNodeWithText("编辑网站").performClick()
        compose.runOnIdle { assertEquals(bookmarks.first(), edited) }
    }

    @Test fun firstWebsiteActionFitsASmallInitialViewport() {
        val configuration = Configuration(context.resources.configuration).apply {
            screenWidthDp = 320
            screenHeightDp = 480
        }
        var added = false
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                WebShortcutsTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(320.dp, 480.dp)) {
                        TestApp(HomeUiState(loading = false), onAdd = { added = true })
                    }
                }
            }
        }
        compose.onNodeWithText("添加第一个网站").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(added) }
    }

    @Composable
    private fun TestApp(
        state: HomeUiState,
        appearance: AppearancePreferences = AppearancePreferences(),
        onAppearanceChange: (AppearancePreferences) -> Unit = {},
        onAdd: () -> Unit = {},
        onEdit: (BookmarkItem) -> Unit = {},
    ) {
        val resolver = remember { IconResolver(context, IconStore(context), IconProcessor()) }
        WebShortcutsApp(
            state, resolver, remember { SnackbarHostState() }, onQueryChange = {}, onAdd = onAdd,
            onEdit = onEdit, onOpen = {}, onPin = {}, onUpdateShortcut = {}, onRefresh = {},
            onRefreshAll = {}, onDelete = {}, onDismissEditor = {}, onTitleChange = {},
            onUrlChange = {}, onUrlFocusLost = {}, onModeChange = {}, onPickImage = {},
            onRefetchIcon = {}, onSave = {}, appearance = appearance, onAppearanceChange = onAppearanceChange,
        )
    }
}
