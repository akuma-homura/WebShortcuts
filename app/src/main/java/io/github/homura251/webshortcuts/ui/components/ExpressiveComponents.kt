package io.github.homura251.webshortcuts.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import io.github.homura251.webshortcuts.R

enum class TonalEmphasis { NEUTRAL, PRIMARY, SECONDARY, TERTIARY, ERROR }

/** TonalCard is our composition of the official Card, not a separate Material component. */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    emphasis: TonalEmphasis = TonalEmphasis.NEUTRAL,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.page),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, foreground) = when (emphasis) {
        TonalEmphasis.NEUTRAL -> scheme.surfaceContainerLow to scheme.onSurface
        TonalEmphasis.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        TonalEmphasis.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TonalEmphasis.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TonalEmphasis.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Card(modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground)) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(AppSpacing.related), content = content)
    }
}

/** One source of truth for collection-card press, selection, and long-click behavior. */
@Composable
fun InteractiveTonalCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        if (pressed && enabled) 16.dp else if (selected) 28.dp else 24.dp,
        motion.fastSpatialSpec(), label = "card_shape")
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        motion.fastEffectsSpec(), label = "card_container")
    val foreground by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        motion.fastEffectsSpec(), label = "card_content")
    val shape = RoundedCornerShape(corner)
    Surface(modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget).clip(shape).combinedClickable(
            interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button,
            onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClickLabel,
        ).semantics { this.selected = selected },
        shape = shape, color = container, contentColor = foreground) {
        Column(Modifier.padding(if (compact) AppSpacing.compact else AppSpacing.content), content = content)
    }
}

/** The shapes overload enables Material's native pressed-state shape morph. */
@Composable
fun ExpressiveButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight), enabled = enabled,
        contentPadding = contentPadding, content = content)
}

@Composable
fun ExpressiveTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    content: @Composable RowScope.() -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget), enabled = enabled,
        contentPadding = contentPadding, content = content)
}

@Composable
fun ExpressiveTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    content: @Composable RowScope.() -> Unit) {
    TextButton(onClick = onClick, shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget), enabled = enabled,
        contentPadding = contentPadding, content = content)
}

/** Chips keep selection semantics while only shape changes use the spatial motion scheme. */
@Composable
fun ExpressiveFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: (@Composable () -> Unit)? = null) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 12.dp else if (selected) 16.dp else 24.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "choice_shape")
    FilterChip(selected = selected, onClick = onClick, label = label, enabled = enabled,
        modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget), leadingIcon = leadingIcon,
        shape = RoundedCornerShape(corner), interactionSource = interactionSource)
}

/** Compact tag content can keep its own removable action while sharing selection treatment. */
@Composable
fun ChoiceChipSurface(selected: Boolean, interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(if (pressed) 8.dp else if (selected) 12.dp else 24.dp,
        motion.fastSpatialSpec(), label = "tag_shape")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        motion.fastEffectsSpec(), label = "tag_color")
    Surface(modifier, shape = RoundedCornerShape(corner), color = color,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        content = content)
}

@Composable
fun SectionHeader(title: String, supporting: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SheetHeader(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier,
    supporting: String? = null, icon: ImageVector? = null, dismissEnabled: Boolean = true,
    dismissTag: String? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.related)) {
            icon?.let {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Icon(it, contentDescription = null, modifier = Modifier.padding(12.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() })
            IconButton(onClick = onDismiss, enabled = dismissEnabled,
                modifier = dismissTag?.let { Modifier.testTag(it) } ?: Modifier) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }
        supporting?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ConnectedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap), content = content)
}

/** For non-clickable collection rows with independent trailing actions (e.g. reorder handles). */
@Composable
fun connectedItemShape(index: Int, count: Int): Shape = ListItemDefaults.segmentedShapes(index, count).shape

@Composable
fun ConnectedActionItem(index: Int, count: Int, title: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, supporting: String? = null, icon: ImageVector? = null,
    enabled: Boolean = true, trailingContent: (@Composable () -> Unit)? = null) {
    SegmentedListItem(onClick = onClick, shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth(), enabled = enabled,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = supporting?.let { { Text(it) } }, trailingContent = trailingContent) { Text(title) }
}

@Composable
fun ConnectedChoiceItem(index: Int, count: Int, title: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, supporting: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    SegmentedListItem(selected = selected, onClick = onClick, shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth(), enabled = enabled,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) }) { Text(title) }
}

@Composable
fun ConnectedSwitchItem(index: Int, count: Int, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, supporting: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    SegmentedListItem(checked = checked, onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth().semantics { role = Role.Switch }, enabled = enabled,
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }) { Text(title) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoading(modifier: Modifier = Modifier, label: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.related)) {
        LoadingIndicator(Modifier.size(40.dp))
        label?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** No idle animation. Uses Compose's duration-scale context, including system animations-off. */
@Composable
fun ExpressiveVisibility(visible: Boolean, modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(visible = visible, modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = .94f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = .96f),
        content = content)
}

/** Modal semantics/focus and dismissal stay with the official dialog. Only entrance is emphasized. */
@Composable
fun ExpressiveAlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
        modifier = modifier.expressiveEntrance(), dismissButton = dismissButton, icon = icon, title = title, text = text,
        shape = MaterialTheme.shapes.extraLarge, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

/** Reusable entrance for custom modal content such as the crop editor. */
@Composable
fun Modifier.expressiveEntrance(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(if (entered) 1f else .96f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "dialog_enter")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
