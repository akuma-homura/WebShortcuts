package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconPolicy
import io.github.homura251.webshortcuts.icons.CropRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

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
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<BookmarkItem?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val filtered = remember(state.bookmarks, state.query) {
        val query = state.query.trim()
        state.bookmarks.filter { query.isEmpty() || it.title.contains(query, true) || it.url.contains(query, true) }
    }
    val autoCount = state.bookmarks.count { it.selectedIconSource == IconSourceMode.AUTO }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("home_screen").nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (state.refreshing) R.string.refreshing_all else R.string.refresh_all)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                enabled = autoCount > 0 && !state.refreshing,
                                onClick = { menuExpanded = false; onRefreshAll() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_menu)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { menuExpanded = false; showAbout = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.loading && state.error == null && state.bookmarks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_site)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing,
            onRefresh = { if (!state.loading && state.error == null && autoCount > 0) onRefreshAll() },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.bookmarks.isNotEmpty()) item(key = "intro") {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            stringResource(R.string.home_tagline),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(R.string.home_subtitle),
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.error != null) {
                    item(key = "load_error") { ErrorNotice(state.error, title = stringResource(R.string.read_error_title)) }
                }
                if (state.loading) {
                    item(key = "loading") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.loading_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (state.bookmarks.isEmpty() && state.error == null) {
                    item(key = "empty") { EmptyBookmarks(onAdd) }
                } else if (state.bookmarks.isNotEmpty()) {
                    item(key = "search") {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (state.query.isNotEmpty()) {
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        )
                    }
                    item(key = "counts") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(if (state.query.isBlank()) R.string.bookmark_count else R.string.filtered_count, filtered.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.query.isBlank() && autoCount > 0) {
                                Text(
                                    stringResource(R.string.auto_count, autoCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (state.refreshing) {
                        item(key = "refresh_progress") {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(progress = { if (state.refreshTotal > 0) state.refreshCompleted.toFloat() / state.refreshTotal else 0f }, modifier = Modifier.fillMaxWidth())
                                Text(stringResource(R.string.refresh_progress_count, state.refreshCompleted, state.refreshTotal),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    state.refreshSummary?.let { summary ->
                        item(key = "refresh_summary") {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    IconButton(onClick = onClearRefreshSummary) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss_refresh_summary)) }
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "no_results") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.no_results_title), modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.no_results_body), modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { onQueryChange("") }, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.clear_search)) }
                            }
                        }
                    }
                    items(filtered, key = { it.id }) { bookmark ->
                        BookmarkCard(
                            bookmark = bookmark,
                            iconResolver = iconResolver,
                            refreshing = bookmark.id in state.refreshingIds,
                            refreshMessage = state.refreshMessages[bookmark.id],
                            onOpen = { onOpen(bookmark) },
                            onPin = { onPin(bookmark) },
                            onEdit = { onEdit(bookmark) },
                            onRefresh = { onRefresh(bookmark) },
                            onUpdateShortcut = { onUpdateShortcut(bookmark) },
                            onDelete = { deleteTarget = bookmark },
                        )
                    }
                }
            }
        }
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
        )
    }
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

@Composable
private fun EmptyBookmarks(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                Icons.Default.BookmarkBorder,
                contentDescription = null,
                modifier = Modifier.padding(20.dp).size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 390.dp).padding(top = 12.dp),
        )
        Button(onClick = onAdd, modifier = Modifier.padding(top = 24.dp).heightIn(min = 52.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_first_site))
        }
        Row(
            modifier = Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            modifier = Modifier.padding(top = 28.dp).widthIn(max = 440.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                EmptyStep(Icons.Default.Link, stringResource(R.string.empty_step_one))
                EmptyStep(Icons.Default.Image, stringResource(R.string.empty_step_two))
                EmptyStep(Icons.Default.PushPin, stringResource(R.string.empty_step_three))
            }
        }
    }
}

