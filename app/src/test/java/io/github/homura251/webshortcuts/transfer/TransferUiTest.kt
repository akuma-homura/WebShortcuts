package io.github.homura251.webshortcuts.transfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.ui.TransferContent
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Runs important destructive-action and accessibility behavior without requiring an emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransferUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replacementCannotCommitBeforeItsSecondConfirmation() {
        val commits = AtomicInteger()
        var state by mutableStateOf(TransferUiState(visible = true, phase = TransferPhase.PREVIEW,
            preview = ImportPreview(TransferFormat.BACKUP_ZIP, 2, 1, 1, 0, 3, true)))
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            TransferContent(state, onModeChange = { state = state.copy(mode = it) }, onCommit = { commits.incrementAndGet() })
        } }
        compose.onNodeWithTag("import_replace").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_import").performScrollTo().performClick()
        assertEquals(0, commits.get())
        compose.onNodeWithTag("confirm_replace").assertIsDisplayed().performClick()
        assertEquals(1, commits.get())
    }

    @Test fun enlargedTextCanScrollToConfirmationAndCommittingDisablesClose() {
        val commits = AtomicInteger()
        var state by mutableStateOf(TransferUiState(visible = true, phase = TransferPhase.PREVIEW,
            preview = ImportPreview(TransferFormat.BOOKMARKS_HTML, 24, 20, 4, 2, 12, false)))
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Box(Modifier.requiredSize(320.dp, 480.dp)) {
                    TransferContent(state, onCommit = { commits.incrementAndGet() })
                }
            }
        } }
        compose.onNodeWithTag("confirm_import").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, commits.get())
        compose.runOnIdle { state = state.copy(phase = TransferPhase.COMMITTING) }
        compose.onNodeWithTag("close_transfer").assertIsNotEnabled()
    }
}
