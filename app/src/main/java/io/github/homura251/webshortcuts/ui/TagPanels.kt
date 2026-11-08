package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.*

data class TagActions(
    val filter: (TagFilter) -> Unit = {},
    val toggleFilter: (String) -> Unit = {},
    val select: (io.github.homura251.webshortcuts.data.BookmarkItem) -> Unit = {},
    val selectAll: () -> Unit = {},
    val cancelSelection: () -> Unit = {},
    val edit: (io.github.homura251.webshortcuts.data.BookmarkItem) -> Unit = {},
    val editEditor: () -> Unit = {},
    val editBatch: (Boolean) -> Unit = {},
    val toggle: (String) -> Unit = {},
    val removeSelected: (String) -> Unit = {},
    val toggleNew: (String) -> Unit = {},
    val createDraft: (String) -> Unit = {},
    val pendingNameChange: (String) -> Unit = {},
    val catalogNameChange: (String) -> Unit = {},
    val apply: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val create: (String) -> Unit = {},
    val rename: (String, String) -> Unit = { _, _ -> },
    val delete: (String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagPanel(title: String, onDismiss: () -> Unit, canDismiss: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val dismissAllowed by rememberUpdatedState(canDismiss)
    val availableHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    ModalBottomSheet(onDismissRequest = { if (dismissAllowed) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { dismissAllowed || it != androidx.compose.material3.SheetValue.Hidden })) {
        Column(Modifier.fillMaxWidth().heightIn(max = availableHeight * .85f)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss, enabled = canDismiss) {
                    Icon(Icons.Default.Close, stringResource(R.string.close))
                }
            }
            content()
        }
    }
}

