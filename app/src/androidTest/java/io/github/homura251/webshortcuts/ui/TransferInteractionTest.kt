package io.github.homura251.webshortcuts.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.homura251.webshortcuts.transfer.ImportMode
import io.github.homura251.webshortcuts.transfer.ImportPreview
import io.github.homura251.webshortcuts.transfer.TransferFormat
import io.github.homura251.webshortcuts.transfer.TransferPhase
import io.github.homura251.webshortcuts.transfer.TransferUiState
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransferInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun replacementRequiresASecondConfirmationAndAppearanceDefaultsOff() {
        val calls = AtomicInteger()
        var state by mutableStateOf(TransferUiState(visible = true, phase = TransferPhase.PREVIEW,
            preview = ImportPreview(TransferFormat.BACKUP_ZIP, 2, 1, 1, 0, 3, true)))
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                TransferContent(state, onModeChange = { state = state.copy(mode = it) },
                    onRestoreAppearance = { state = state.copy(restoreAppearance = it) }, onCommit = { calls.incrementAndGet() })
            }
        }
        compose.onNodeWithTag("restore_appearance").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("import_replace").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_import").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ImportMode.REPLACE, state.mode); assertEquals(0, calls.get()) }
        compose.onNodeWithTag("confirm_replace").performClick()
        compose.runOnIdle { assertEquals(1, calls.get()) }
    }

    @Test fun emptyHtmlCannotClearTheLocalCollectionAndCommittingCannotBeDismissed() {
        var state by mutableStateOf(TransferUiState(visible = true, phase = TransferPhase.PREVIEW,
            preview = ImportPreview(TransferFormat.BOOKMARKS_HTML, 0, 0, 0, 2, 5, false)))
        compose.setContent { WebShortcutsTheme(dynamicColor = false) {
            TransferContent(state, onModeChange = { state = state.copy(mode = it) })
        } }
        compose.onNodeWithTag("confirm_import").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("import_replace").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_import").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(phase = TransferPhase.COMMITTING) }
        compose.onNodeWithTag("close_transfer").assertIsNotEnabled()
    }
}
