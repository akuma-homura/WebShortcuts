package io.github.homura251.webshortcuts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconResolver
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
) {
    val filtered = remember(state.bookmarks, state.query) {
        val query = state.query.trim()
        state.bookmarks.filter { query.isEmpty() || it.title.contains(query, true) || it.url.contains(query, true) }
    }
    val autoCount = state.bookmarks.count { it.selectedIconSource == IconSourceMode.AUTO }
    val grid = layoutMode == LayoutMode.GRID
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 2 else 1),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("bookmark_collection"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.bookmarks.isNotEmpty()) {
            item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.home_tagline), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountBadge(stringResource(if (state.query.isBlank()) R.string.bookmark_count else R.string.filtered_count,
                                filtered.size), Icons.Default.Language)
                            if (state.query.isBlank()) CountBadge(stringResource(R.string.auto_count, autoCount), Icons.Default.Sync)
                        }
                        FilledTonalIconButton(onClick = { onLayoutChange(if (grid) LayoutMode.LIST else LayoutMode.GRID) },
                            modifier = Modifier.testTag("layout_toggle")) {
                            Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = stringResource(if (grid) R.string.show_list else R.string.show_grid))
                        }
                    }
                }
            }
        }
        if (state.error != null) item(key = "load_error", span = { GridItemSpan(maxLineSpan) }) {
            ErrorNotice(state.error, title = stringResource(R.string.read_error_title))
        }
        when {
            state.loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.loading_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.bookmarks.isEmpty() && state.error == null -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyBookmarks(onAdd)
            }
            state.bookmarks.isNotEmpty() -> {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) { BookmarkSearch(state.query, onQueryChange) }
                item(key = "refresh_feedback", span = { GridItemSpan(maxLineSpan) }) {
                    RefreshFeedback(state, onClearRefreshSummary)
                }
                if (filtered.isEmpty()) item(key = "no_results", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.no_results_title), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.no_results_body), Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onQueryChange("") }, Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.clear_search)) }
                    }
                }
                items(filtered, key = { it.id }, contentType = { "bookmark" }) { bookmark ->
                    BookmarkCard(
                        bookmark, iconResolver, bookmark.id in state.refreshingIds, state.refreshMessages[bookmark.id],
                        onOpen = { onOpen(bookmark) }, onPin = { onPin(bookmark) }, onEdit = { onEdit(bookmark) },
                        onRefresh = { onRefresh(bookmark) }, onUpdateShortcut = { onUpdateShortcut(bookmark) },
                        onDelete = { onDelete(bookmark) }, compact = grid,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountBadge(text: String, icon: ImageVector) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun BookmarkSearch(query: String, onQueryChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (focused) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        TextField(
            value = query, onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(28.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun RefreshFeedback(state: HomeUiState, onClear: () -> Unit) {
    var lastSummary by remember { mutableStateOf(state.refreshSummary.orEmpty()) }
    LaunchedEffect(state.refreshSummary) { state.refreshSummary?.let { lastSummary = it } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(state.refreshing) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = { if (state.refreshTotal > 0) (state.refreshCompleted.toFloat() / state.refreshTotal).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.refresh_progress_count, state.refreshCompleted, state.refreshTotal),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(state.refreshSummary != null) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(lastSummary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss_refresh_summary))
                    }
                }
            }
        }
    }
}
