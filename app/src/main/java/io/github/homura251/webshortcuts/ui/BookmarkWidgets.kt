package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import io.github.homura251.webshortcuts.ui.components.InteractiveTonalCard
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton as Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton as FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.data.BookmarkTag
import io.github.homura251.webshortcuts.icons.IconPolicy
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.ui.theme.animatedIconTint
import io.github.homura251.webshortcuts.ui.theme.rememberIconAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EmptyBookmarks(onAdd: () -> Unit, onImportLocal: () -> Unit = {}) {
    val compact = LocalConfiguration.current.screenHeightDp < 640
    val heroSize = if (compact) 64.dp else 80.dp
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.size(heroSize), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(Modifier.size(heroSize - 12.dp), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.BookmarkBorder, contentDescription = null,
                                modifier = Modifier.padding(if (compact) 10.dp else 16.dp).size(if (compact) 24.dp else 36.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 390.dp).padding(top = 8.dp),
        )
        Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_first_site))
        }
        FilledTonalButton(onClick = onImportLocal, modifier = Modifier.padding(top = 8.dp).testTag("empty_import_local")) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_local_page))
        }
        Text(stringResource(R.string.empty_local_action_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 390.dp).padding(top = 12.dp))
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 440.dp).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmptyStep(Icons.Default.Link, stringResource(R.string.empty_step_one))
                EmptyStep(Icons.Default.Image, stringResource(R.string.empty_step_two))
                EmptyStep(Icons.Default.PushPin, stringResource(R.string.empty_step_three))
            }
        }
    }
}

@Composable
private fun EmptyStep(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BookmarkCard(
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
    compact: Boolean = false,
    tags: List<BookmarkTag> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    onTagClick: (String) -> Unit = {},
    onEditTags: () -> Unit = {},
    onLongClick: () -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    interactionBlocked: Boolean = false,
    browserName: String? = null,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = bookmark.copy(tagIds = emptySet(), updatedAt = 0), key2 = iconResolver) {
        value = try {
            withContext(Dispatchers.IO) { iconResolver.resolve(bookmark) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    val accent by rememberIconAccent(bitmap)
    val colors = MaterialTheme.colorScheme
    val badge = animatedIconTint(colors.secondaryContainer, accent, colors.onSecondaryContainer)
    val actions: @Composable () -> Unit = {
        if (selectionMode) Checkbox(selected, onCheckedChange = { if (!interactionBlocked) onSelect() }, enabled = !interactionBlocked)
        else BookmarkActions(bookmark, refreshing, onOpen, onPin, onEdit, onRefresh, onUpdateShortcut, onDelete, onEditTags, !interactionBlocked)
    }
    val information: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(bookmark.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val domain = remember(bookmark.url, bookmark.localDisplayName) {
                if (bookmark.isLocal) bookmark.localDisplayName.orEmpty() else runCatching { java.net.URI(bookmark.url).host }.getOrNull()
                    ?: bookmark.url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringAfter('@')
            }
            Text(domain, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (bookmark.isLocal) Icons.Default.FolderOpen else Icons.Default.Language, null,
                    Modifier.size(14.dp), tint = colors.primary)
                Text(if (bookmark.isLocal) stringResource(R.string.source_offline_badge) else browserName ?: stringResource(R.string.browser_automatic),
                    style = MaterialTheme.typography.labelMedium, color = colors.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = badge, contentColor = colors.onSecondaryContainer) {
                    Text(modeLabel(bookmark.selectedIconSource), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (refreshing) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = colors.primary)
                        Text(stringResource(R.string.fetching_icon), style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant)
                    }
                } else if (refreshMessage != null) {
                    Text(refreshMessage, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                } else if (bookmark.selectedIconSource == IconSourceMode.AUTO && bookmark.lastFetchedAt != null) {
                    val description = stringResource(R.string.last_successful_fetch, formatIconTime(bookmark.lastFetchedAt))
                    Text(formatIconTime(bookmark.lastFetchedAt, compact = true), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, modifier = Modifier.semantics { contentDescription = description })
                }
            }
        }
    }
    InteractiveTonalCard(
        onClick = { if (selectionMode) onSelect() else onOpen() },
        modifier = Modifier.fillMaxWidth().testTag("bookmark_card_${bookmark.id}"),
        selected = selected, enabled = !interactionBlocked, compact = compact,
        onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.select_bookmark),
    ) {
        if (compact) {
            Column(Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BookmarkIcon(bookmark, bitmap, badge)
                    Spacer(Modifier.weight(1f))
                    actions()
                }
                information()
            }
        } else {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically) {
                BookmarkIcon(bookmark, bitmap, badge)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) { information() }
                actions()
            }
        }
        if (tags.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            BookmarkTagRow(tags, selectedTags, { if (selectionMode) onSelect() else onTagClick(it) }, !interactionBlocked, onMoreClick = onSelect.takeIf { selectionMode })
        }
    }
}

@Composable
private fun BookmarkActions(
    bookmark: BookmarkItem, refreshing: Boolean, onOpen: () -> Unit, onPin: () -> Unit,
    onEdit: () -> Unit, onRefresh: () -> Unit, onUpdateShortcut: () -> Unit, onDelete: () -> Unit,
    onEditTags: () -> Unit, enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.site_options, bookmark.title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BookmarkMenuItem(R.string.open_website, Icons.AutoMirrored.Filled.OpenInNew) { expanded = false; onOpen() }
            BookmarkMenuItem(R.string.pin_shortcut, Icons.Default.PushPin) { expanded = false; onPin() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            BookmarkMenuItem(R.string.edit_bookmark, Icons.Default.Edit) { expanded = false; onEdit() }
            BookmarkMenuItem(R.string.bookmark_tags, Icons.AutoMirrored.Filled.Label) { expanded = false; onEditTags() }
            if (!bookmark.isLocal && bookmark.selectedIconSource == IconSourceMode.AUTO) {
                BookmarkMenuItem(R.string.refresh_icon, Icons.Default.Refresh, enabled = !refreshing) { expanded = false; onRefresh() }
            }
            BookmarkMenuItem(R.string.update_shortcut, Icons.Default.Sync) { expanded = false; onUpdateShortcut() }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_bookmark), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() },
            )
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
private fun BookmarkIcon(bookmark: BookmarkItem, bitmap: Bitmap?, container: androidx.compose.ui.graphics.Color) {
    Surface(shape = MaterialTheme.shapes.medium, color = container, modifier = Modifier.size(48.dp)) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().adaptiveIconViewport().testTag("bookmark_icon_${bookmark.id}"))
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(bookmark.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
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
internal fun modeLabel(mode: IconSourceMode): String = stringResource(
    when (mode) {
        IconSourceMode.AUTO -> R.string.mode_label_auto
        IconSourceMode.CUSTOM -> R.string.mode_label_custom
        IconSourceMode.GENERATED -> R.string.mode_label_generated
    },
)

@Composable
internal fun ErrorNotice(message: String, title: String? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
