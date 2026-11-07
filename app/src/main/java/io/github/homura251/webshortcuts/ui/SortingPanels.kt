package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.*
import kotlinx.coroutines.isActive
import kotlin.math.abs

data class SortActions(
    val change: (LibrarySortPreferences) -> Unit = {},
    val begin: (OrderTarget, String) -> Unit = { _, _ -> },
    val move: (String, Int) -> Unit = { _, _ -> },
    val save: () -> Unit = {},
    val cancel: () -> Unit = {},
)

@Composable
internal fun sortLabel(value: LibrarySortPreferences, target: OrderTarget): String = stringResource(
    if (target == OrderTarget.BOOKMARKS) when (value.bookmarks) {
        BookmarkSortField.CREATED_AT -> if (value.bookmarkDirection == SortDirection.DESCENDING) R.string.sort_newest else R.string.sort_oldest
        BookmarkSortField.UPDATED_AT -> if (value.bookmarkDirection == SortDirection.DESCENDING) R.string.sort_recent_update else R.string.sort_old_update
        BookmarkSortField.TITLE -> if (value.bookmarkDirection == SortDirection.ASCENDING) R.string.sort_name_ascending else R.string.sort_name_descending
        BookmarkSortField.MANUAL -> R.string.sort_manual
    } else when (value.tags) {
        TagSortField.NAME -> if (value.tagDirection == SortDirection.ASCENDING) R.string.sort_name_ascending else R.string.sort_name_descending
        TagSortField.COUNT -> if (value.tagDirection == SortDirection.DESCENDING) R.string.sort_count_descending else R.string.sort_count_ascending
        TagSortField.MANUAL -> R.string.sort_manual
    })

