package io.github.homura251.webshortcuts.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkTag
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.CropRect
import io.github.homura251.webshortcuts.shortcuts.BrowserOption
import io.github.homura251.webshortcuts.ui.components.ConnectedActionItem
import io.github.homura251.webshortcuts.ui.components.ConnectedGroup
import io.github.homura251.webshortcuts.ui.components.ExpressiveAlertDialog
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveChoiceOption
import io.github.homura251.webshortcuts.ui.components.ExpressiveConnectedChoices
import io.github.homura251.webshortcuts.ui.components.ExpressiveFilterChip
import io.github.homura251.webshortcuts.ui.components.ExpressiveStatusPill
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextField
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalIconButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveVisibility
import io.github.homura251.webshortcuts.ui.components.SectionHeader
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.components.TonalEmphasis
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import io.github.homura251.webshortcuts.ui.theme.AppTheme
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
    val editorScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val processingCustomImage = (state.loading && state.mode == IconSourceMode.CUSTOM) || state.importingFile
    val requestDismiss: () -> Unit = {
        if (!state.saving && !state.importingFile) {
            if (state.dirty) askDiscard = true else onDismiss()
        }
    }

    EditorWindow(onDismiss = requestDismiss) {
        // A Dialog owns a different focus root from the Activity beneath it.
        val focusManager = LocalFocusManager.current
        val view = LocalView.current
        val surfaces = AppTheme.surfaces
        val lightSystemBars = surfaces.canvas.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { dialogWindow ->
                WindowCompat.getInsetsController(dialogWindow, view).apply {
                    isAppearanceLightStatusBars = lightSystemBars
                    isAppearanceLightNavigationBars = lightSystemBars
                }
            }
        }

        Surface(Modifier.fillMaxSize().testTag("editor_screen"), color = surfaces.canvas) {
            Scaffold(
                modifier = Modifier.imePadding().nestedScroll(editorScrollBehavior.nestedScrollConnection),
                containerColor = surfaces.canvas,
                topBar = {
                    CenterAlignedTopAppBar(
                        scrollBehavior = editorScrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = surfaces.canvas,
                            scrolledContainerColor = surfaces.scrolledBar,
                        ),
                        title = {
                            Text(
                                stringResource(
                                    when {
                                        state.isLocal && state.isNew -> R.string.editor_new_local_title
                                        state.isLocal -> R.string.editor_edit_local_title
                                        state.isNew -> R.string.editor_new_title
                                        else -> R.string.editor_edit_title
                                    }
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            ExpressiveTonalIconButton(onClick = requestDismiss, enabled = !state.saving) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(
                        shadowElevation = 6.dp,
                        color = surfaces.card,
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            ExpressiveButton(
                                onClick = { focusManager.clearFocus(); onSave() },
                                enabled = !state.saving && !processingCustomImage,
                                modifier = Modifier.widthIn(max = 608.dp).fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                if (state.saving || processingCustomImage) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        when {
                                            state.saving -> R.string.saving
                                            state.importingFile -> R.string.local_document_importing
                                            processingCustomImage -> R.string.processing_image
                                            else -> R.string.save
                                        }
                                    )
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(
                    Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = AppSpacing.editorMaxWidth)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = AppSpacing.content, vertical = AppSpacing.compact),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 1. Website / Document Details Card
                        TonalCard(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(stringResource(if (state.isLocal) R.string.local_document_details else R.string.website_details))
                            if (!state.isLocal) {
                                ExpressiveTextField(
                                    value = state.url,
                                    onValueChange = onUrlChange,
                                    enabled = !state.saving,
                                    label = stringResource(R.string.url_label),
                                    placeholder = stringResource(R.string.url_placeholder),
                                    leadingIcon = Icons.Default.Link,
                                    supportingText = if (state.urlError != null || urlWasFocused) {
                                        state.urlError ?: stringResource(R.string.url_supporting)
                                    } else null,
                                    isError = state.urlError != null,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Uri,
                                        imeAction = ImeAction.Next,
                                        autoCorrectEnabled = false,
                                    ),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                                    modifier = Modifier.onFocusChanged { focus ->
                                        if (urlWasFocused && !focus.isFocused) onUrlFocusLost()
                                        urlWasFocused = focus.isFocused
                                    },
                                )
                            }
                            ExpressiveTextField(
                                value = state.title,
                                onValueChange = onTitleChange,
                                enabled = !state.saving,
                                label = stringResource(if (state.isLocal) R.string.local_document_name_label else R.string.title_label),
                                placeholder = stringResource(if (state.isLocal) R.string.local_document_name_hint else R.string.title_placeholder),
                                leadingIcon = Icons.Default.TextFields,
                                supportingText = state.titleError,
                                isError = state.titleError != null,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            )
                        }

                        // 2. Unified Icon Studio Card (Preview Stage + Mask Controls + Source Mode + Framing)
                        IconStudioCard(
                            state = state,
                            round = roundPreview,
                            fullCanvas = fullCanvasPreview,
                            showSafeZone = showSafeZone,
                            onRoundChange = { roundPreview = it },
                            onFullCanvasChange = { fullCanvasPreview = it },
                            onSafeZoneChange = { showSafeZone = it },
                            onModeChange = { focusManager.clearFocus(); onModeChange(it) },
                            onPickImage = onPickImage,
                            onRefetchIcon = onRefetchIcon,
                            onCenterCropChange = onCenterCropChange,
                            onFreeCrop = { showCrop = true },
                        )

                        if (state.statusMessage != null && state.error == null) {
                            ExpressiveStatusPill(
                                text = state.statusMessage,
                                emphasis = TonalEmphasis.PRIMARY,
                                modifier = Modifier.testTag("icon_fetch_status"),
                            )
                        }
                        if (state.error != null) ErrorNotice(state.error)

                        // 3. Launch Target & Tag Organization Connected Group
                        val tagNames = tags.filter { it.id in state.tagIds }.map { it.name } + state.newTagNames
                        val tagSummary = if (tagNames.isEmpty()) stringResource(R.string.bookmark_tags) else tagNames.joinToString(" · ")
                        if (state.isLocal) {
                            LocalDocumentSection(state, onImportLocal)
                            ConnectedGroup {
                                ConnectedActionItem(
                                    index = 0,
                                    count = 1,
                                    title = tagSummary,
                                    supporting = if (tagNames.isEmpty()) null else stringResource(R.string.bookmark_tags),
                                    icon = Icons.AutoMirrored.Filled.Label,
                                    enabled = !state.saving,
                                    onClick = onEditTags,
                                    modifier = Modifier.testTag("editor_tags"),
                                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                                )
                            }
                        } else {
                            BrowserAndTagsSection(
                                packageName = state.browserPackage,
                                browsers = browsers,
                                tagSummary = tagSummary,
                                hasTags = tagNames.isNotEmpty(),
                                enabled = !state.saving,
                                onChooseBrowser = { showBrowserPicker = true },
                                onEditTags = onEditTags,
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            ExpressiveAlertDialog(
                onDismissRequest = { askDiscard = false },
                title = { Text(stringResource(R.string.discard_title)) },
                text = { Text(stringResource(R.string.discard_body)) },
                confirmButton = {
                    ExpressiveTextButton(onClick = { askDiscard = false; onDismiss() }, destructive = true) {
                        Text(stringResource(R.string.discard_changes))
                    }
                },
                dismissButton = {
                    ExpressiveTextButton(onClick = { askDiscard = false }) {
                        Text(stringResource(R.string.keep_editing))
                    }
                },
            )
        }
        if (showCrop && state.cropSource != null) {
            FreeCropDialog(
                source = state.cropSource,
                initialCrop = state.cropRect,
                onDismiss = { showCrop = false },
                onApply = { crop -> showCrop = false; onCropApply(crop) },
            )
        }
    }
}

@Composable
private fun EditorWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (LocalInspectionMode.current) {
        content()
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
            ),
        ) {
            BackHandler(onBack = onDismiss)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconStudioCard(
    state: EditorUiState,
    round: Boolean,
    fullCanvas: Boolean,
    showSafeZone: Boolean,
    onRoundChange: (Boolean) -> Unit,
    onFullCanvasChange: (Boolean) -> Unit,
    onSafeZoneChange: (Boolean) -> Unit,
    onModeChange: (IconSourceMode) -> Unit,
    onPickImage: () -> Unit,
    onRefetchIcon: () -> Unit,
    onCenterCropChange: (Boolean) -> Unit,
    onFreeCrop: () -> Unit,
) {
    val accent by rememberIconAccent(state.preview)
    val colors = MaterialTheme.colorScheme

    TonalCard(
        modifier = Modifier.fillMaxWidth().testTag("icon_preview_panel"),
        accentTint = accent,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = AppTheme.surfaces.inset,
                modifier = Modifier.size(100.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    IconArtworkPreview(state.preview, round, fullCanvas, showSafeZone, Modifier.size(84.dp))
                    if (state.loading) CircularProgressIndicator(modifier = Modifier.size(94.dp), strokeWidth = 3.dp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    state.title.ifBlank { stringResource(R.string.icon_preview) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.icon_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                ExpressiveConnectedChoices(
                    options = listOf(
                        ExpressiveChoiceOption(false, stringResource(R.string.preview_launcher), testTag = "preview_launcher"),
                        ExpressiveChoiceOption(true, stringResource(R.string.preview_full_canvas), testTag = "preview_full_canvas"),
                    ),
                    selected = fullCanvas,
                    onSelect = onFullCanvasChange,
                    onCardSurface = true,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveConnectedChoices(
                options = listOf(
                    ExpressiveChoiceOption(false, stringResource(R.string.preview_rounded)),
                    ExpressiveChoiceOption(true, stringResource(R.string.preview_round)),
                ),
                selected = round,
                onSelect = onRoundChange,
                onCardSurface = true,
            )
            ExpressiveFilterChip(
                selected = showSafeZone,
                onClick = { onSafeZoneChange(!showSafeZone) },
                modifier = Modifier.testTag("preview_safe_zone"),
                leadingIcon = if (showSafeZone) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
                label = { Text(stringResource(R.string.show_safe_zone)) },
            )
        }

        ExpressiveVisibility(showSafeZone) {
            Text(
                stringResource(if (fullCanvas) R.string.full_canvas_safe_hint else R.string.safe_zone_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

        Text(
            stringResource(R.string.icon_mode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        val modes = IconSourceMode.entries.filter { !state.isLocal || it != IconSourceMode.AUTO }
        ExpressiveConnectedChoices(
            options = modes.map { mode ->
                ExpressiveChoiceOption(
                    value = mode,
                    label = stringResource(
                        when (mode) {
                            IconSourceMode.AUTO -> R.string.mode_auto
                            IconSourceMode.CUSTOM -> R.string.mode_custom
                            IconSourceMode.GENERATED -> R.string.mode_generated
                        }
                    ),
                )
            },
            selected = state.mode,
            onSelect = onModeChange,
            enabled = !state.saving,
            fillWidth = true,
            onCardSurface = true,
            modifier = Modifier.fillMaxWidth(),
        )

        ModeControls(
            state = state,
            onPickImage = onPickImage,
            onRefetchIcon = onRefetchIcon,
            onCenterCropChange = onCenterCropChange,
            onFreeCrop = onFreeCrop,
        )
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
    TonalCard(
        modifier = Modifier.fillMaxWidth(),
        inset = true,
        contentPadding = PaddingValues(14.dp),
        verticalSpacing = 8.dp,
    ) {
        Text(
            stringResource(
                when (state.mode) {
                    IconSourceMode.AUTO -> R.string.auto_explanation
                    IconSourceMode.CUSTOM -> R.string.custom_explanation
                    IconSourceMode.GENERATED -> R.string.generated_explanation
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.mode) {
            IconSourceMode.AUTO -> {
                if (state.sourceLabel != null) {
                    ExpressiveStatusPill(text = state.sourceLabel, emphasis = TonalEmphasis.SECONDARY)
                }
                IconSourceDetails(state.sourceUrl, state.lastFetchedAt)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExpressiveTonalButton(
                        onClick = onRefetchIcon,
                        enabled = !state.loading && !state.saving && state.url.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.refetch_icon))
                    }
                    if (state.loading) {
                        Text(
                            stringResource(R.string.fetching_icon),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconSourceMode.CUSTOM -> {
                ExpressiveTonalButton(
                    onClick = onPickImage,
                    enabled = !state.saving && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.hasCustomImage) R.string.replace_image else R.string.choose_image))
                }
                if (state.hasCustomImage) {
                    Text(
                        stringResource(R.string.custom_retained),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (state.canCrop) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.custom_framing), style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ExpressiveFilterChip(
                                selected = state.cropRect == null,
                                onClick = { onCenterCropChange(false) },
                                enabled = !state.loading && !state.saving,
                                modifier = Modifier.testTag("crop_fit"),
                                leadingIcon = if (state.cropRect == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                label = { Text(stringResource(R.string.crop_fit)) },
                            )
                            ExpressiveFilterChip(
                                selected = state.centerCrop,
                                onClick = { onCenterCropChange(true) },
                                enabled = !state.loading && !state.saving,
                                modifier = Modifier.testTag("crop_center"),
                                leadingIcon = if (state.centerCrop) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                label = { Text(stringResource(R.string.crop_center)) },
                            )
                        }
                        ExpressiveTonalButton(
                            onClick = onFreeCrop,
                            enabled = !state.loading && !state.saving,
                            modifier = Modifier.fillMaxWidth().testTag("free_crop"),
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.free_crop_entry))
                        }
                        Text(
                            stringResource(R.string.crop_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (state.hasCustomImage && !state.loading) {
                    Text(
                        stringResource(R.string.reimport_to_crop),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconSourceMode.GENERATED -> Unit
        }
    }
}

internal fun formatIconTime(timestamp: Long, compact: Boolean = false): String =
    DateFormat.getDateTimeInstance(if (compact) DateFormat.SHORT else DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

@Composable
private fun IconSourceDetails(sourceUrl: String?, lastFetchedAt: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.icon_source_url_label), style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(
                sourceUrl ?: stringResource(R.string.icon_source_url_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("icon_source_url"),
            )
        }
        Text(
            if (lastFetchedAt == null) stringResource(R.string.icon_fetch_time_empty)
            else stringResource(R.string.last_successful_fetch, formatIconTime(lastFetchedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("icon_sync_time"),
        )
    }
}
