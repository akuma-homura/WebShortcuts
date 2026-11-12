package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.LibraryOrdering
import io.github.homura251.webshortcuts.data.LibrarySnapshot
import io.github.homura251.webshortcuts.data.TagFilter
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.ui.components.ExpressiveChoiceOption
import io.github.homura251.webshortcuts.ui.components.ExpressiveConnectedChoices
import io.github.homura251.webshortcuts.ui.components.ExpressiveFilterChip
import io.github.homura251.webshortcuts.ui.components.ExpressiveIconButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveLoading
import io.github.homura251.webshortcuts.ui.components.ExpressiveProgressBar
import io.github.homura251.webshortcuts.ui.components.ExpressiveSearchBar
import io.github.homura251.webshortcuts.ui.components.ExpressiveStatusPill
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalIconButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveVisibility
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.components.TonalEmphasis
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import io.github.homura251.webshortcuts.ui.theme.LayoutMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookmarkCollection(
    state: HomeUiState,
    iconResolver: IconResolver,
    layoutMode: LayoutMode,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onLayoutChange: (LayoutMode) -> Unit,
    onOpen: (BookmarkItem) -> Unit,
    onPin: (BookmarkItem) -> Unit,
    onEdit: (BookmarkItem) -> Unit,
    onRefresh: (BookmarkItem) -> Unit,
    onUpdateShortcut: (BookmarkItem) -> Unit,
    onDelete: (BookmarkItem) -> Unit,
    onClearRefreshSummary: () -> Unit,
    tagActions: TagActions = TagActions(),
    onShowTagFilter: () -> Unit = {},
    onShowSorting: () -> Unit = {},
    onImportLocal: () -> Unit = {},
    onSourceFilterChange: (SourceFilter) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val filtered = remember(state.bookmarks, state.tags, state.query, state.tagFilter, state.sorting, state.sourceFilter, locale) {
        LibraryOrdering.bookmarks(LibrarySnapshot(state.bookmarks, state.tags), state.query, state.tagFilter, state.sorting, locale)
            .filter {
                when (state.sourceFilter) {
                    SourceFilter.ALL -> true
                    SourceFilter.WEB -> !it.isLocal
                    SourceFilter.LOCAL -> it.isLocal
                }
            }
    }
    val orderedTags = remember(state.bookmarks, state.tags, state.sorting, locale) {
        LibraryOrdering.tags(LibrarySnapshot(state.bookmarks, state.tags), state.sorting, locale)
    }
    val grid = layoutMode == LayoutMode.GRID
    // Two narrow cells cannot accommodate enlarged text and both 48dp action targets.
    val columns = if (grid && LocalDensity.current.fontScale < 1.5f) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .widthIn(max = AppSpacing.contentMaxWidth)
            .fillMaxSize()
            .testTag("bookmark_collection"),
        contentPadding = PaddingValues(start = AppSpacing.page, end = AppSpacing.page, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.error != null) {
            item(key = "load_error", span = { GridItemSpan(maxLineSpan) }) {
                ErrorNotice(state.error, title = stringResource(R.string.read_error_title))
            }
        }
        if (state.importingLocal) {
            item(key = "local_import", span = { GridItemSpan(maxLineSpan) }) {
                TonalCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    ExpressiveLoading(label = stringResource(R.string.local_document_importing))
                }
            }
        }
        when {
            state.loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ExpressiveLoading()
                    Text(stringResource(R.string.loading_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.bookmarks.isEmpty() && state.error == null -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyBookmarks(onAdd, onImportLocal)
            }
            state.bookmarks.isNotEmpty() -> {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
                    ) {
                        Box(Modifier.weight(1f)) {
                            ExpressiveSearchBar(
                                query = state.query,
                                onQueryChange = onQueryChange,
                                placeholder = stringResource(R.string.search_hint),
                                clearDescription = stringResource(R.string.clear_search),
                                enabled = !state.selectionMode,
                            )
                        }
                        ExpressiveTonalIconButton(
                            onClick = onShowTagFilter,
                            enabled = !state.selectionMode,
                            highlighted = state.tagFilter.active,
                            modifier = Modifier.size(52.dp).testTag("open_tag_filter"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Label, stringResource(R.string.filter_tags))
                        }
                    }
                }
                item(key = "source_filters", span = { GridItemSpan(maxLineSpan) }) {
                    val options = listOf(
                        ExpressiveChoiceOption(SourceFilter.ALL, stringResource(R.string.source_all), testTag = "source_filter_ALL"),
                        ExpressiveChoiceOption(SourceFilter.WEB, stringResource(R.string.source_web), testTag = "source_filter_WEB"),
                        ExpressiveChoiceOption(SourceFilter.LOCAL, stringResource(R.string.source_local), testTag = "source_filter_LOCAL"),
                    )
                    ExpressiveConnectedChoices(
                        options = options,
                        selected = state.sourceFilter,
                        onSelect = onSourceFilterChange,
                        enabled = !state.selectionMode && state.reorder == null,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (state.query.isNotBlank() || state.tagFilter.active || state.sourceFilter != SourceFilter.ALL) {
                                ExpressiveStatusPill(
                                    text = stringResource(R.string.filtered_count, filtered.size),
                                    icon = Icons.Default.Search,
                                    emphasis = TonalEmphasis.PRIMARY,
                                )
                            } else {
                                ExpressiveStatusPill(
                                    text = stringResource(R.string.bookmark_count, state.bookmarks.count { !it.isLocal }),
                                    icon = Icons.Default.Language,
                                    emphasis = TonalEmphasis.SECONDARY,
                                )
                                val localCount = state.bookmarks.count { it.isLocal }
                                if (localCount > 0) {
                                    ExpressiveStatusPill(
                                        text = stringResource(R.string.home_local_count, localCount),
                                        icon = Icons.Default.FolderOpen,
                                        emphasis = TonalEmphasis.TERTIARY,
                                    )
                                }
                            }
                        }
                        ExpressiveTonalIconButton(
                            onClick = onShowSorting,
                            enabled = !state.selectionMode && !state.transferBusy && !state.tagSaving && state.reorder == null,
                            modifier = Modifier.testTag("open_bookmark_sort"),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                stringResource(R.string.sort_current, sortLabel(state.sorting, OrderTarget.BOOKMARKS)),
                            )
                        }
                        ExpressiveTonalIconButton(
                            onClick = { onLayoutChange(if (grid) LayoutMode.LIST else LayoutMode.GRID) },
                            enabled = state.reorder == null,
                            highlighted = grid,
                            modifier = Modifier.testTag("layout_toggle"),
                        ) {
                            Icon(
                                if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = stringResource(if (grid) R.string.show_list else R.string.show_grid),
                            )
                        }
                    }
                }
                if (state.tagFilter.active) {
                    item(key = "tag_filters", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (state.tagFilter.untagged) {
                                ExpressiveFilterChip(
                                    selected = true,
                                    onClick = { tagActions.filter(TagFilter()) },
                                    label = { Text(stringResource(R.string.untagged), style = MaterialTheme.typography.labelMedium) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                                    enabled = !state.selectionMode,
                                    modifier = Modifier.testTag("selected_untagged"),
                                )
                            }
                            orderedTags.filter { it.id in state.tagFilter.ids }.forEach { tag ->
                                ExpressiveFilterChip(
                                    selected = true,
                                    onClick = { tagActions.toggleFilter(tag.id) },
                                    label = { Text(tag.name, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                                    trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                                    enabled = !state.selectionMode,
                                    modifier = Modifier.testTag("selected_filter_${tag.id}"),
                                )
                            }
                            ExpressiveTextButton(
                                onClick = { tagActions.filter(TagFilter()) },
                                enabled = !state.selectionMode,
                                modifier = Modifier.testTag("clear_tag_filters"),
                            ) {
                                Text(stringResource(R.string.clear_tag_filters))
                            }
                        }
                    }
                }
                if (state.selectionMode) {
                    item(key = "batch_tags", span = { GridItemSpan(maxLineSpan) }) {
                        TonalCard(
                            emphasis = TonalEmphasis.SECONDARY,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.micro),
                            ) {
                                ExpressiveTonalButton(
                                    onClick = { tagActions.editBatch(false) },
                                    enabled = state.selectedIds.isNotEmpty() && !state.tagSaving,
                                    modifier = Modifier.testTag("batch_add_tags"),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.add_tags))
                                }
                                ExpressiveTonalButton(
                                    onClick = { tagActions.editBatch(true) },
                                    enabled = state.selectedIds.isNotEmpty() && !state.tagSaving,
                                    modifier = Modifier.testTag("batch_remove_tags"),
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.remove_tags))
                                }
                            }
                        }
                    }
                }
                if (state.refreshing || state.refreshSummary != null) {
                    item(key = "refresh_feedback", span = { GridItemSpan(maxLineSpan) }) {
                        RefreshFeedback(state, onClearRefreshSummary)
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "no_results", span = { GridItemSpan(maxLineSpan) }) {
                        TonalCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    stringResource(R.string.no_results_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    stringResource(R.string.no_results_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                ExpressiveTonalButton(
                                    onClick = {
                                        onQueryChange("")
                                        onSourceFilterChange(SourceFilter.ALL)
                                        tagActions.filter(TagFilter())
                                    },
                                    enabled = !state.selectionMode,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Text(stringResource(R.string.clear_search))
                                }
                            }
                        }
                    }
                }
                items(filtered, key = { it.id }, contentType = { "bookmark" }) { bookmark ->
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
                        onDelete = { onDelete(bookmark) },
                        compact = columns > 1,
                        tags = orderedTags.filter { it.id in bookmark.tagIds },
                        selectedTags = state.tagFilter.ids,
                        onTagClick = tagActions.toggleFilter,
                        onEditTags = { tagActions.edit(bookmark) },
                        onLongClick = { tagActions.select(bookmark) },
                        selectionMode = state.selectionMode,
                        selected = bookmark.id in state.selectedIds,
                        onSelect = { tagActions.select(bookmark) },
                        interactionBlocked = state.transferBusy || state.tagSaving,
                        browserName = browserLabel(bookmark.browserPackage, state.browsers),
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshFeedback(state: HomeUiState, onClear: () -> Unit) {
    var lastSummary by remember { mutableStateOf(state.refreshSummary.orEmpty()) }
    LaunchedEffect(state.refreshSummary) { state.refreshSummary?.let { lastSummary = it } }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
        ExpressiveVisibility(state.refreshing) {
            TonalCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalSpacing = 8.dp) {
                ExpressiveProgressBar(
                    progress = {
                        if (state.refreshTotal > 0) (state.refreshCompleted.toFloat() / state.refreshTotal).coerceIn(0f, 1f) else 0f
                    },
                )
                Text(
                    stringResource(R.string.refresh_progress_count, state.refreshCompleted, state.refreshTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExpressiveVisibility(state.refreshSummary != null) {
            TonalCard(
                emphasis = TonalEmphasis.SECONDARY,
                contentPadding = PaddingValues(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lastSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    ExpressiveIconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss_refresh_summary))
                    }
                }
            }
        }
    }
}
