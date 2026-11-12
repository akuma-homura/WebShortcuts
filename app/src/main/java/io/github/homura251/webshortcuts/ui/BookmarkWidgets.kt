package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkTag
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.IconPolicy
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.ui.components.ConnectedCustomItem
import io.github.homura251.webshortcuts.ui.components.ConnectedGroup
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveHeroEmblem
import io.github.homura251.webshortcuts.ui.components.ExpressiveIconButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveNoticeCard
import io.github.homura251.webshortcuts.ui.components.ExpressiveStatusPill
import io.github.homura251.webshortcuts.ui.components.ExpressiveTonalButton
import io.github.homura251.webshortcuts.ui.components.InteractiveTonalCard
import io.github.homura251.webshortcuts.ui.components.TonalEmphasis
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import io.github.homura251.webshortcuts.ui.theme.animatedIconTint
import io.github.homura251.webshortcuts.ui.theme.rememberIconAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptyBookmarks(onAdd: () -> Unit, onImportLocal: () -> Unit = {}) {
    val compact = LocalConfiguration.current.screenHeightDp < 640
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 4.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpressiveHeroEmblem(icon = Icons.Default.BookmarkBorder, compact = compact)
        Text(
            stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (compact) 10.dp else 16.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 390.dp).padding(top = 6.dp),
        )
        FlowRow(
            modifier = Modifier.padding(top = if (compact) 12.dp else 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExpressiveButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_first_site))
            }
            ExpressiveTonalButton(onClick = onImportLocal, modifier = Modifier.testTag("empty_import_local")) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_local_page))
            }
        }
        ExpressiveStatusPill(
            text = stringResource(R.string.local_only),
            icon = Icons.Default.Lock,
            emphasis = TonalEmphasis.SECONDARY,
            modifier = Modifier.padding(top = 12.dp),
        )
        ConnectedGroup(
            modifier = Modifier.padding(top = 20.dp).widthIn(max = 440.dp),
        ) {
            ConnectedCustomItem(index = 0, count = 3, title = stringResource(R.string.empty_step_one), icon = Icons.Default.Link)
            ConnectedCustomItem(index = 1, count = 3, title = stringResource(R.string.empty_step_two), icon = Icons.Default.Image)
            ConnectedCustomItem(index = 2, count = 3, title = stringResource(R.string.empty_step_three), icon = Icons.Default.PushPin)
        }
        Text(
            stringResource(R.string.empty_local_action_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 390.dp).padding(top = 12.dp),
        )
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
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = bookmark.copy(tagIds = emptySet(), updatedAt = 0),
        key2 = iconResolver,
    ) {
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
    val iconContainer = animatedIconTint(colors.secondaryContainer, accent, colors.onSecondaryContainer)
    val explicitBrowser = !bookmark.isLocal && bookmark.browserPackage != null && !browserName.isNullOrBlank()
    val hasContextualStatus = bookmark.isLocal || explicitBrowser || refreshing || refreshMessage != null

    val actions: @Composable () -> Unit = {
        if (selectionMode) {
            Checkbox(selected, onCheckedChange = { if (!interactionBlocked) onSelect() }, enabled = !interactionBlocked)
        } else {
            BookmarkActions(bookmark, refreshing, onOpen, onPin, onEdit, onRefresh, onUpdateShortcut, onDelete, onEditTags, !interactionBlocked)
        }
    }

    val information: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                bookmark.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val domain = remember(bookmark.url, bookmark.localDisplayName, bookmark.isLocal) {
                if (bookmark.isLocal) bookmark.localDisplayName.orEmpty()
                else runCatching { java.net.URI(bookmark.url).host }.getOrNull()
                    ?: bookmark.url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringAfter('@')
            }
            if (domain.isNotBlank()) {
                Text(
                    domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasContextualStatus) {
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (bookmark.isLocal) {
                        ExpressiveStatusPill(
                            text = stringResource(R.string.source_offline_badge),
                            icon = Icons.Default.FolderOpen,
                            emphasis = TonalEmphasis.TERTIARY,
                        )
                    } else if (explicitBrowser) {
                        ExpressiveStatusPill(
                            text = browserName.orEmpty(),
                            icon = Icons.Default.Language,
                            emphasis = TonalEmphasis.PRIMARY,
                        )
                    }
                    if (refreshing) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = colors.primary)
                            Text(
                                stringResource(R.string.fetching_icon),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    } else if (refreshMessage != null) {
                        Text(refreshMessage, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }

    InteractiveTonalCard(
        onClick = { if (selectionMode) onSelect() else onOpen() },
        modifier = Modifier.fillMaxWidth().testTag("bookmark_card_${bookmark.id}"),
        selected = selected,
        enabled = !interactionBlocked,
        compact = compact,
        accentTint = accent,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.select_bookmark),
    ) {
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 136.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BookmarkIcon(bookmark, bitmap, iconContainer, size = 48.dp)
                    Spacer(Modifier.weight(1f))
                    actions()
                }
                information()
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BookmarkIcon(bookmark, bitmap, iconContainer, size = 52.dp)
                Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) { information() }
                actions()
            }
        }
        if (tags.isNotEmpty()) {
            BookmarkTagRow(
                tags = tags,
                selected = selectedTags,
                onClick = { if (selectionMode) onSelect() else onTagClick(it) },
                enabled = !interactionBlocked,
                onMoreClick = onSelect.takeIf { selectionMode },
                onCardSurface = true,
            )
        }
    }
}

@Composable
private fun BookmarkActions(
    bookmark: BookmarkItem,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateShortcut: () -> Unit,
    onDelete: () -> Unit,
    onEditTags: () -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ExpressiveIconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.site_options, bookmark.title))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = AppTheme.surfaces.elevated,
        ) {
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
private fun BookmarkIcon(bookmark: BookmarkItem, bitmap: Bitmap?, container: Color, size: Dp = 52.dp) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.size(size),
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .adaptiveIconViewport()
                    .testTag("bookmark_icon_${bookmark.id}"),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    bookmark.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// Adaptive foreground layers are 108dp, while the launcher's visible viewport is 72dp.
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
    ExpressiveNoticeCard(message = message, title = title, emphasis = TonalEmphasis.ERROR)
}
