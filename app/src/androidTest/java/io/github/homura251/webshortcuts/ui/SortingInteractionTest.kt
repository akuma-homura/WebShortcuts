package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import io.github.homura251.webshortcuts.data.*
import io.github.homura251.webshortcuts.icons.*
import io.github.homura251.webshortcuts.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import java.io.File

@RunWith(AndroidJUnit4::class)
class SortingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val items = (0..29).map { BookmarkItem(id = "site-$it", title = "Site $it", url = "https://site$it.test/",
        selectedIconSource = IconSourceMode.GENERATED, createdAt = it.toLong() + 1) }

    @Test fun homeSortingSheetEntersManualOrganizerAndCancelReturnsToTheCollection() {
        var home by mutableStateOf(HomeUiState(loading = false, bookmarks = items.take(3)))
        compose.setContent {
            val context = LocalContext.current
            WebShortcutsTheme(dynamicColor = false) {
                WebShortcutsApp(home, IconResolver(context, IconStore(context), IconProcessor()), remember { SnackbarHostState() },
                    onQueryChange = {}, onAdd = {}, onEdit = {}, onOpen = {}, onPin = {}, onUpdateShortcut = {},
                    onRefresh = {}, onRefreshAll = {}, onDelete = {}, onDismissEditor = {}, onTitleChange = {},
                    onUrlChange = {}, onUrlFocusLost = {}, onModeChange = {}, onPickImage = {}, onRefetchIcon = {}, onSave = {},
                    sortActions = SortActions(change = { home = home.copy(sorting = it) }, begin = { target, _ ->
                        val ids = home.bookmarks.map { it.id }
                        home = home.copy(reorder = ReorderUiState(target, ids, ids))
                    }, cancel = { home = home.copy(reorder = null) }))
            }
        }
        compose.onNodeWithTag("open_bookmark_sort").performClick()
        compose.onNodeWithTag("sort_bookmarks_CREATED_AT").assertIsSelected()
        clickSortOption("sort_bookmarks_MANUAL")
        compose.onNodeWithTag("organize_order").performClick()
        compose.onNodeWithTag("reorder_screen").assertIsDisplayed()
        compose.onNodeWithTag("cancel_reorder").performClick()
        compose.onNodeWithTag("reorder_screen").assertDoesNotExist()
        compose.onNodeWithTag("bookmark_collection").assertIsDisplayed()
        assertEquals(items.take(3), home.bookmarks)
    }

    @Test fun captureSortingAndOrganizingInBothThemesAndLargeText() {
        var settings by mutableStateOf(true)
        var dark by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        val home = home(8)
        compose.setContent { WebShortcutsTheme(darkTheme = dark, dynamicColor = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                if (settings) Surface(Modifier.fillMaxSize().testTag("sorting_capture")) {
                    Column(Modifier.padding(16.dp)) {
                        Text("收藏排序", style = MaterialTheme.typography.titleLarge)
                        SortingContent(home.sorting, OrderTarget.BOOKMARKS, {}, {})
                    }
                } else ReorderContent(home, SortActions())
            }
        } }
        capture("sorting_capture", "10_sorting_light.png")
        compose.runOnIdle { settings = false }
        capture("reorder_screen", "11_reorder_light.png")
        compose.runOnIdle { dark = true }
        capture("reorder_screen", "12_reorder_dark.png")
        compose.runOnIdle { dark = false; fontScale = 2f }
        capture("reorder_screen", "13_reorder_large_font.png")
    }

    private fun capture(tag: String, name: String) {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag).assertIsDisplayed().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, name).outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test fun automaticModesHideOrganizerAndDirectionsAreIndependentOfManualOrder() {
        var preferences by mutableStateOf(LibrarySortPreferences())
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            Column { SortingContent(preferences, OrderTarget.BOOKMARKS, { preferences = it }, {}) }
        } }
        compose.onNodeWithTag("sort_bookmarks_CREATED_AT").assertIsSelected()
        compose.onNodeWithTag("organize_order").assertDoesNotExist()
        clickSortOption("sort_bookmarks_TITLE")
        assertEquals(SortDirection.ASCENDING, preferences.bookmarkDirection)
        clickSortOption("sort_direction_DESCENDING")
        assertEquals(SortDirection.DESCENDING, preferences.bookmarkDirection)
        clickSortOption("sort_bookmarks_MANUAL")
        compose.onNodeWithTag("organize_order").assertIsDisplayed()
        compose.onNodeWithTag("sort_direction_DESCENDING").assertDoesNotExist()
    }

    @Test fun largeTextCanSelectManualModeAndReachOrganizer() {
        var preferences by mutableStateOf(LibrarySortPreferences())
        var starts = 0
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Column(Modifier.requiredSize(320.dp, 480.dp)) {
                    SortingContent(preferences, OrderTarget.BOOKMARKS, { preferences = it }, { starts++ })
                }
            }
        } }
        compose.onNodeWithTag("sort_options").performScrollToNode(hasTestTag("sort_bookmarks_MANUAL"))
        compose.onNodeWithTag("sort_bookmarks_MANUAL").performClick()
        compose.onNodeWithTag("organize_order").assertIsDisplayed().performClick()
        assertEquals(1, starts)
    }

    @Test fun moveMenuAndAccessibilityActionsWorkWithLargeTextAndBothExitButtonsRemainReachable() {
        var home by mutableStateOf(home(3))
        var saves = 0
        var cancels = 0
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                ReorderContent(home, SortActions(move = { id, index -> home = moved(home, id, index) },
                    save = { saves++ }, cancel = { cancels++ }), Modifier.requiredSize(320.dp, 480.dp))
            }
        } }
        compose.onNodeWithTag("reorder_menu_site-0").performClick()
        compose.onNodeWithTag("reorder_up_site-0").assertIsNotEnabled()
        compose.onNodeWithTag("reorder_down_site-0").performClick()
        assertEquals(listOf("site-1", "site-0", "site-2"), home.reorder!!.orderedIds)
        val accessible = compose.onNodeWithTag("reorder_row_site-0").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(accessible.first().action()) }
        assertEquals("site-0", home.reorder!!.orderedIds.first())
        compose.onNodeWithTag("save_reorder").assertIsDisplayed().performClick()
        compose.onNodeWithTag("cancel_reorder").assertIsDisplayed().performClick()
        assertEquals(1, saves)
        assertEquals(1, cancels)
    }

    @Test fun draggingTheHandleMovesTheDraftAndSavingDisablesControls() {
        var home by mutableStateOf(home(4))
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            ReorderContent(home, SortActions(move = { id, index -> home = moved(home, id, index) }))
        } }
        val first = compose.onNodeWithTag("reorder_drag_site-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("reorder_drag_site-1").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reorder_drag_site-0").performTouchInput {
            down(center); moveBy(Offset(0f, second.center.y - first.center.y + 24f), delayMillis = 80)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(home.reorder!!.orderedIds.indexOf("site-0") > 0)
        compose.runOnIdle { home = home.copy(reorder = home.reorder!!.copy(saving = true)) }
        compose.onNodeWithTag("save_reorder").assertIsNotEnabled()
        compose.onNodeWithTag("cancel_reorder").assertIsNotEnabled()
        compose.onNodeWithTag("reorder_menu_site-0").assertIsNotEnabled()
    }

    @Test fun holdingAHandleNearTheBottomScrollsAcrossMoreThanOneScreen() {
        var home by mutableStateOf(home(30))
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            ReorderContent(home, SortActions(move = { id, index -> home = moved(home, id, index) }), Modifier.requiredSize(360.dp, 480.dp))
        } }
        val start = compose.onNodeWithTag("reorder_drag_site-0").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("reorder_list").fetchSemanticsNode().boundsInRoot
        val bottom = viewport.bottom
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reorder_drag_site-0").performTouchInput {
            down(center); moveBy(Offset(0f, bottom - start.center.y - 8f), delayMillis = 100)
        }
        compose.waitForIdle()
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithTag("reorder_row_site-1").assertIsDisplayed()
        repeat(120) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        assertTrue("Moved only to ${home.reorder!!.orderedIds.indexOf("site-0")}", home.reorder!!.orderedIds.indexOf("site-0") >= 8)
        compose.onRoot().performTouchInput { moveTo(Offset(start.center.x, viewport.top + 4f), delayMillis = 100) }
        compose.waitForIdle()
        repeat(160) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("site-0", home.reorder!!.orderedIds.first())
    }

    @Test fun listAndGridUseTheSameAutomaticOrderAndSortingIsLockedDuringSelection() {
        var layout by mutableStateOf(LayoutMode.LIST)
        var home by mutableStateOf(HomeUiState(loading = false, bookmarks = items.take(3)))
        compose.setContent {
            val context = LocalContext.current
            WebShortcutsTheme(dynamicColor = false) {
                BookmarkCollection(home, IconResolver(context, IconStore(context), IconProcessor()), layout,
                    {}, {}, { layout = it }, {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("bookmark_collection").performScrollToNode(hasTestTag("bookmark_card_site-0"))
        val newest = compose.onNodeWithTag("bookmark_card_site-2").fetchSemanticsNode().boundsInRoot
        val oldest = compose.onNodeWithTag("bookmark_card_site-0").fetchSemanticsNode().boundsInRoot
        assertTrue(newest.top < oldest.top)
        compose.onNodeWithTag("bookmark_collection").performScrollToNode(hasTestTag("layout_toggle"))
        compose.onNodeWithTag("layout_toggle").performClick()
        compose.onNodeWithTag("bookmark_card_site-2").assertIsDisplayed()
        assertEquals(LayoutMode.GRID, layout)
        compose.runOnIdle { home = home.copy(selectionMode = true) }
        compose.onNodeWithTag("open_bookmark_sort").assertIsNotEnabled()
    }

    private fun clickSortOption(tag: String) {
        compose.onNodeWithTag("sort_options").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    private fun home(count: Int): HomeUiState {
        val records = items.take(count)
        val ids = records.map { it.id }
        return HomeUiState(loading = false, bookmarks = records,
            sorting = LibrarySortPreferences(bookmarks = BookmarkSortField.MANUAL),
            reorder = ReorderUiState(OrderTarget.BOOKMARKS, ids, ids))
    }
    private fun moved(home: HomeUiState, id: String, index: Int): HomeUiState {
        val draft = home.reorder!!
        val ids = draft.orderedIds.toMutableList()
        ids.add(index, ids.removeAt(ids.indexOf(id)))
        return home.copy(reorder = draft.copy(orderedIds = ids))
    }
}
