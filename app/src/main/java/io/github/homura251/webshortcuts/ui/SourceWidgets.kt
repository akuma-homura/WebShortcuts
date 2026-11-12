package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.shortcuts.BrowserCatalog
import io.github.homura251.webshortcuts.shortcuts.BrowserOption
import io.github.homura251.webshortcuts.ui.components.ConnectedActionItem
import io.github.homura251.webshortcuts.ui.components.ConnectedChoiceItem
import io.github.homura251.webshortcuts.ui.components.ConnectedGroup
import io.github.homura251.webshortcuts.ui.components.ExpressiveLoading
import io.github.homura251.webshortcuts.ui.components.ExpressiveModalSheet
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton
import io.github.homura251.webshortcuts.ui.components.SectionHeader
import io.github.homura251.webshortcuts.ui.components.SheetHeader
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.components.TonalEmphasis
import io.github.homura251.webshortcuts.ui.theme.AppSpacing

@Composable
internal fun browserLabel(packageName: String?, browsers: List<BrowserOption>): String =
    if (packageName == null) stringResource(R.string.browser_automatic)
    else browsers.firstOrNull { it.packageName == packageName }?.label ?: BrowserCatalog.knownLabel(packageName)

@Composable
internal fun BrowserAndTagsSection(
    packageName: String?,
    browsers: List<BrowserOption>,
    tagSummary: String,
    hasTags: Boolean,
    enabled: Boolean,
    onChooseBrowser: () -> Unit,
    onEditTags: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.browser_section))
        ConnectedGroup {
            ConnectedActionItem(
                index = 0,
                count = 2,
                title = browserLabel(packageName, browsers),
                onClick = onChooseBrowser,
                supporting = stringResource(R.string.browser_section_hint),
                icon = Icons.Default.Language,
                enabled = enabled,
                modifier = Modifier.testTag("editor_browser"),
                trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
            )
            ConnectedActionItem(
                index = 1,
                count = 2,
                title = tagSummary,
                onClick = onEditTags,
                supporting = if (hasTags) stringResource(R.string.bookmark_tags) else null,
                icon = Icons.AutoMirrored.Filled.Label,
                enabled = enabled,
                modifier = Modifier.testTag("editor_tags"),
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserPicker(
    packageName: String?,
    browsers: List<BrowserOption>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ExpressiveModalSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("browser_picker_sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = AppSpacing.page, end = AppSpacing.page, bottom = AppSpacing.section),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.section),
        ) {
            SheetHeader(
                stringResource(R.string.browser_picker_title),
                onDismiss,
                supporting = stringResource(R.string.browser_accounts_hint),
                icon = Icons.Default.Language,
            )
            val missing = packageName != null && browsers.none { it.packageName == packageName }
            val count = browsers.size + 1 + if (missing) 1 else 0
            ConnectedGroup {
                ConnectedChoiceItem(
                    index = 0,
                    count = count,
                    title = stringResource(R.string.browser_automatic),
                    selected = packageName == null,
                    onClick = { onSelect(null); onDismiss() },
                    supporting = stringResource(R.string.browser_automatic_hint),
                    icon = Icons.Default.Public,
                    modifier = Modifier.testTag("browser_automatic"),
                )
                browsers.forEachIndexed { index, browser ->
                    ConnectedChoiceItem(
                        index = index + 1,
                        count = count,
                        title = browser.label,
                        selected = packageName == browser.packageName,
                        onClick = { onSelect(browser.packageName); onDismiss() },
                        supporting = stringResource(
                            if (browser.supportsCustomTabs) R.string.browser_cct_hint else R.string.browser_external_hint
                        ),
                        icon = Icons.Default.Language,
                        modifier = Modifier.testTag("browser_${browser.packageName}"),
                    )
                }
                if (missing) {
                    ConnectedChoiceItem(
                        index = count - 1,
                        count = count,
                        title = browserLabel(packageName, browsers),
                        selected = true,
                        onClick = {},
                        supporting = stringResource(R.string.browser_unavailable),
                        icon = Icons.Default.WarningAmber,
                        enabled = false,
                        modifier = Modifier.testTag("browser_unavailable"),
                    )
                }
            }
            if (browsers.isEmpty()) {
                Text(
                    stringResource(R.string.browser_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun LocalDocumentSection(state: EditorUiState, onReplace: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.local_document_title))
        TonalCard(Modifier.fillMaxWidth(), emphasis = TonalEmphasis.TERTIARY) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Description, null, Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.localDisplayName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("local_document_name"),
                    )
                    Text(stringResource(R.string.local_document_ready), style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Default.CheckCircle, null)
            }
            Text(stringResource(R.string.local_document_hint), style = MaterialTheme.typography.bodyMedium)
            if (state.importingFile) {
                ExpressiveLoading(label = stringResource(R.string.local_document_importing))
            } else {
                ExpressiveTonalButton(onReplace, enabled = !state.saving, modifier = Modifier.testTag("replace_local_document")) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.local_document_replace))
                }
            }
        }
    }
}
