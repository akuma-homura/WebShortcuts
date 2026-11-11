package io.github.homura251.webshortcuts.ui

import androidx.compose.material.icons.filled.FolderOpen
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import io.github.homura251.webshortcuts.ui.components.ExpressiveAlertDialog as AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton as TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.LibraryOrdering
import io.github.homura251.webshortcuts.data.LibrarySnapshot
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.CropRect
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebShortcutsApp(
    state: HomeUiState,
    iconResolver: IconResolver,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onEdit: (BookmarkItem) -> Unit,
    onOpen: (BookmarkItem) -> Unit,
    onPin: (BookmarkItem) -> Unit,
    onUpdateShortcut: (BookmarkItem) -> Unit,
    onRefresh: (BookmarkItem) -> Unit,
    onRefreshAll: () -> Unit,
    onDelete: (BookmarkItem) -> Unit,
    onDismissEditor: () -> Unit,
    onTitleChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onUrlFocusLost: () -> Unit,
    onModeChange: (IconSourceMode) -> Unit,
    onPickImage: () -> Unit,
    onRefetchIcon: () -> Unit,
    onSave: () -> Unit,
    onCenterCropChange: (Boolean) -> Unit = {},
    onCropApply: (CropRect?) -> Unit = {},
    onClearRefreshSummary: () -> Unit = {},
    appearance: AppearancePreferences = AppearancePreferences(),
    onAppearanceChange: (AppearancePreferences) -> Unit = {},
    onOpenTransfer: () -> Unit = {},
    tagActions: TagActions = TagActions(),
    sortActions: SortActions = SortActions(),
    onBrowserChange: (String?) -> Unit = {},
    onImportLocal: () -> Unit = {},
    onSourceFilterChange: (SourceFilter) -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showTagFilter by rememberSaveable { mutableStateOf(false) }
    var showTagManager by rememberSaveable { mutableStateOf(false) }
    var showSorting by rememberSaveable { mutableStateOf<String?>(null) }
    var sortQuery by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val orderedTags = remember(state.bookmarks, state.tags, state.sorting, locale) {
        LibraryOrdering.tags(LibrarySnapshot(state.bookmarks, state.tags), state.sorting, locale)
    }
    BackHandler(state.selectionMode && state.tagEditor == null) { tagActions.cancelSelection() }
    var deleteTarget by remember { mutableStateOf<BookmarkItem?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val autoCount = state.bookmarks.count { !it.isLocal && it.selectedIconSource == IconSourceMode.AUTO }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("home_screen").nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.selectionMode) stringResource(R.string.selected_bookmark_count, state.selectedIds.size) else stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (state.selectionMode) IconButton(onClick = tagActions.cancelSelection, enabled = !state.tagSaving) {
                        Icon(Icons.Default.Close, stringResource(R.string.cancel_selection))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    if (state.selectionMode) {
                        TextButton(onClick = tagActions.selectAll, enabled = !state.tagSaving,
                            modifier = Modifier.testTag("select_all_bookmarks")) { Text(stringResource(R.string.select_all)) }
                    } else {
                        IconButton(onClick = onImportLocal, enabled = !state.transferBusy && !state.importingLocal, modifier = Modifier.testTag("import_local_page")) {
                            Icon(Icons.Default.FolderOpen, stringResource(R.string.import_local_page))
                        }
                        IconButton(onClick = { showAppearance = true }, enabled = !state.transferBusy, modifier = Modifier.testTag("open_appearance")) {
                            Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.appearance_title))
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (state.refreshing) R.string.refreshing_all else R.string.refresh_all)) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    enabled = autoCount > 0 && !state.refreshing && !state.transferBusy && state.reorder == null,
                                    onClick = { menuExpanded = false; onRefreshAll() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.transfer_title)) },
                                    leadingIcon = { Icon(Icons.Default.ImportExport, contentDescription = null) },
                                    enabled = !state.loading && state.error == null && !state.transferBusy && !state.tagSaving && state.reorder == null,
                                    onClick = { menuExpanded = false; onOpenTransfer() },
                                    modifier = Modifier.testTag("open_transfer"),
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.manage_tags)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) }, enabled = !state.transferBusy && !state.tagSaving,
                                    onClick = { menuExpanded = false; showTagManager = true }, modifier = Modifier.testTag("manage_tags"))
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.about_menu)) },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = { menuExpanded = false; showAbout = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.loading && state.error == null && state.bookmarks.isNotEmpty() && !state.selectionMode && state.reorder == null) {
                ExtendedFloatingActionButton(
                    onClick = { if (!state.transferBusy) onAdd() },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_site)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing,
            onRefresh = { if (!state.loading && state.error == null && autoCount > 0 && !state.transferBusy && !state.selectionMode && state.reorder == null) onRefreshAll() },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            BookmarkCollection(
                state = state, iconResolver = iconResolver, layoutMode = appearance.layoutMode,
                onQueryChange = onQueryChange, onAdd = onAdd,
                onImportLocal = onImportLocal, onSourceFilterChange = onSourceFilterChange,
                onLayoutChange = { onAppearanceChange(appearance.copy(layoutMode = it)) },
                onOpen = onOpen, onPin = onPin, onEdit = onEdit, onRefresh = onRefresh,
                onUpdateShortcut = onUpdateShortcut, onDelete = { deleteTarget = it },
                onClearRefreshSummary = onClearRefreshSummary,
                tagActions = tagActions, onShowTagFilter = { showTagFilter = true },
                onShowSorting = { sortQuery = ""; showSorting = OrderTarget.BOOKMARKS.name },
            )
        }
    }

    if (showAppearance) {
        AppearanceSheet(appearance, onAppearanceChange, onDismiss = { showAppearance = false })
    }
    state.editor?.let { editor ->
        BookmarkEditor(
            state = editor,
            onDismiss = onDismissEditor,
            onTitleChange = onTitleChange,
            onUrlChange = onUrlChange,
            onUrlFocusLost = onUrlFocusLost,
            onModeChange = onModeChange,
            onPickImage = onPickImage,
            onRefetchIcon = onRefetchIcon,
            onSave = onSave,
            onCenterCropChange = onCenterCropChange,
            onCropApply = onCropApply,
            tags = orderedTags, onEditTags = tagActions.editEditor,
            browsers = state.browsers, onBrowserChange = onBrowserChange, onImportLocal = onImportLocal,
        )
    }
    if (showTagFilter && !state.selectionMode) TagPanel(stringResource(R.string.filter_tags), { showTagFilter = false }) {
        TagFilterContent(state, tagActions.filter)
    }
    if (showTagManager) TagPanel(stringResource(R.string.manage_tags), {
        if (!state.tagSaving) { tagActions.catalogNameChange(""); showTagManager = false }
    }, !state.tagSaving) {
        TagManagerContent(state, tagActions.create, tagActions.rename, tagActions.delete,
            onShowSorting = { sortQuery = it; showSorting = OrderTarget.TAGS.name }, onNewNameChange = tagActions.catalogNameChange)
    }
    state.tagEditor?.let { sheet ->
        val title = stringResource(when (sheet.mode) {
            TagEditMode.SET -> R.string.bookmark_tags
            TagEditMode.ADD -> R.string.add_tags
            TagEditMode.REMOVE -> R.string.remove_tags
        })
        TagPanel(title, tagActions.dismiss, !sheet.saving) {
            TagEditorContent(orderedTags, sheet, tagActions.toggle, tagActions.toggleNew, tagActions.createDraft, tagActions.apply,
                onPendingChange = tagActions.pendingNameChange, onRemoveSelected = tagActions.removeSelected)
        }
    }
    showSorting?.let { target ->
        SortingSheet(state, OrderTarget.valueOf(target), sortQuery, { showSorting = null }, sortActions)
    }
    if (state.reorder != null) ReorderDialog(state, sortActions)
    deleteTarget?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_title, bookmark.title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; onDelete(bookmark) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.about_title)) },
            text = { Text(stringResource(R.string.about_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.understood)) } },
        )
    }
}
