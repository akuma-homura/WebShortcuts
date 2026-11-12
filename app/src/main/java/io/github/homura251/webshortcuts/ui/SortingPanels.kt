package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkSortField
import io.github.homura251.webshortcuts.data.LibraryOrdering
import io.github.homura251.webshortcuts.data.LibrarySortPreferences
import io.github.homura251.webshortcuts.data.LibraryTags
import io.github.homura251.webshortcuts.data.SortDirection
import io.github.homura251.webshortcuts.data.TagSortField
import io.github.homura251.webshortcuts.ui.components.ConnectedChoiceItem
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveChoiceOption
import io.github.homura251.webshortcuts.ui.components.ExpressiveConnectedChoices
import io.github.homura251.webshortcuts.ui.components.ExpressiveIconButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalIconButton
import io.github.homura251.webshortcuts.ui.components.connectedItemShape
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import kotlin.math.abs
import kotlinx.coroutines.isActive

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
    }
)

@Composable
internal fun SortingSheet(home: HomeUiState, target: OrderTarget, query: String, onDismiss: () -> Unit, actions: SortActions) {
    TagPanel(stringResource(if (target == OrderTarget.BOOKMARKS) R.string.sort_bookmarks else R.string.sort_tags), onDismiss) {
        val available = !home.loading && home.error == null && !home.transferBusy && !home.selectionMode && !home.tagSaving
        val count = if (target == OrderTarget.BOOKMARKS) {
            LibraryTags.filter(home.bookmarks, home.tags, home.query, home.tagFilter).count {
                when (home.sourceFilter) {
                    SourceFilter.ALL -> true
                    SourceFilter.WEB -> !it.isLocal
                    SourceFilter.LOCAL -> it.isLocal
                }
            }
        } else {
            home.tags.count { it.name.contains(query.trim(), true) }
        }
        SortingContent(home.sorting, target, actions.change, { onDismiss(); actions.begin(target, query) }, available, count >= 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SortingContent(
    value: LibrarySortPreferences,
    target: OrderTarget,
    onChange: (LibrarySortPreferences) -> Unit,
    onOrganize: () -> Unit,
    enabled: Boolean = true,
    canOrganize: Boolean = true,
) {
    val bookmarks = target == OrderTarget.BOOKMARKS
    val manual = if (bookmarks) value.bookmarks == BookmarkSortField.MANUAL else value.tags == TagSortField.MANUAL
    val direction = if (bookmarks) value.bookmarkDirection else value.tagDirection
    Text(
        stringResource(R.string.sort_local_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f, fill = false).testTag("sort_options"),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        if (bookmarks) items(BookmarkSortField.entries.size) { index ->
            val field = BookmarkSortField.entries[index]
            SortChoice(
                label = stringResource(
                    when (field) {
                        BookmarkSortField.CREATED_AT -> R.string.sort_created
                        BookmarkSortField.UPDATED_AT -> R.string.sort_updated
                        BookmarkSortField.TITLE -> R.string.sort_name
                        BookmarkSortField.MANUAL -> R.string.sort_manual
                    }
                ),
                icon = when (field) {
                    BookmarkSortField.CREATED_AT -> Icons.Default.Schedule
                    BookmarkSortField.UPDATED_AT -> Icons.Default.History
                    BookmarkSortField.TITLE -> Icons.Default.SortByAlpha
                    BookmarkSortField.MANUAL -> Icons.Default.DragIndicator
                },
                selected = field == value.bookmarks,
                enabled = enabled,
                key = "sort_bookmarks_${field.name}",
                index = index,
                count = BookmarkSortField.entries.size,
            ) {
                onChange(
                    value.copy(
                        bookmarks = field,
                        bookmarkDirection = if (field == value.bookmarks) value.bookmarkDirection
                        else if (field == BookmarkSortField.TITLE) SortDirection.ASCENDING else SortDirection.DESCENDING,
                    )
                )
            }
        } else items(TagSortField.entries.size) { index ->
            val field = TagSortField.entries[index]
            SortChoice(
                label = stringResource(
                    when (field) {
                        TagSortField.NAME -> R.string.sort_name
                        TagSortField.COUNT -> R.string.sort_count
                        TagSortField.MANUAL -> R.string.sort_manual
                    }
                ),
                icon = when (field) {
                    TagSortField.NAME -> Icons.Default.SortByAlpha
                    TagSortField.COUNT -> Icons.Default.Numbers
                    TagSortField.MANUAL -> Icons.Default.DragIndicator
                },
                selected = field == value.tags,
                enabled = enabled,
                key = "sort_tags_${field.name}",
                index = index,
                count = TagSortField.entries.size,
            ) {
                onChange(
                    value.copy(
                        tags = field,
                        tagDirection = if (field == value.tags) value.tagDirection
                        else if (field == TagSortField.NAME) SortDirection.ASCENDING else SortDirection.DESCENDING,
                    )
                )
            }
        }
        if (!manual) {
            item {
                Box(Modifier.padding(top = 8.dp)) {
                    ExpressiveConnectedChoices(
                        options = SortDirection.entries.map { next ->
                            ExpressiveChoiceOption(
                                value = next,
                                label = stringResource(if (next == SortDirection.ASCENDING) R.string.sort_ascending else R.string.sort_descending),
                                testTag = "sort_direction_${next.name}",
                            )
                        },
                        selected = direction,
                        onSelect = { next ->
                            onChange(if (bookmarks) value.copy(bookmarkDirection = next) else value.copy(tagDirection = next))
                        },
                        enabled = enabled,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if ((bookmarks && value.bookmarks == BookmarkSortField.UPDATED_AT) || (!bookmarks && value.tags == TagSortField.COUNT)) {
            item {
                Text(
                    stringResource(if (bookmarks) R.string.sort_updated_hint else R.string.sort_count_hint),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (manual) {
        ExpressiveButton(
            onClick = onOrganize,
            enabled = enabled && canOrganize,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("organize_order"),
        ) {
            Text(stringResource(R.string.organize_order))
        }
    }
}

@Composable
private fun SortChoice(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    key: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    ConnectedChoiceItem(
        index = index,
        count = count,
        title = label,
        selected = selected,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
        modifier = Modifier.testTag(key),
    )
}

@Composable
internal fun ReorderDialog(home: HomeUiState, actions: SortActions) {
    val draft = home.reorder ?: return
    Dialog(
        onDismissRequest = { if (!draft.saving) actions.cancel() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = !draft.saving,
            dismissOnClickOutside = false,
        ),
    ) {
        ReorderContent(home, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReorderContent(home: HomeUiState, actions: SortActions, modifier: Modifier = Modifier) {
    val draft = home.reorder ?: return
    val surfaces = AppTheme.surfaces
    Scaffold(
        modifier = modifier.fillMaxSize().testTag("reorder_screen"),
        containerColor = surfaces.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (draft.target == OrderTarget.BOOKMARKS) R.string.organize_bookmarks else R.string.organize_tags),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaces.canvas,
                    scrolledContainerColor = surfaces.scrolledBar,
                ),
                navigationIcon = {
                    ExpressiveTonalIconButton(actions.cancel, enabled = !draft.saving, modifier = Modifier.testTag("cancel_reorder")) {
                        Icon(Icons.Default.Close, stringResource(R.string.cancel))
                    }
                },
                actions = {
                    ExpressiveTextButton(actions.save, enabled = !draft.saving, modifier = Modifier.testTag("save_reorder")) {
                        Text(stringResource(if (draft.saving) R.string.organize_saving else R.string.organize_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).widthIn(max = AppSpacing.contentMaxWidth).fillMaxSize()) {
            Text(
                stringResource(
                    if (draft.query.isNotBlank() || draft.filter.active || draft.sourceFilter != SourceFilter.ALL) {
                        R.string.organize_filtered_hint
                    } else {
                        R.string.organize_hint
                    }
                ),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draft.error?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = 16.dp).testTag("reorder_error"),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
    val surfaces = AppTheme.surfaces
    val scheme = MaterialTheme.colorScheme

    fun moveAtPointer() {
        val id = draggedId ?: return
        if (currentSaving || pendingAnchor != null) return
        val layout = list.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull() ?: return
        val last = layout.visibleItemsInfo.last()
        val top = maxOf(layout.viewportStartOffset, first.offset)
        val bottom = minOf(layout.viewportEndOffset, last.offset + last.size)
        if (bottom <= top) return
        val y = center.coerceIn(top + 0.5f, bottom - 0.5f)
        val target = layout.visibleItemsInfo.firstOrNull { it.key != id && y >= it.offset && y < it.offset + it.size } ?: return
        val to = target.index
        if (to in currentIds.indices && currentIds.indexOf(id) != to) {
            val firstIndex = list.firstVisibleItemIndex
            val firstOffset = list.firstVisibleItemScrollOffset
            if (first.key == id || to == firstIndex) pendingAnchor = firstIndex to firstOffset
            currentMove(id, to)
        }
    }

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
                center < layout.viewportStartOffset + edge ->
                    ((center - layout.viewportStartOffset - edge) / edge * maxScroll).coerceAtLeast(-maxScroll)
                center > layout.viewportEndOffset - edge ->
                    ((center - layout.viewportEndOffset + edge) / edge * maxScroll).coerceAtMost(maxScroll)
                else -> 0f
            }
            if (distance != 0f) {
                list.scrollBy(distance)
                moveAtPointer()
            }
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
                    } else {
                        center += delta
                        moveAtPointer()
                    }
                }
            } finally {
                draggedId = null
            }
        }
    }

    LazyColumn(
        modifier.fillMaxWidth().navigationBarsPadding().testTag("reorder_list").then(handles),
        state = list,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(draft.orderedIds, key = { _, id -> id }) { index, id ->
            val title = if (draft.target == OrderTarget.BOOKMARKS) bookmarks[id]?.title.orEmpty() else tags[id]?.name.orEmpty()
            val subtitle = if (draft.target == OrderTarget.BOOKMARKS) bookmarks[id]?.url.orEmpty()
            else stringResource(R.string.tag_bookmark_count, counts[id] ?: 0)
            val up = stringResource(R.string.organize_up)
            val down = stringResource(R.string.organize_down)
            val position = stringResource(R.string.organize_position, index + 1, draft.orderedIds.size)
            var menu by remember(id) { mutableStateOf(false) }
            val moving = draggedId == id
            val rowModifier = if (moving) {
                Modifier.zIndex(1f).graphicsLayer {
                    val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }
                    translationY = if (item != null) center - item.offset - item.size / 2f else 0f
                    scaleX = 1.015f
                    scaleY = 1.015f
                }
            } else {
                Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
            }

            Surface(
                rowModifier
                    .fillMaxWidth()
                    .testTag("reorder_row_$id")
                    .semantics {
                        stateDescription = position
                        customActions = if (draft.saving) emptyList() else buildList {
                            if (index > 0) add(CustomAccessibilityAction(up) { actions.move(id, index - 1); true })
                            if (index < draft.orderedIds.lastIndex) add(CustomAccessibilityAction(down) { actions.move(id, index + 1); true })
                        }
                    },
                shape = connectedItemShape(index, draft.orderedIds.size),
                color = if (moving) scheme.secondaryContainer else surfaces.card,
                border = if (moving) BorderStroke(1.5.dp, scheme.primary) else null,
                shadowElevation = if (moving) 8.dp else 0.dp,
            ) {
                Row(
                    Modifier.heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.padding(start = 14.dp).size(28.dp),
                        shape = CircleShape,
                        color = if (moving) scheme.primary else surfaces.inset,
                        contentColor = if (moving) scheme.onPrimary else scheme.onSurfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box {
                        ExpressiveIconButton(
                            onClick = { menu = true },
                            enabled = !draft.saving && draggedId == null,
                            modifier = Modifier.testTag("reorder_menu_$id"),
                        ) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.organize_actions, title))
                        }
                        DropdownMenu(
                            expanded = menu,
                            onDismissRequest = { menu = false },
                            shape = MaterialTheme.shapes.large,
                            containerColor = surfaces.elevated,
                        ) {
                            DropdownMenuItem(
                                text = { Text(up) },
                                onClick = { menu = false; actions.move(id, index - 1) },
                                enabled = !draft.saving && index > 0,
                                modifier = Modifier.testTag("reorder_up_$id"),
                            )
                            DropdownMenuItem(
                                text = { Text(down) },
                                onClick = { menu = false; actions.move(id, index + 1) },
                                enabled = !draft.saving && index < draft.orderedIds.lastIndex,
                                modifier = Modifier.testTag("reorder_down_$id"),
                            )
                        }
                    }
                    Box(Modifier.size(48.dp).testTag("reorder_drag_$id"), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.DragHandle,
                            stringResource(R.string.organize_drag, title),
                            tint = if (draft.saving) scheme.onSurface.copy(alpha = 0.38f) else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