@Composable
private fun EmptyStep(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BookmarkCard(
    bookmark: BookmarkItem,
    iconResolver: IconResolver,
    refreshing: Boolean,
    refreshMessage: String?,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateShortcut: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BookmarkIcon(bookmark, iconResolver)
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bookmark.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(bookmark.url.removePrefix("https://").removePrefix("http://").trimEnd('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(modeLabel(bookmark.selectedIconSource), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (refreshing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.fetching_icon), style = MaterialTheme.typography.labelSmall)
                    }
                } else if (refreshMessage != null) {
                    Text(refreshMessage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (bookmark.selectedIconSource == IconSourceMode.AUTO && bookmark.lastFetchedAt != null) {
                    Text(stringResource(R.string.last_successful_fetch, formatIconTime(bookmark.lastFetchedAt)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.site_options, bookmark.title))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    BookmarkMenuItem(R.string.open_website, Icons.Default.OpenInNew) { expanded = false; onOpen() }
                    BookmarkMenuItem(R.string.pin_shortcut, Icons.Default.PushPin) { expanded = false; onPin() }
                    BookmarkMenuItem(R.string.edit_bookmark, Icons.Default.Edit) { expanded = false; onEdit() }
                    if (bookmark.selectedIconSource == IconSourceMode.AUTO) {
                        BookmarkMenuItem(R.string.refresh_icon, Icons.Default.Refresh, enabled = !refreshing) { expanded = false; onRefresh() }
                    }
                    BookmarkMenuItem(R.string.update_shortcut, Icons.Default.Sync) { expanded = false; onUpdateShortcut() }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_bookmark), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { expanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkMenuItem(text: Int, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(text)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun BookmarkIcon(bookmark: BookmarkItem, iconResolver: IconResolver) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = bookmark, key2 = iconResolver) {
        value = try {
            withContext(Dispatchers.IO) { iconResolver.resolve(bookmark) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().adaptiveIconViewport().testTag("bookmark_icon_${bookmark.id}"))
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(bookmark.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

// Adaptive foreground layers are 108dp, while the launcher's visible viewport is 72dp.
// The surrounding Surface supplies the selected mask; showing the whole layer adds false padding.
private fun Modifier.adaptiveIconViewport(): Modifier = graphicsLayer {
    scaleX = 1f / IconPolicy.ADAPTIVE_VIEWPORT_RATIO
    scaleY = 1f / IconPolicy.ADAPTIVE_VIEWPORT_RATIO
}

@Composable
private fun modeLabel(mode: IconSourceMode): String = stringResource(
    when (mode) {
        IconSourceMode.AUTO -> R.string.mode_label_auto
        IconSourceMode.CUSTOM -> R.string.mode_label_custom
        IconSourceMode.GENERATED -> R.string.mode_label_generated
    },
)

@Composable
private fun ErrorNotice(message: String, title: String? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkEditor(
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
) {
    var askDiscard by rememberSaveable(state.id) { mutableStateOf(false) }
    var roundPreview by rememberSaveable(state.id) { mutableStateOf(false) }
    var fullCanvasPreview by rememberSaveable(state.id) { mutableStateOf(false) }
    var showSafeZone by rememberSaveable(state.id) { mutableStateOf(false) }
    var showCrop by rememberSaveable(state.id) { mutableStateOf(false) }
    var urlWasFocused by remember(state.id) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val processingCustomImage = state.loading && state.mode == IconSourceMode.CUSTOM
    val requestDismiss: () -> Unit = {
        if (!state.saving) {
            if (state.dirty) askDiscard = true else onDismiss()
        }
    }
    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnBackPress = false),
    ) {
        BackHandler(onBack = requestDismiss)
        Surface(Modifier.fillMaxSize().testTag("editor_screen"), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title), style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            IconButton(onClick = requestDismiss, enabled = !state.saving) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        },
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Button(
                                onClick = onSave,
                                enabled = !state.saving && !processingCustomImage,
                                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                if (state.saving || processingCustomImage) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(when {
                                    state.saving -> R.string.saving
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
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        IconPreview(state, roundPreview, fullCanvasPreview, showSafeZone,
                            onRoundChange = { roundPreview = it }, onFullCanvasChange = { fullCanvasPreview = it },
                            onSafeZoneChange = { showSafeZone = it })
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.icon_mode), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                val modes = IconSourceMode.entries
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
                        OutlinedTextField(
                            value = state.url,
                            onValueChange = onUrlChange,
                            enabled = !state.saving,
                            label = { Text(stringResource(R.string.url_label)) },
                            placeholder = { Text(stringResource(R.string.url_placeholder)) },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            supportingText = { Text(state.urlError ?: stringResource(R.string.url_supporting)) },
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
                            supportingText = { Text(state.titleError ?: stringResource(R.string.title_supporting)) },
                            isError = state.titleError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
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
private fun IconPreview(state: EditorUiState, round: Boolean, fullCanvas: Boolean, showSafeZone: Boolean,
                        onRoundChange: (Boolean) -> Unit, onFullCanvasChange: (Boolean) -> Unit,
                        onSafeZoneChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                IconArtworkPreview(state.preview, round, fullCanvas, showSafeZone, Modifier.size(156.dp))
                if (state.loading) CircularProgressIndicator(modifier = Modifier.size(174.dp), strokeWidth = 3.dp)
            }
            Text(
                state.title.ifBlank { stringResource(R.string.icon_preview) },
                modifier = Modifier.padding(top = 12.dp),
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilterChip(selected = !fullCanvas, onClick = { onFullCanvasChange(false) }, modifier = Modifier.testTag("preview_launcher"), label = { Text(stringResource(R.string.preview_launcher)) })
                FilterChip(selected = fullCanvas, onClick = { onFullCanvasChange(true) }, modifier = Modifier.testTag("preview_full_canvas"), label = { Text(stringResource(R.string.preview_full_canvas)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !round, onClick = { onRoundChange(false) }, label = { Text(stringResource(R.string.preview_rounded)) })
                FilterChip(selected = round, onClick = { onRoundChange(true) }, label = { Text(stringResource(R.string.preview_round)) })
            }
            FilterChip(selected = showSafeZone, onClick = { onSafeZoneChange(!showSafeZone) },
                modifier = Modifier.testTag("preview_safe_zone"), label = { Text(stringResource(R.string.show_safe_zone)) })
            Text(
                stringResource(if (fullCanvas && showSafeZone) R.string.full_canvas_safe_hint else if (showSafeZone) R.string.safe_zone_hint else R.string.icon_preview_hint),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ModeControls(
    state: EditorUiState,
    onPickImage: () -> Unit,
    onRefetchIcon: () -> Unit,
    onCenterCropChange: (Boolean) -> Unit,
    onFreeCrop: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(when (state.mode) {
                IconSourceMode.AUTO -> R.string.auto_explanation
                IconSourceMode.CUSTOM -> R.string.custom_explanation
                IconSourceMode.GENERATED -> R.string.generated_explanation
            }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.mode) {
            IconSourceMode.AUTO -> {
                if (state.sourceLabel != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(state.sourceLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.cropRect == null,
                                onClick = { onCenterCropChange(false) },
                                enabled = !state.loading && !state.saving,
                                modifier = Modifier.weight(1f).testTag("crop_fit"),
                                label = { Text(stringResource(R.string.crop_fit), textAlign = TextAlign.Center) },
                                leadingIcon = if (state.cropRect == null) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                            )
                            FilterChip(
                                selected = state.centerCrop,
                                onClick = { onCenterCropChange(true) },
                                enabled = !state.loading && !state.saving,
                                modifier = Modifier.weight(1f).testTag("crop_center"),
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

private fun formatIconTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

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
