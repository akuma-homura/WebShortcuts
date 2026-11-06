package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.platform.app.InstrumentationRegistry
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.CropRect
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs the production crop editor and bookmark editor; no image download or repository writes. */
class IconEditorInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun draggingTheCropChangesOnlyTheDraftUntilApply() {
        val source = image()
        val initial = CropRect(0.05f, 0.10f, 0.45f, 0.50f)
        val applied = AtomicReference<CropRect?>(null)
        val applyCalls = AtomicInteger()
        val dismissCalls = AtomicInteger()
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                FreeCropContent(source, initial, onDismiss = { dismissCalls.incrementAndGet() }, onApply = {
                    applied.set(it)
                    applyCalls.incrementAndGet()
                })
            }
        }

        dragSelection(source, initial)
        compose.runOnIdle {
            assertNull("Dragging must not commit an image crop", applied.get())
            assertEquals(0, applyCalls.get())
            assertEquals(0, dismissCalls.get())
        }

        compose.onNodeWithTag("apply_crop").assertIsDisplayed().performClick()
        compose.runOnIdle {
            val result = applied.get()
            assertNotNull(result)
            result!!
            assertTrue("The crop must move horizontally", result.left > initial.left + 0.04f)
            assertTrue("The crop must move vertically", result.top > initial.top + 0.03f)
            assertEquals(initial.width, result.width, 0.001f)
            assertEquals(initial.height, result.height, 0.001f)
            assertTrue("This must preserve a freely chosen, off-center selection", result.centerX < 0.48f && result.centerY < 0.48f)
            assertEquals(1, applyCalls.get())
            assertEquals(0, dismissCalls.get())
        }
    }

    @Test
    fun cancellingAnEditedCropDismissesWithoutApplying() {
        val source = image()
        val initial = CropRect(0.05f, 0.10f, 0.45f, 0.50f)
        val applyCalls = AtomicInteger()
        val dismissCalls = AtomicInteger()
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                FreeCropContent(source, initial, onDismiss = { dismissCalls.incrementAndGet() },
                    onApply = { applyCalls.incrementAndGet() })
            }
        }

        dragSelection(source, initial)
        compose.onNodeWithTag("cancel_crop").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, dismissCalls.get())
            assertEquals("Cancel must not submit the edited selection", 0, applyCalls.get())
        }
    }

    @Test
    fun previewControlsChangeTheActualCanvasAndOverlayWithoutSavingAndAutoShowsItsSource() {
        val sourceUrl = "https://cdn.example.test/icons/site-512.png"
        val fetchedAt = 1_799_164_800_000L
        val saveCalls = AtomicInteger()
        val state = HomeUiState(loading = false, editor = EditorUiState(
            id = "icon-preview-interaction", isNew = false, title = "图标交互测试",
            url = "https://example.test/", mode = IconSourceMode.AUTO, preview = image(),
            sourceLabel = "Web App Manifest · 512×512", sourceUrl = sourceUrl, lastFetchedAt = fetchedAt,
        ))
        val resolver = IconResolver(context, IconStore(context), IconProcessor())
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                WebShortcutsApp(
                    state = state,
                    iconResolver = resolver,
                    snackbarHostState = remember { SnackbarHostState() },
                    onQueryChange = {},
                    onAdd = {},
                    onEdit = {},
                    onOpen = {},
                    onPin = {},
                    onUpdateShortcut = {},
                    onRefresh = {},
                    onRefreshAll = {},
                    onDelete = {},
                    onDismissEditor = {},
                    onTitleChange = {},
                    onUrlChange = {},
                    onUrlFocusLost = {},
                    onModeChange = {},
                    onPickImage = {},
                    onRefetchIcon = {},
                    onSave = { saveCalls.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithTag("launcher_preview", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("full_canvas_preview", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("safe_zone_overlay", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("preview_full_canvas").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("full_canvas_preview", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("launcher_preview", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("preview_safe_zone").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("safe_zone_overlay", useUnmergedTree = true).assertExists()

        compose.onNodeWithTag("preview_launcher").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("launcher_preview", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("full_canvas_preview", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("safe_zone_overlay", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("preview_safe_zone").performScrollTo().performClick().assertIsNotSelected()
        compose.onNodeWithTag("safe_zone_overlay", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag("icon_source_url").performScrollTo().assertIsDisplayed().assertTextEquals(sourceUrl)
        val formattedTime = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(fetchedAt))
        compose.onNodeWithTag("icon_sync_time").performScrollTo().assertIsDisplayed()
            .assertTextEquals(context.getString(R.string.last_successful_fetch, formattedTime))
        compose.runOnIdle { assertEquals("Preview aids must never save the bookmark", 0, saveCalls.get()) }
    }

    /** Convert source-relative coordinates through the same Fit geometry used by the real canvas. */
    private fun dragSelection(source: Bitmap, selection: CropRect) {
        val canvas = compose.onNodeWithTag("free_crop_canvas").performScrollTo().assertIsDisplayed()
        val canvasSize = canvas.fetchSemanticsNode().size
        val scale = min(canvasSize.width.toFloat() / source.width, canvasSize.height.toFloat() / source.height)
        val imageWidth = source.width * scale
        val imageHeight = source.height * scale
        val imageLeft = (canvasSize.width - imageWidth) / 2f
        val imageTop = (canvasSize.height - imageHeight) / 2f
        val start = Offset(imageLeft + imageWidth * selection.centerX, imageTop + imageHeight * selection.centerY)
        val end = start + Offset(imageWidth * 0.12f, imageHeight * 0.10f)
        canvas.performTouchInput { swipe(start, end, durationMillis = 400) }
    }

    private fun image(): Bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
}