@Composable
internal fun SortingSheet(home: HomeUiState, target: OrderTarget, query: String, onDismiss: () -> Unit, actions: SortActions) {
    TagPanel(stringResource(if (target == OrderTarget.BOOKMARKS) R.string.sort_bookmarks else R.string.sort_tags), onDismiss) {
        val available = !home.loading && home.error == null && !home.transferBusy && !home.selectionMode && !home.tagSaving
        val count = if (target == OrderTarget.BOOKMARKS) LibraryTags.filter(home.bookmarks, home.tags, home.query, home.tagFilter).size
            else home.tags.count { it.name.contains(query.trim(), true) }
        SortingContent(home.sorting, target, actions.change, { onDismiss(); actions.begin(target, query) }, available, count >= 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SortingContent(value: LibrarySortPreferences, target: OrderTarget,
    onChange: (LibrarySortPreferences) -> Unit, onOrganize: () -> Unit, enabled: Boolean = true, canOrganize: Boolean = true) {
    val bookmarks = target == OrderTarget.BOOKMARKS
    val manual = if (bookmarks) value.bookmarks == BookmarkSortField.MANUAL else value.tags == TagSortField.MANUAL
    val direction = if (bookmarks) value.bookmarkDirection else value.tagDirection
    Text(stringResource(R.string.sort_local_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("sort_options")) {
        if (bookmarks) items(BookmarkSortField.entries.size) { index ->
            val field = BookmarkSortField.entries[index]
            SortChoice(stringResource(when (field) {
                BookmarkSortField.CREATED_AT -> R.string.sort_created
                BookmarkSortField.UPDATED_AT -> R.string.sort_updated
                BookmarkSortField.TITLE -> R.string.sort_name
                BookmarkSortField.MANUAL -> R.string.sort_manual
            }), field == value.bookmarks, enabled, "sort_bookmarks_${field.name}") {
                onChange(value.copy(bookmarks = field, bookmarkDirection = if (field == value.bookmarks) value.bookmarkDirection
                    else if (field == BookmarkSortField.TITLE) SortDirection.ASCENDING else SortDirection.DESCENDING))
            }
        } else items(TagSortField.entries.size) { index ->
            val field = TagSortField.entries[index]
            SortChoice(stringResource(when (field) {
                TagSortField.NAME -> R.string.sort_name
                TagSortField.COUNT -> R.string.sort_count
                TagSortField.MANUAL -> R.string.sort_manual
            }), field == value.tags, enabled, "sort_tags_${field.name}") {
                onChange(value.copy(tags = field, tagDirection = if (field == value.tags) value.tagDirection
                    else if (field == TagSortField.NAME) SortDirection.ASCENDING else SortDirection.DESCENDING))
            }
        }
        if (!manual) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortDirection.entries.forEach { next ->
                    FilterChip(direction == next, {
                        onChange(if (bookmarks) value.copy(bookmarkDirection = next) else value.copy(tagDirection = next))
                    }, label = { Text(stringResource(if (next == SortDirection.ASCENDING) R.string.sort_ascending else R.string.sort_descending)) },
                        enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("sort_direction_${next.name}"))
                }
            }
        }
        if ((bookmarks && value.bookmarks == BookmarkSortField.UPDATED_AT) || (!bookmarks && value.tags == TagSortField.COUNT)) item {
            Text(stringResource(if (bookmarks) R.string.sort_updated_hint else R.string.sort_count_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (manual) Button(onOrganize, enabled = enabled && canOrganize,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("organize_order")) { Text(stringResource(R.string.organize_order)) }
}

@Composable
private fun SortChoice(label: String, selected: Boolean, enabled: Boolean, key: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, enabled = enabled,
        role = Role.RadioButton, onClick = onClick).testTag(key), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ReorderDialog(home: HomeUiState, actions: SortActions) {
    val draft = home.reorder ?: return
    Dialog(onDismissRequest = { if (!draft.saving) actions.cancel() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = !draft.saving, dismissOnClickOutside = false)) {
        ReorderContent(home, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReorderContent(home: HomeUiState, actions: SortActions, modifier: Modifier = Modifier) {
    val draft = home.reorder ?: return
    Scaffold(modifier.fillMaxSize().testTag("reorder_screen"), topBar = {
        TopAppBar(title = { Text(stringResource(if (draft.target == OrderTarget.BOOKMARKS) R.string.organize_bookmarks else R.string.organize_tags),
            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(actions.cancel, enabled = !draft.saving, modifier = Modifier.testTag("cancel_reorder")) {
                Icon(Icons.Default.Close, stringResource(R.string.cancel)) } },
            actions = { TextButton(actions.save, enabled = !draft.saving, modifier = Modifier.testTag("save_reorder")) {
                Text(stringResource(if (draft.saving) R.string.organize_saving else R.string.organize_done)) } })
    }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).widthIn(max = 760.dp).fillMaxSize()) {
            Text(stringResource(if (draft.query.isNotBlank() || draft.filter.active) R.string.organize_filtered_hint else R.string.organize_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            draft.error?.let { Text(it, Modifier.padding(horizontal = 16.dp).testTag("reorder_error"),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            ReorderList(home, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReorderList(home: HomeUiState, actions: SortActions, modifier: Modifier) {
    val draft = home.reorder ?: return
    val list = rememberLazyListState()
    var draggedId by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    var pendingAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val currentIds by rememberUpdatedState(draft.orderedIds)
    val currentMove by rememberUpdatedState(actions.move)
    val currentSaving by rememberUpdatedState(draft.saving)
    val edge = with(LocalDensity.current) { 56.dp.toPx() }
    val maxScroll = with(LocalDensity.current) { 18.dp.toPx() }
    val handleSize = with(LocalDensity.current) { 48.dp.toPx() }
    val sidePadding = with(LocalDensity.current) { 16.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bookmarks = remember(home.bookmarks) { home.bookmarks.associateBy { it.id } }
    val tags = remember(home.tags) { home.tags.associateBy { it.id } }
    val counts = remember(home.bookmarks) { LibraryOrdering.tagCounts(home.bookmarks) }

    fun moveAtPointer() {
        val id = draggedId ?: return
        if (currentSaving || pendingAnchor != null) return
        val layout = list.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull() ?: return
        val last = layout.visibleItemsInfo.last()
        val top = maxOf(layout.viewportStartOffset, first.offset)
        val bottom = minOf(layout.viewportEndOffset, last.offset + last.size)
        if (bottom <= top) return
        val y = center.coerceIn(top + .5f, bottom - .5f)
        val target = layout.visibleItemsInfo.firstOrNull { it.key != id && y >= it.offset && y < it.offset + it.size } ?: return
        val to = target.index
        if (to in currentIds.indices && currentIds.indexOf(id) != to) {
            val firstIndex = list.firstVisibleItemIndex
            val firstOffset = list.firstVisibleItemScrollOffset
            if (first.key == id || to == firstIndex) pendingAnchor = firstIndex to firstOffset
            currentMove(id, to)
        }
    }
    // Apply after the new item provider is composed; doing this during the input event would
    // anchor the old provider to the dragged key and scroll it to its new index on remeasurement.
    LaunchedEffect(draft.orderedIds) {
        pendingAnchor?.let { (index, offset) -> list.requestScrollToItem(index, offset) }
        pendingAnchor = null
    }
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        while (isActive && draggedId != null && !currentSaving) {
            withFrameNanos { }
            if (pendingAnchor != null) continue
            val layout = list.layoutInfo
            val distance = when {
                center < layout.viewportStartOffset + edge -> ((center - layout.viewportStartOffset - edge) / edge * maxScroll).coerceAtLeast(-maxScroll)
                center > layout.viewportEndOffset - edge -> ((center - layout.viewportEndOffset + edge) / edge * maxScroll).coerceAtMost(maxScroll)
                else -> 0f
            }
            if (distance != 0f) { list.scrollBy(distance); moveAtPointer() }
        }
    }
    LaunchedEffect(draft.saving) { if (draft.saving) draggedId = null }
    // The gesture belongs to the list: moving or recycling a row must not cancel its drag.
    val handles = Modifier.pointerInput(draft.target, draft.saving, rtl) {
        if (!draft.saving) awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val x = down.position.x
            val inHandle = if (rtl) x >= sidePadding && x < sidePadding + handleSize
                else x >= size.width - sidePadding - handleSize && x < size.width - sidePadding
            if (!inHandle) return@awaitEachGesture
            val y = down.position.y + list.layoutInfo.viewportStartOffset
            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { y >= it.offset && y < it.offset + it.size }
                ?: return@awaitEachGesture
            val id = item.key as? String ?: return@awaitEachGesture
            down.consume()
            var distance = 0f
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    change.consume()
                    if (!change.pressed) break
                    val delta = change.position.y - change.previousPosition.y
                    if (draggedId == null) {
                        distance += delta
                        if (abs(distance) >= viewConfiguration.touchSlop) {
                            center = item.offset + item.size / 2f + distance
                            draggedId = id
                            moveAtPointer()
                        }
                    } else { center += delta; moveAtPointer() }
                }
            } finally { draggedId = null }
        }
    }
    LazyColumn(modifier.fillMaxWidth().navigationBarsPadding().testTag("reorder_list").then(handles), state = list,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(draft.orderedIds, key = { _, id -> id }) { index, id ->
            val title = if (draft.target == OrderTarget.BOOKMARKS) bookmarks[id]?.title.orEmpty() else tags[id]?.name.orEmpty()
            val subtitle = if (draft.target == OrderTarget.BOOKMARKS) bookmarks[id]?.url.orEmpty()
                else stringResource(R.string.tag_bookmark_count, counts[id] ?: 0)
            val up = stringResource(R.string.organize_up)
            val down = stringResource(R.string.organize_down)
            val position = stringResource(R.string.organize_position, index + 1, draft.orderedIds.size)
            var menu by remember(id) { mutableStateOf(false) }
            val moving = draggedId == id
            val rowModifier = if (moving) Modifier.zIndex(1f).graphicsLayer {
                val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                translationY = if (item != null) center - item.offset - item.size / 2f else 0f
            } else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
            Surface(rowModifier.fillMaxWidth().testTag("reorder_row_$id").semantics {
                stateDescription = position
                customActions = if (draft.saving) emptyList() else buildList {
                    if (index > 0) add(CustomAccessibilityAction(up) { actions.move(id, index - 1); true })
                    if (index < draft.orderedIds.lastIndex) add(CustomAccessibilityAction(down) { actions.move(id, index + 1); true })
                }
            }, shape = MaterialTheme.shapes.medium, color = if (moving) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = if (moving) 6.dp else 0.dp) {
                Row(Modifier.heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box {
                        IconButton({ menu = true }, enabled = !draft.saving && draggedId == null,
                            modifier = Modifier.testTag("reorder_menu_$id")) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.organize_actions, title))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(up) }, onClick = { menu = false; actions.move(id, index - 1) },
                                enabled = !draft.saving && index > 0, modifier = Modifier.testTag("reorder_up_$id"))
                            DropdownMenuItem(text = { Text(down) }, onClick = { menu = false; actions.move(id, index + 1) },
                                enabled = !draft.saving && index < draft.orderedIds.lastIndex, modifier = Modifier.testTag("reorder_down_$id"))
                        }
                    }
                    Box(Modifier.size(48.dp).testTag("reorder_drag_$id"), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DragHandle, stringResource(R.string.organize_drag, title),
                            tint = if (draft.saving) MaterialTheme.colorScheme.onSurface.copy(alpha = .38f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
