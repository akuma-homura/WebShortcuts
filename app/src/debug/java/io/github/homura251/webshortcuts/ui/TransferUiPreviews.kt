package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.homura251.webshortcuts.transfer.ImportPreview
import io.github.homura251.webshortcuts.transfer.TransferFormat
import io.github.homura251.webshortcuts.transfer.TransferPhase
import io.github.homura251.webshortcuts.transfer.TransferProgress
import io.github.homura251.webshortcuts.transfer.TransferUiState
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme

@Preview(name = "Import export", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
fun TransferIdlePreview() = TransferPreview(TransferUiState(visible = true))

@Preview(name = "Import export dark", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
fun TransferDarkPreview() = TransferPreview(TransferUiState(visible = true), dark = true)

@Preview(name = "ZIP import preview", widthDp = 360, heightDp = 720, showBackground = true)
@Composable
fun TransferZipPreview() = TransferPreview(
    TransferUiState(
        visible = true,
        phase = TransferPhase.PREVIEW,
        preview = ImportPreview(TransferFormat.BACKUP_ZIP, 80, 60, 20, 0, 20, true, 1_799_164_800_000),
    )
)

@Preview(name = "HTML import large font", widthDp = 320, heightDp = 720, fontScale = 2f, showBackground = true)
@Composable
fun TransferLargeFontPreview() = TransferPreview(
    TransferUiState(
        visible = true,
        phase = TransferPhase.PREVIEW,
        preview = ImportPreview(TransferFormat.BOOKMARKS_HTML, 24, 20, 4, 2, 12, false),
    )
)

@Preview(name = "Import commit", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun TransferProgressPreview() = TransferPreview(
    TransferUiState(
        visible = true,
        phase = TransferPhase.COMMITTING,
        progress = TransferProgress("正在恢复图像 12 / 36", 12, 36),
    )
)

@Preview(name = "Result large font", widthDp = 320, heightDp = 720, fontScale = 2f, showBackground = true)
@Composable
fun TransferResultPreview() = TransferPreview(
    TransferUiState(
        visible = true,
        phase = TransferPhase.RESULT,
        message = "已新增 20 条收藏，跳过 4 条重复、2 条无效书签。",
        warnings = listOf("收藏已导入，部分桌面入口更新待重试"),
    )
)

@Composable
private fun TransferPreview(state: TransferUiState, dark: Boolean = false) {
    WebShortcutsTheme(darkTheme = dark, dynamicColor = false) {
        Surface(Modifier.fillMaxSize(), color = AppTheme.surfaces.canvas) { TransferContent(state) }
    }
}