@Composable
internal fun ColumnScope.TagFilterContent(state: HomeUiState, onChange: (TagFilter) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = LocalConfiguration.current.locales[0]
    val ordered = remember(state.bookmarks, state.tags, state.sorting, locale) {
        LibraryOrdering.tags(LibrarySnapshot(state.bookmarks, state.tags), state.sorting, locale)
    }
    val counts = remember(state.bookmarks) { LibraryOrdering.tagCounts(state.bookmarks) }
    TagSearch(query, { query = it }, "filter_tag_search")
    Text(stringResource(R.string.tag_filter_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyVerticalGrid(tagColumns(), Modifier.fillMaxWidth().weight(1f, fill = false).testTag("filter_tag_list"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item { CompactTagChoice(stringResource(R.string.all_bookmarks), !state.tagFilter.active, state.bookmarks.size,
            "filter_all") { onChange(TagFilter()) } }
        item { CompactTagChoice(stringResource(R.string.untagged), state.tagFilter.untagged,
            state.bookmarks.count { it.tagIds.isEmpty() }, "filter_untagged") { onChange(TagFilter(untagged = true)) } }
        gridItems(ordered.filter { it.name.contains(query.trim(), true) }, key = { it.id }) { tag ->
            CompactTagChoice(tag.name, tag.id in state.tagFilter.ids, counts[tag.id] ?: 0,
                "filter_tag_${tag.id}") { onChange(state.tagFilter.toggle(tag.id)) }
        }
        if (state.tags.none { it.name.contains(query.trim(), true) }) item {
            Text(stringResource(R.string.no_matching_tags), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun tagColumns(): GridCells = if (LocalDensity.current.fontScale >= 1.5f) GridCells.Fixed(1) else GridCells.Adaptive(120.dp)

@Composable
private fun TagSearch(query: String, onChange: (String) -> Unit, key: String, enabled: Boolean = true, modifier: Modifier = Modifier) {
    TagSearchInput(query, onChange, modifier.fillMaxWidth().testTag(key), busy = !enabled)
}

@Composable
internal fun ColumnScope.TagEditorContent(tags: List<BookmarkTag>, state: TagEditorUiState, onToggle: (String) -> Unit,
    onToggleNew: (String) -> Unit, onCreate: (String) -> Unit, onApply: () -> Unit, onPendingChange: (String) -> Unit = {},
    onRemoveSelected: (String) -> Unit = {}) {
    val query = state.pendingName
    var composing by remember(state.targetIds, state.mode, state.forEditor) { mutableStateOf(false) }
    val removing = state.mode == TagEditMode.REMOVE
    Text(stringResource(when {
        removing -> R.string.remove_tags_hint
        state.forEditor -> R.string.edit_editor_tags_hint
        else -> R.string.edit_tags_hint
    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TagSearchInput(query, onPendingChange, Modifier.fillMaxWidth().testTag("edit_tag_search"),
        busy = state.saving, error = state.error != null,
        onAdd = if (removing) null else onCreate, addKey = "create_draft_tag", onComposingChange = { composing = it })
    if (!removing && query.isNotBlank()) {
        val existing = tags.any { LibraryTags.key(it.name) == LibraryTags.key(query.trim()) } ||
            state.newNames.any { LibraryTags.key(it) == LibraryTags.key(query.trim()) }
        Text(stringResource(if (existing) R.string.tag_select_on_save else R.string.tag_add_on_save, query.trim()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("tag_pending_save_hint"))
    }
    if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("tag_error"))
    LazyVerticalGrid(tagColumns(), Modifier.fillMaxWidth().weight(1f, fill = false).testTag("edit_tag_list"),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        gridItems(tags.filter { it.id in state.selectedIds || it.name.contains(query.trim(), true) }, key = { it.id }) { tag ->
            CompactTagChoice(tag.name, tag.id in state.selectedIds, null, "edit_tag_${tag.id}", !state.saving,
                onRemove = if (tag.id in state.selectedIds) ({ onRemoveSelected(tag.id) }) else null) { onToggle(tag.id) }
        }
        // Pending names remain visible even while filtering the catalog.
        gridItems(state.newNames, key = { "new:$it" }) { name ->
            CompactTagChoice(stringResource(R.string.draft_tag_name, name), true, null, "draft_tag_$name", !state.saving,
                onRemove = { onToggleNew(name) }) { onToggleNew(name) }
        }
        if (tags.isEmpty() && state.newNames.isEmpty()) item {
            Text(stringResource(R.string.no_tags_yet), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        } else if (state.newNames.isEmpty() && tags.none { it.id in state.selectedIds || it.name.contains(query.trim(), true) }) item {
            Text(stringResource(R.string.no_matching_tags), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    Button(onClick = onApply, enabled = !state.saving && !composing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("apply_tags")) {
        Text(stringResource(when {
            state.saving -> R.string.saving_tags
            state.forEditor -> R.string.apply_editor_tags
            else -> R.string.apply_tags
        }))
    }
}

@Composable
internal fun ColumnScope.TagManagerContent(state: HomeUiState, onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit, onDelete: (String) -> Unit, onShowSorting: (String) -> Unit = {},
    onNewNameChange: (String) -> Unit = {}) {
    val query = state.newTagName
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val ordered = remember(state.bookmarks, state.tags, state.sorting, locale) {
        LibraryOrdering.tags(LibrarySnapshot(state.bookmarks, state.tags), state.sorting, locale)
    }
    val counts = remember(state.bookmarks) { LibraryOrdering.tagCounts(state.bookmarks) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TagSearchInput(query, onNewNameChange, Modifier.weight(1f).testTag("manage_tag_search"),
            busy = state.tagSaving || state.transferBusy || state.reorder != null, error = state.tagError != null,
            onAdd = onCreate, addKey = "create_tag")
        IconButton(onClick = { onShowSorting(query) }, enabled = !state.tagSaving && !state.transferBusy && state.reorder == null,
            modifier = Modifier.testTag("open_tag_sort")) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_current, sortLabel(state.sorting, OrderTarget.TAGS)))
        }
    }
    (validationError ?: state.tagError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("manage_tag_list")) {
        items(ordered.filter { it.name.contains(query.trim(), true) }, key = { it.id }) { tag ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tag.name, Modifier.weight(1f).padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.tag_bookmark_count, counts[tag.id] ?: 0), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { renameId = tag.id; renameName = tag.name }, enabled = !state.tagSaving) {
                    Icon(Icons.Default.Edit, stringResource(R.string.rename_tag_name, tag.name))
                }
                IconButton(onClick = { deleteId = tag.id }, enabled = !state.tagSaving) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_tag_name, tag.name))
                }
            }
        }
        if (state.tags.isEmpty()) item { Text(stringResource(R.string.no_tags_yet), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall) }
        else if (ordered.none { it.name.contains(query.trim(), true) }) item {
            Text(stringResource(R.string.no_matching_tags), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (renameId != null) AlertDialog(onDismissRequest = { renameId = null },
        title = { Text(stringResource(R.string.rename_tag)) }, text = {
            OutlinedTextField(renameName, { renameName = it }, label = { Text(stringResource(R.string.tag_name)) }, singleLine = true)
        }, confirmButton = { TextButton(onClick = {
            val result = runCatching { LibraryTags.name(renameName) }
            validationError = result.exceptionOrNull()?.message
            if (result.isSuccess) { onRename(renameId!!, result.getOrThrow()); renameId = null }
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = { renameId = null }) { Text(stringResource(R.string.cancel)) } })
    state.tags.firstOrNull { it.id == deleteId }?.let { tag ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.delete_tag_name, tag.name)) },
            text = { Text(stringResource(R.string.delete_tag_body)) },
            confirmButton = { TextButton(onClick = { deleteId = null; onDelete(tag.id) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
internal fun BookmarkTagRow(tags: List<BookmarkTag>, selected: Set<String>, onClick: (String) -> Unit, enabled: Boolean = true, onMoreClick: (() -> Unit)? = null) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        fun labelWidth(text: String) = with(density) { measurer.measure(AnnotatedString(text), style).size.width.toDp() }
        val widths = mutableListOf<androidx.compose.ui.unit.Dp>()
        var used = 0.dp
        for (tag in tags) {
            val width = (labelWidth(tag.name) + 16.dp).coerceAtLeast(48.dp).coerceAtMost(maxWidth)
            if (widths.isNotEmpty() && used + gap + width > maxWidth) break
            used += if (widths.isEmpty()) width else gap + width
            widths += width
        }
        var overflowWidth = 0.dp
        if (widths.size < tags.size) {
            while (true) {
                overflowWidth = (labelWidth("+${tags.size - widths.size}") + 24.dp).coerceAtLeast(48.dp).coerceAtMost(maxWidth)
                if (used + (if (widths.isEmpty()) 0.dp else gap) + overflowWidth <= maxWidth) break
                if (widths.size == 1 && maxWidth - gap - overflowWidth >= 48.dp) {
                    widths[0] = maxWidth - gap - overflowWidth
                    break
                }
                if (widths.isEmpty()) break
                widths.removeAt(widths.lastIndex)
                used = widths.fold(0.dp) { total, width -> if (total == 0.dp) width else total + gap + width }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
            tags.take(widths.size).forEachIndexed { index, tag ->
                Box(Modifier.width(widths[index])) {
                    CompactTagChoice(tag.name, tag.id in selected, null, "card_tag_${tag.id}", enabled, fill = false) { onClick(tag.id) }
                }
            }
            if (tags.size > widths.size) TextButton(onClick = { if (onMoreClick != null) onMoreClick() else showAll = true }, enabled = enabled,
                modifier = Modifier.width(overflowWidth).heightIn(min = 48.dp).testTag("more_tags"),
                contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("+${tags.size - widths.size}", style = style, maxLines = 1)
            }
        }
    }
    if (showAll) TagPanel(stringResource(R.string.bookmark_tags), { showAll = false }) {
        LazyColumn {
            items(tags, key = { it.id }) { tag ->
                CompactTagChoice(tag.name, tag.id in selected, null, "all_card_tag_${tag.id}") { onClick(tag.id); showAll = false }
            }
        }
    }
}
