package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.LayoutMode
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Device-rendered QA captures. All sample content is isolated to this instrumentation source set. */
class UiScreenshotsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun captureHomeThemesAndCustomIconFraming() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val processor = IconProcessor()
        val resolver = IconResolver(context, IconStore(context), processor)
        val uiState = mutableStateOf(HomeUiState(loading = false))
        val darkTheme = mutableStateOf(false)
        val appearance = mutableStateOf(AppearancePreferences(dynamicColor = false))
        val source = landscapeFixture()
        val fit = processor.normalize(source, centerCrop = false)
        val cropped = processor.normalize(source, centerCrop = true)
        // Keep the capture order deterministic and the awaited first icon in the visible lazy row.
        val fixtures = listOf(
            BookmarkItem(
                id = "2bed5c71-95f5-4d9b-86d5-5a2a3c917d34", title = "GitHub", url = "https://github.com",
                selectedIconSource = IconSourceMode.GENERATED, createdAt = 3L,
            ),
            BookmarkItem(
                id = "36bc7a49-0a31-4c2d-a6df-99d25fbff1a0", title = "Android 开发者", url = "https://developer.android.com",
                selectedIconSource = IconSourceMode.AUTO, createdAt = 2L,
            ),
            BookmarkItem(
                id = "55449913-ae1c-45f4-921f-d4892f61b9d7", title = "维基百科", url = "https://zh.wikipedia.org",
                selectedIconSource = IconSourceMode.GENERATED, createdAt = 1L,
            ),
        )
        compose.setContent {
            WebShortcutsTheme(darkTheme = darkTheme.value, dynamicColor = false) {
                WebShortcutsApp(
                    state = uiState.value,
                    iconResolver = resolver,
                    snackbarHostState = remember { SnackbarHostState() },
                    onQueryChange = { uiState.value = uiState.value.copy(query = it) },
                    onAdd = {}, onEdit = {}, onOpen = {}, onPin = {}, onUpdateShortcut = {},
                    onRefresh = {}, onRefreshAll = {}, onDelete = {}, onDismissEditor = {},
                    onTitleChange = {}, onUrlChange = {}, onUrlFocusLost = {}, onModeChange = {},
                    onPickImage = {}, onRefetchIcon = {}, onSave = {},
                    appearance = appearance.value, onAppearanceChange = { appearance.value = it },
                    onCenterCropChange = { crop ->
                        uiState.value = uiState.value.copy(
                            editor = uiState.value.editor?.copy(centerCrop = crop, preview = if (crop) cropped else fit),
                        )
                    },
                )
            }
        }
        // This assertion catches the first-run CTA falling below the initial mobile viewport.
        compose.onNodeWithText("添加第一个网站").assertIsDisplayed()
        capture("home_screen", "01_empty_light.png")

        compose.runOnIdle { uiState.value = HomeUiState(bookmarks = fixtures, loading = false) }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("bookmark_icon_${fixtures.first().id}", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        capture("home_screen", "02_home_light.png")
        compose.runOnIdle { darkTheme.value = true }
        capture("home_screen", "03_home_dark.png")

        compose.runOnIdle {
            darkTheme.value = false
            appearance.value = appearance.value.copy(layoutMode = LayoutMode.GRID)
        }
        capture("home_screen", "08_home_grid.png")
        compose.onNodeWithTag("open_appearance").performClick()
        capture("appearance_sheet", "09_appearance.png")
        compose.onNodeWithTag("close_appearance").performClick()

        compose.runOnIdle {
            darkTheme.value = false
            uiState.value = uiState.value.copy(editor = EditorUiState(
                id = "screenshot-auto", isNew = true, title = "我的工作台", url = "https://example.com",
                mode = IconSourceMode.AUTO, preview = resolver.generated("我的工作台", "https://example.com"),
                error = "未能获取高清图标，已使用默认字母图标", dirty = true,
            ))
        }
        capture("editor_screen", "18_editor_details.png")
        compose.onNodeWithText("未能获取高清图标，已使用默认字母图标").performScrollTo().assertIsDisplayed()
        capture("editor_screen", "04_editor_auto_fallback.png")

        compose.runOnIdle {
            uiState.value = uiState.value.copy(editor = EditorUiState(
                id = "screenshot-custom", isNew = false, title = "旅行手记", url = "https://example.com/journal",
                mode = IconSourceMode.CUSTOM, preview = fit, hasCustomImage = true,
                canCrop = true, centerCrop = false, dirty = true,
            ))
        }
        compose.onNodeWithTag("crop_fit").performScrollTo().assertIsDisplayed()
        capture("editor_screen", "05_editor_custom_fit.png")
        compose.onNodeWithTag("crop_center").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(uiState.value.editor?.centerCrop == true) }
        capture("editor_screen", "06_editor_custom_crop.png")
        compose.onNodeWithText("网站名称").performScrollTo().assertIsDisplayed()
        capture("editor_screen", "07_editor_form.png")
        // Leave the content without references to the fixture bitmaps before recycling them.
        compose.runOnIdle { uiState.value = HomeUiState(loading = false) }
        compose.waitForIdle()
        source.recycle()
        fit.recycle()
        cropped.recycle()
    }

    private fun capture(tag: String, name: String) {
        compose.waitForIdle()
        val image = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, name).outputStream().use { stream ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    /** A deliberately wide, synthetic test image makes fit versus center crop visually inspectable. */
    private fun landscapeFixture(): Bitmap {
        val bitmap = Bitmap.createBitmap(720, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(239, 234, 218))
        paint.color = Color.rgb(204, 137, 87)
        canvas.drawCircle(540f, 110f, 52f, paint)
        paint.color = Color.rgb(136, 169, 146)
        canvas.drawOval(RectF(-150f, 150f, 470f, 660f), paint)
        paint.color = Color.rgb(56, 109, 96)
        canvas.drawOval(RectF(220f, 190f, 900f, 720f), paint)
        paint.color = Color.rgb(255, 248, 224)
        paint.strokeWidth = 9f
        canvas.drawLine(90f, 70f, 230f, 70f, paint)
        return bitmap
    }
}
