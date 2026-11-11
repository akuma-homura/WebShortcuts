package io.github.homura251.webshortcuts.ui

import io.github.homura251.webshortcuts.shortcuts.BrowserOption
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.components.SectionHeader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import io.github.homura251.webshortcuts.ui.components.ExpressiveAlertDialog as AlertDialog
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton as Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton as FilledTonalButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveFilterChip as FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton as TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.data.BookmarkTag
import io.github.homura251.webshortcuts.icons.CropRect
import io.github.homura251.webshortcuts.ui.theme.animatedIconTint
import io.github.homura251.webshortcuts.ui.theme.rememberIconAccent
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BookmarkEditor(
    state: EditorUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlFocusLost: () -> Unit,
    onModeChange: (IconSourceMode) -> Unit,
    onPickImage: () -> Unit,
    onRefetchIcon: () -> Unit,
    onSave: () -> Unit,
    onCenterCropChange: (Boolean) -> Unit,
    onCropApply: (CropRect?) -> Unit,
    tags: List<BookmarkTag>,
    onEditTags: () -> Unit,
    browsers: List<BrowserOption> = emptyList(),
    onBrowserChange: (String?) -> Unit = {},
    onImportLocal: () -> Unit = {},
) {
    var showBrowserPicker by rememberSaveable(state.id) { mutableStateOf(false) }
    var askDiscard by rememberSaveable(state.id) { mutableStateOf(false) }
    var roundPreview by rememberSaveable(state.id) { mutableStateOf(false) }
    var fullCanvasPreview by rememberSaveable(state.id) { mutableStateOf(false) }
    var showSafeZone by rememberSaveable(state.id) { mutableStateOf(false) }
    var showCrop by rememberSaveable(state.id) { mutableStateOf(false) }
    var urlWasFocused by remember(state.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val editorScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val processingCustomImage = (state.loading && state.mode == IconSourceMode.CUSTOM) || state.importingFile
    val requestDismiss: () -> Unit = {
        if (!state.saving && !state.importingFile) {
            if (state.dirty) askDiscard = true else onDismiss()
        }
    }
    EditorWindow(onDismiss = requestDismiss) {
        val view = LocalView.current
        val lightSystemBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { dialogWindow ->
                WindowCompat.getInsetsController(dialogWindow, view).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
        }
        Surface(Modifier.fillMaxSize().testTag("editor_screen"), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                modifier = Modifier.imePadding().nestedScroll(editorScrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    CenterAlignedTopAppBar(
                        scrollBehavior = editorScrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        title = { Text(stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title),
                            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = requestDismiss, enabled = !state.saving) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Button(
                                onClick = onSave,
                                enabled = !state.saving && !processingCustomImage,
                                modifier = Modifier.widthIn(max = 608.dp).fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                if (state.saving || processingCustomImage) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(when {
                                    state.saving -> R.string.saving
                                    state.importingFile -> R.string.local_document_importing
                                    processingCustomImage -> R.string.processing_image
                                    else -> R.string.save
                                }))
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TonalCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionHeader(stringResource(if (state.isLocal) R.string.local_document_title else R.string.website_details))
                                if (!state.isLocal) OutlinedTextField(
                                    value = state.url,
                                    onValueChange = onUrlChange,
                                    enabled = !state.saving,
                                    label = { Text(stringResource(R.string.url_label)) },
                                    placeholder = { Text(stringResource(R.string.url_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                    supportingText = if (state.urlError != null || urlWasFocused) {
                                        { Text(state.urlError ?: stringResource(R.string.url_supporting)) }
                                    } else null,
                                    isError = state.urlError != null,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier.fillMaxWidth().onFocusChanged { focus ->
                                        if (urlWasFocused && !focus.isFocused) onUrlFocusLost()
                                        urlWasFocused = focus.isFocused
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                )
                                OutlinedTextField(
                                    value = state.title,
                                    onValueChange = onTitleChange,
                                    enabled = !state.saving,
                                    label = { Text(stringResource(R.string.title_label)) },
                                    placeholder = { Text(stringResource(R.string.title_placeholder)) },
                                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                    supportingText = if (state.titleError != null) { { Text(state.titleError) } } else null,
                                    isError = state.titleError != null,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        }
                        if (state.isLocal) LocalDocumentSection(state, onImportLocal)
                        else BrowserSection(state.browserPackage, browsers, !state.saving) { showBrowserPicker = true }
                        IconPreview(state, roundPreview, fullCanvasPreview, showSafeZone,
                            onRoundChange = { roundPreview = it }, onFullCanvasChange = { fullCanvasPreview = it },
                            onSafeZoneChange = { showSafeZone = it })
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.icon_mode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            if (LocalDensity.current.fontScale >= 1.5f) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconSourceMode.entries.filter { !state.isLocal || it != IconSourceMode.AUTO }.forEach { mode ->
                                        FilterChip(selected = state.mode == mode, enabled = !state.saving,
                                            onClick = { focusManager.clearFocus(); onModeChange(mode) },
                                            label = { Text(modeLabel(mode)) })
                                    }
                                }
                            } else SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                val modes = IconSourceMode.entries.filter { !state.isLocal || it != IconSourceMode.AUTO }
                                modes.forEachIndexed { index, mode ->
                                    SegmentedButton(
                                        selected = state.mode == mode,
                                        onClick = { focusManager.clearFocus(); onModeChange(mode) },
                                        enabled = !state.saving,
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                                        label = {
                                            Text(
                                                stringResource(when (mode) {
                                                    IconSourceMode.AUTO -> R.string.mode_auto
                                                    IconSourceMode.CUSTOM -> R.string.mode_custom
                                                    IconSourceMode.GENERATED -> R.string.mode_generated
                                                }),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 2,
                                                textAlign = TextAlign.Center,
                                            )
                                        },
                                    )
                                }
                            }
                            ModeControls(state, onPickImage, onRefetchIcon, onCenterCropChange, onFreeCrop = { showCrop = true })
                        }
                        if (state.statusMessage != null && state.error == null) {
                            Text(state.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("icon_fetch_status"))
                        }
                        if (state.error != null) ErrorNotice(state.error)
                        TextButton(onClick = onEditTags, enabled = !state.saving,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("editor_tags")) {
                            Icon(Icons.AutoMirrored.Filled.Label, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val names = tags.filter { it.id in state.tagIds }.map { it.name } + state.newTagNames
                            Text(if (names.isEmpty()) stringResource(R.string.bookmark_tags) else names.joinToString(" · "),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(if (state.isNew) R.string.save_new_hint else R.string.save_sync_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (showBrowserPicker) BrowserPicker(state.browserPackage, browsers, onBrowserChange) { showBrowserPicker = false }
        if (askDiscard) {
            AlertDialog(
                onDismissRequest = { askDiscard = false },
                title = { Text(stringResource(R.string.discard_title)) },
                text = { Text(stringResource(R.string.discard_body)) },
                confirmButton = { TextButton(onClick = { askDiscard = false; onDismiss() }) { Text(stringResource(R.string.discard_changes)) } },
                dismissButton = { TextButton(onClick = { askDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
            )
        }
        if (showCrop && state.cropSource != null) {
            FreeCropDialog(source = state.cropSource, initialCrop = state.cropRect, onDismiss = { showCrop = false },
                onApply = { crop -> showCrop = false; onCropApply(crop) })
        }
    }
}

@Composable
private fun EditorWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
    } else {
        Dialog(onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false)) {
            BackHandler(onBack = onDismiss)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPreview(state: EditorUiState, round: Boolean, fullCanvas: Boolean, showSafeZone: Boolean,
                        onRoundChange: (Boolean) -> Unit, onFullCanvasChange: (Boolean) -> Unit,
                        onSafeZoneChange: (Boolean) -> Unit) {
    val accent by rememberIconAccent(state.preview)
    val colors = MaterialTheme.colorScheme
    val stageColor = animatedIconTint(colors.surfaceContainerLow, accent, colors.onSurface, colors.onSurfaceVariant)
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    val viewControls: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !fullCanvas, onClick = { onFullCanvasChange(false) }, modifier = Modifier.testTag("preview_launcher"), label = { Text(stringResource(R.string.preview_launcher)) })
            FilterChip(selected = fullCanvas, onClick = { onFullCanvasChange(true) }, modifier = Modifier.testTag("preview_full_canvas"), label = { Text(stringResource(R.string.preview_full_canvas)) })
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = stageColor, modifier = Modifier.fillMaxWidth().testTag("icon_preview_panel")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    IconArtworkPreview(state.preview, round, fullCanvas, showSafeZone, Modifier.size(88.dp))
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.size(96.dp), strokeWidth = 3.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.title.ifBlank { stringResource(R.string.icon_preview) }, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (!largeFont) viewControls()
                    if (!showSafeZone) Text(stringResource(R.string.icon_preview_hint), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant)
                }
            }
            if (largeFont) viewControls()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !round, onClick = { onRoundChange(false) }, label = { Text(stringResource(R.string.preview_rounded)) })
                FilterChip(selected = round, onClick = { onRoundChange(true) }, label = { Text(stringResource(R.string.preview_round)) })
                FilterChip(selected = showSafeZone, onClick = { onSafeZoneChange(!showSafeZone) },
                    modifier = Modifier.testTag("preview_safe_zone"), label = { Text(stringResource(R.string.show_safe_zone)) })
            }
            if (showSafeZone) Text(
                stringResource(if (fullCanvas) R.string.full_canvas_safe_hint else R.string.safe_zone_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeControls(
    state: EditorUiState,
    onPickImage: () -> Unit,
    onRefetchIcon: () -> Unit,
    onCenterCropChange: (Boolean) -> Unit,
    onFreeCrop: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(when (state.mode) {
                    IconSourceMode.AUTO -> R.string.auto_explanation
                    IconSourceMode.CUSTOM -> R.string.custom_explanation
                    IconSourceMode.GENERATED -> R.string.generated_explanation
                }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.mode) {
                IconSourceMode.AUTO -> {
                    if (state.sourceLabel != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(state.sourceLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    IconSourceDetails(state.sourceUrl, state.lastFetchedAt)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRefetchIcon, enabled = !state.loading && !state.saving && state.url.isNotBlank()) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.refetch_icon))
                        }
                        if (state.loading) {
                            Text(stringResource(R.string.fetching_icon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                IconSourceMode.CUSTOM -> {
                    FilledTonalButton(onClick = onPickImage, enabled = !state.saving && !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.hasCustomImage) R.string.replace_image else R.string.choose_image))
                    }
                    if (state.hasCustomImage) {
                        Text(stringResource(R.string.custom_retained), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (state.canCrop) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.custom_framing), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.cropRect == null,
                                    onClick = { onCenterCropChange(false) },
                                    enabled = !state.loading && !state.saving,
                                    modifier = Modifier.testTag("crop_fit"),
                                    label = { Text(stringResource(R.string.crop_fit), textAlign = TextAlign.Center) },
                                    leadingIcon = if (state.cropRect == null) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                )
                                FilterChip(
                                    selected = state.centerCrop,
                                    onClick = { onCenterCropChange(true) },
                                    enabled = !state.loading && !state.saving,
                                    modifier = Modifier.testTag("crop_center"),
                                    label = { Text(stringResource(R.string.crop_center), textAlign = TextAlign.Center) },
                                    leadingIcon = if (state.centerCrop) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                )
                            }
                            FilledTonalButton(onClick = onFreeCrop, enabled = !state.loading && !state.saving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("free_crop")) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.free_crop_entry))
                            }
                            Text(stringResource(R.string.crop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (state.hasCustomImage && !state.loading) {
                        Text(stringResource(R.string.reimport_to_crop), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconSourceMode.GENERATED -> Unit
            }
        }
    }
}

internal fun formatIconTime(timestamp: Long, compact: Boolean = false): String =
    DateFormat.getDateTimeInstance(if (compact) DateFormat.SHORT else DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun IconSourceDetails(sourceUrl: String?, lastFetchedAt: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.icon_source_url_label), style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(sourceUrl ?: stringResource(R.string.icon_source_url_empty), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("icon_source_url"))
        }
        Text(if (lastFetchedAt == null) stringResource(R.string.icon_fetch_time_empty)
            else stringResource(R.string.last_successful_fetch, formatIconTime(lastFetchedAt)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("icon_sync_time"))
    }
}
