package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.transfer.ImportMode
import io.github.homura251.webshortcuts.transfer.TransferFormat
import io.github.homura251.webshortcuts.transfer.TransferPhase
import io.github.homura251.webshortcuts.transfer.TransferUiState
import io.github.homura251.webshortcuts.ui.components.ConnectedActionItem
import io.github.homura251.webshortcuts.ui.components.ConnectedGroup
import io.github.homura251.webshortcuts.ui.components.ConnectedSwitchItem
import io.github.homura251.webshortcuts.ui.components.ExpressiveAlertDialog
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveChoiceOption
import io.github.homura251.webshortcuts.ui.components.ExpressiveConnectedChoices
import io.github.homura251.webshortcuts.ui.components.ExpressiveLoading
import io.github.homura251.webshortcuts.ui.components.ExpressiveModalSheet
import io.github.homura251.webshortcuts.ui.components.ExpressiveProgressBar
import io.github.homura251.webshortcuts.ui.components.ExpressiveStatusPill
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton
import io.github.homura251.webshortcuts.ui.components.SectionHeader
import io.github.homura251.webshortcuts.ui.components.SheetHeader
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.components.TonalEmphasis
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSheet(
    state: TransferUiState,
    onDismiss: () -> Unit,
    onChooseImport: () -> Unit,
    onExport: (TransferFormat) -> Unit,
    onModeChange: (ImportMode) -> Unit,
    onRestoreAppearance: (Boolean) -> Unit,
    onCommit: () -> Unit,
    onRetry: () -> Unit,
    onChooseDestination: () -> Unit,
) {
    val canDismiss by rememberUpdatedState(state.canDismiss)
    ExpressiveModalSheet(
        onDismissRequest = { if (state.canDismiss) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { canDismiss },
        ),
    ) {
        TransferContent(
            state, onDismiss, onChooseImport, onExport, onModeChange, onRestoreAppearance,
            onCommit, onRetry, onChooseDestination,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransferContent(
    state: TransferUiState,
    onDismiss: () -> Unit = {},
    onChooseImport: () -> Unit = {},
    onExport: (TransferFormat) -> Unit = {},
    onModeChange: (ImportMode) -> Unit = {},
    onRestoreAppearance: (Boolean) -> Unit = {},
    onCommit: () -> Unit = {},
    onRetry: () -> Unit = {},
    onChooseDestination: () -> Unit = {},
) {
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("transfer_sheet")
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = AppSpacing.page, end = AppSpacing.page, bottom = AppSpacing.section),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.related),
    ) {
        SheetHeader(
            stringResource(R.string.transfer_title),
            onDismiss,
            supporting = if (state.phase == TransferPhase.IDLE) stringResource(R.string.transfer_subtitle) else null,
            icon = Icons.Default.ImportExport,
            dismissEnabled = state.canDismiss,
            dismissTag = "close_transfer",
        )
        when (state.phase) {
            TransferPhase.IDLE -> {
                TonalCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.content)) {
                    ExpressiveButton(
                        onClick = onChooseImport,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("choose_import"),
                    ) {
                        Icon(Icons.Default.FileDownload, null, Modifier.padding(end = 8.dp).size(20.dp))
                        Text(stringResource(R.string.transfer_choose))
                    }
                }
                ConnectedGroup {
                    TransferExportOption(
                        title = R.string.transfer_export_zip,
                        detail = R.string.transfer_zip_detail,
                        icon = Icons.Default.FolderZip,
                        tag = "export_zip",
                        index = 0,
                    ) { onExport(TransferFormat.BACKUP_ZIP) }
                    TransferExportOption(
                        title = R.string.transfer_export_html,
                        detail = R.string.transfer_html_detail,
                        icon = Icons.Default.Language,
                        tag = "export_html",
                        index = 1,
                    ) { onExport(TransferFormat.BOOKMARKS_HTML) }
                }
                SupportingText(stringResource(R.string.transfer_backup_note))
            }
            TransferPhase.PREVIEW -> state.preview?.let { preview ->
                TonalCard(Modifier.fillMaxWidth()) {
                    SectionHeader(stringResource(R.string.transfer_preview_title))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ExpressiveStatusPill(
                            text = stringResource(
                                if (preview.format == TransferFormat.BACKUP_ZIP) R.string.transfer_zip_format else R.string.transfer_html_format
                            ),
                            emphasis = TonalEmphasis.PRIMARY,
                        )
                        preview.exportedAt?.let {
                            ExpressiveStatusPill(
                                text = stringResource(
                                    R.string.transfer_exported_at,
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)),
                                ),
                                emphasis = TonalEmphasis.SECONDARY,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.transfer_preview_counts, preview.itemCount, preview.duplicateCount, preview.skippedCount),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag("import_counts"),
                    )
                    SupportingText(
                        stringResource(
                            if (state.mode == ImportMode.MERGE) R.string.transfer_new_counts else R.string.transfer_replace_counts,
                            if (state.mode == ImportMode.MERGE) preview.newCount else preview.itemCount,
                            preview.localCount,
                        )
                    )
                    if (state.mode == ImportMode.MERGE) SupportingText(stringResource(R.string.transfer_keep_local))
                    if (preview.format == TransferFormat.BOOKMARKS_HTML) SupportingText(stringResource(R.string.transfer_html_note))
                    if (preview.format == TransferFormat.BACKUP_ZIP && state.mode == ImportMode.MERGE) {
                        SupportingText(stringResource(R.string.transfer_tag_counts, preview.tagCount, preview.newTagCount, preview.tagUpdatedCount))
                    }
                    if (preview.format == TransferFormat.BACKUP_ZIP && state.mode == ImportMode.REPLACE) {
                        SupportingText(stringResource(R.string.transfer_tag_total, preview.tagCount))
                    }
                    if (state.mode == ImportMode.REPLACE) {
                        SupportingText(
                            stringResource(
                                if (preview.tagCount == 0 && preview.localTagCount > 0) R.string.transfer_no_tags_replace
                                else R.string.transfer_tags_replace
                            )
                        )
                    }
                }
                ExpressiveConnectedChoices(
                    options = listOf(
                        ExpressiveChoiceOption(ImportMode.MERGE, stringResource(R.string.transfer_merge), testTag = "import_merge"),
                        ExpressiveChoiceOption(ImportMode.REPLACE, stringResource(R.string.transfer_replace), testTag = "import_replace"),
                    ),
                    selected = state.mode,
                    onSelect = onModeChange,
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.mode == ImportMode.MERGE && preview.mergedTagCount > 2000) SupportingText(stringResource(R.string.transfer_tag_limit))
                if (preview.hasAppearance) {
                    ConnectedSwitchItem(
                        index = 0,
                        count = 1,
                        title = stringResource(R.string.transfer_restore_appearance),
                        icon = Icons.Default.Palette,
                        checked = state.restoreAppearance,
                        onCheckedChange = onRestoreAppearance,
                        modifier = Modifier.testTag("restore_appearance"),
                    )
                }
                preview.warnings.forEach { SupportingText(it) }
                if (!preview.canReplace) SupportingText(stringResource(R.string.transfer_empty_html))
                if (state.mode == ImportMode.MERGE && preview.localCount + preview.newCount > 2000) SupportingText(stringResource(R.string.transfer_limit))
                ExpressiveButton(
                    onClick = { if (state.mode == ImportMode.REPLACE) confirmReplace = true else onCommit() },
                    enabled = if (state.mode == ImportMode.MERGE) preview.canMerge else preview.canReplace,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirm_import"),
                ) {
                    Text(stringResource(R.string.transfer_confirm))
                }
                ExpressiveTextButton(onClick = onRetry) { Text(stringResource(R.string.transfer_retry)) }
            }
            TransferPhase.PICK_IMPORT -> SupportingText(stringResource(R.string.transfer_choose))
            TransferPhase.EXPORT_READY, TransferPhase.PICK_EXPORT -> {
                TonalCard(Modifier.fillMaxWidth()) {
                    SupportingText(stringResource(R.string.transfer_waiting_destination))
                    ExpressiveTonalButton(onClick = onChooseDestination, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.transfer_choose_destination))
                    }
                }
            }
            TransferPhase.RESULT, TransferPhase.ERROR -> {
                TonalCard(
                    Modifier.fillMaxWidth(),
                    emphasis = if (state.phase == TransferPhase.ERROR) TonalEmphasis.ERROR else TonalEmphasis.PRIMARY,
                ) {
                    if (state.phase == TransferPhase.RESULT) SectionHeader(stringResource(R.string.transfer_result))
                    Text(state.message.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("transfer_result"))
                }
                state.warnings.forEach { SupportingText(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.transfer_done))
                    }
                    ExpressiveTextButton(onClick = onRetry) { Text(stringResource(R.string.transfer_retry)) }
                }
            }
            else -> {
                TonalCard(Modifier.fillMaxWidth()) {
                    val fraction = state.progress.fraction
                    if (fraction == null) ExpressiveLoading()
                    else ExpressiveProgressBar(progress = { fraction })
                    Text(state.progress.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("transfer_progress"))
                    if (state.phase == TransferPhase.COMMITTING) SupportingText(stringResource(R.string.transfer_committing))
                }
            }
        }
        if (state.working && state.canDismiss) {
            ExpressiveTextButton(onClick = onDismiss) { Text(stringResource(R.string.transfer_cancel_operation)) }
        }
    }
    if (confirmReplace && state.phase == TransferPhase.PREVIEW) {
        ExpressiveAlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(R.string.transfer_replace_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.transfer_replace_body, state.preview?.localCount ?: 0, state.preview?.itemCount ?: 0))
                    Text(
                        stringResource(
                            if (state.preview?.tagCount == 0 && (state.preview?.localTagCount ?: 0) > 0) {
                                R.string.transfer_no_tags_replace
                            } else {
                                R.string.transfer_tags_replace
                            }
                        )
                    )
                }
            },
            confirmButton = {
                ExpressiveTextButton(
                    onClick = { confirmReplace = false; onCommit() },
                    destructive = true,
                    modifier = Modifier.testTag("confirm_replace"),
                ) {
                    Text(stringResource(R.string.transfer_replace_confirm))
                }
            },
            dismissButton = {
                ExpressiveTextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SupportingText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TransferExportOption(title: Int, detail: Int, icon: ImageVector, tag: String, index: Int, onClick: () -> Unit) {
    ConnectedActionItem(
        index = index,
        count = 2,
        title = stringResource(title),
        onClick = onClick,
        supporting = stringResource(detail),
        icon = icon,
        modifier = Modifier.testTag(tag),
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
    )
}
