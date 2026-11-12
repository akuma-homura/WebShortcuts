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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import io.github.homura251.webshortcuts.ui.theme.animatedIconTint

enum class TonalEmphasis { NEUTRAL, PRIMARY, SECONDARY, TERTIARY, ERROR }

/** TonalCard is our composition of the official Card, unified over AppTheme.surfaces. */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    emphasis: TonalEmphasis = TonalEmphasis.NEUTRAL,
    inset: Boolean = false,
    accentTint: Color? = null,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.content),
    verticalSpacing: Dp = AppSpacing.related,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val (baseContainer, foreground) = when {
        inset -> surfaces.inset to scheme.onSurface
        emphasis == TonalEmphasis.NEUTRAL -> surfaces.card to scheme.onSurface
        emphasis == TonalEmphasis.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        emphasis == TonalEmphasis.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        emphasis == TonalEmphasis.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.errorContainer to scheme.onErrorContainer
    }
    val container = if (accentTint != null) {
        animatedIconTint(baseContainer, accentTint, foreground, scheme.onSurfaceVariant)
    } else {
        baseContainer
    }
    val shape = if (inset) MaterialTheme.shapes.medium else MaterialTheme.shapes.large
    val border = when {
        inset -> BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.22f else 0.32f))
        emphasis == TonalEmphasis.NEUTRAL -> BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.26f else 0.38f))
        else -> null
    }
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = foreground),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
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
    accentTint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val corner by animateDpAsState(
        targetValue = if (pressed && enabled) 16.dp else if (selected) 30.dp else 26.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "card_shape",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.986f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "card_scale",
    )
    val restingContainer = if (accentTint != null) {
        animatedIconTint(surfaces.card, accentTint, scheme.onSurface, scheme.onSurfaceVariant)
    } else {
        surfaces.card
    }
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else restingContainer,
        animationSpec = motion.fastEffectsSpec(),
        label = "card_container",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        animationSpec = motion.fastEffectsSpec(),
        label = "card_content",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.28f else 0.40f),
        animationSpec = motion.fastEffectsSpec(),
        label = "card_border",
    )
    val shape = RoundedCornerShape(corner)
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .heightIn(min = AppSpacing.minimumTouchTarget)
            .clip(shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            )
            .semantics { this.selected = selected },
        shape = shape,
        color = container,
        contentColor = foreground,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) AppSpacing.compactCard else AppSpacing.content),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.compact),
            content = content,
        )
    }
}

/** The shapes overload enables Material's native pressed-state shape morph. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
        modifier = modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget),
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    content: @Composable RowScope.() -> Unit,
) {
    val colors = if (destructive) {
        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    } else {
        ButtonDefaults.textButtonColors()
    }
    TextButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget),
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = if (highlighted) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = AppTheme.surfaces.card,
            contentColor = scheme.onSurfaceVariant,
        )
    }
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveExtendedFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    MediumExtendedFloatingActionButton(
        text = { Text(text, style = MaterialTheme.typography.labelLarge) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.animateFloatingActionButton(visible = visible, alignment = Alignment.BottomEnd),
    )
}

@Immutable
data class ExpressiveChoiceOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val testTag: String? = null,
    val enabled: Boolean = true,
)

/**
 * Connected Expressive Pill Selector.
 * Morphs inner/outer corners via spatial spring when pressed or selected, preserves Role.RadioButton
 * `selected` semantics for UI tests, and automatically wraps into FlowRow at >= 150% font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ExpressiveConnectedChoices(
    options: List<ExpressiveChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    onCardSurface: Boolean = false,
) {
    if (LocalDensity.current.fontScale >= 1.5f) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.micro),
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                ExpressiveFilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option.value) },
                    enabled = enabled && option.enabled,
                    leadingIcon = when {
                        isSelected -> ({ Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) })
                        option.icon != null -> ({ Icon(option.icon, contentDescription = null, modifier = Modifier.size(16.dp)) })
                        else -> null
                    },
                    label = { Text(option.label, textAlign = TextAlign.Center) },
                    modifier = option.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        }
        return
    }

    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val restingBg = if (onCardSurface) surfaces.inset else surfaces.card

    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            val itemEnabled = enabled && option.enabled
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val isFirst = index == 0
            val isLast = index == options.lastIndex

            val targetStart = when {
                pressed && itemEnabled -> 10.dp
                isSelected -> 24.dp
                isFirst -> 24.dp
                else -> 8.dp
            }
            val targetEnd = when {
                pressed && itemEnabled -> 10.dp
                isSelected -> 24.dp
                isLast -> 24.dp
                else -> 8.dp
            }
            val startCorner by animateDpAsState(targetStart, motion.fastSpatialSpec(), label = "seg_start")
            val endCorner by animateDpAsState(targetEnd, motion.fastSpatialSpec(), label = "seg_end")
            val bg by animateColorAsState(
                targetValue = if (isSelected) scheme.secondaryContainer else restingBg,
                animationSpec = motion.fastEffectsSpec(),
                label = "seg_bg",
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                animationSpec = motion.fastEffectsSpec(),
                label = "seg_fg",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) scheme.primary.copy(alpha = 0.35f)
                else scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.28f else 0.42f),
                animationSpec = motion.fastEffectsSpec(),
                label = "seg_border",
            )
            val shape = RoundedCornerShape(
                topStart = startCorner,
                bottomStart = startCorner,
                topEnd = endCorner,
                bottomEnd = endCorner,
            )
            val itemModifier = Modifier
                .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                .heightIn(min = AppSpacing.minimumTouchTarget)
                .clip(shape)
                .selectable(
                    selected = isSelected,
                    enabled = itemEnabled,
                    role = Role.RadioButton,
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = { onSelect(option.value) },
                )
                .then(option.testTag?.let { Modifier.testTag(it) } ?: Modifier)

            Surface(
                modifier = itemModifier,
                shape = shape,
                color = bg.copy(alpha = if (itemEnabled) 1f else 0.55f),
                contentColor = fg.copy(alpha = if (itemEnabled) 1f else 0.38f),
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    } else if (option.icon != null) {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        text = option.label,
                        style = if (isSelected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Chips keep selection semantics while only shape changes use the spatial motion scheme. */
@Composable
fun ExpressiveFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val corner by animateDpAsState(
        targetValue = if (pressed && enabled) 12.dp else if (selected) 16.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "choice_shape",
    )
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        enabled = enabled,
        modifier = modifier.heightIn(min = AppSpacing.minimumTouchTarget),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(corner),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = surfaces.card,
            labelColor = scheme.onSurfaceVariant,
            iconColor = scheme.onSurfaceVariant,
            selectedContainerColor = scheme.secondaryContainer,
            selectedLabelColor = scheme.onSecondaryContainer,
            selectedLeadingIconColor = scheme.onSecondaryContainer,
            selectedTrailingIconColor = scheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.32f else 0.48f),
            selectedBorderColor = scheme.primary.copy(alpha = 0.32f),
        ),
        interactionSource = interactionSource,
    )
}

/** Compact tag content can keep its own removable action while sharing selection treatment. */
@Composable
fun ChoiceChipSurface(
    selected: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    onCardSurface: Boolean = false,
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val resting = if (onCardSurface) surfaces.inset else surfaces.card
    val corner by animateDpAsState(
        targetValue = if (pressed) 8.dp else if (selected) 12.dp else 20.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "tag_shape",
    )
    val color by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else resting,
        animationSpec = motion.fastEffectsSpec(),
        label = "tag_color",
    )
    val border by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.35f)
        else scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.28f else 0.42f),
        animationSpec = motion.fastEffectsSpec(),
        label = "tag_border",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = color,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        border = BorderStroke(1.dp, border),
        content = content,
    )
}

@Composable
fun ExpressiveStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasis: TonalEmphasis = TonalEmphasis.SECONDARY,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val (defaultBg, defaultFg) = when (emphasis) {
        TonalEmphasis.NEUTRAL -> surfaces.inset to scheme.onSurfaceVariant
        TonalEmphasis.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        TonalEmphasis.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TonalEmphasis.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TonalEmphasis.ERROR -> scheme.errorContainer to scheme.onErrorContainer
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor ?: defaultBg,
        contentColor = contentColor ?: defaultFg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ExpressiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    clearDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    val motion = MaterialTheme.motionScheme
    val borderColor by animateColorAsState(
        targetValue = if (focused) scheme.primary else scheme.outlineVariant.copy(alpha = if (surfaces.isDark) 0.34f else 0.48f),
        animationSpec = motion.fastEffectsSpec(),
        label = "search_border",
    )
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = surfaces.card,
        border = BorderStroke(if (focused) 1.5.dp else 1.dp, borderColor),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = if (focused) scheme.primary else scheme.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    ExpressiveIconButton(onClick = { onQueryChange("") }, enabled = enabled) {
                        Icon(Icons.Default.Close, contentDescription = clearDescription)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            shape = shape,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val scheme = MaterialTheme.colorScheme
    val surfaces = AppTheme.surfaces
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surfaces.card,
            unfocusedContainerColor = surfaces.inset,
            disabledContainerColor = surfaces.inset.copy(alpha = 0.6f),
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.65f),
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveHeroEmblem(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val outerSize = if (compact) 68.dp else 88.dp
    val innerSize = if (compact) 42.dp else 54.dp
    val iconSize = if (compact) 22.dp else 28.dp
    val scheme = MaterialTheme.colorScheme
    Box(modifier = modifier.size(outerSize), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(outerSize),
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = scheme.primaryContainer,
        ) {}
        Surface(
            modifier = Modifier.size(innerSize),
            shape = CircleShape,
            color = scheme.primary,
            contentColor = scheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
fun ExpressiveNoticeCard(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    emphasis: TonalEmphasis = TonalEmphasis.ERROR,
    icon: ImageVector = Icons.Default.Info,
) {
    TonalCard(
        modifier = modifier.fillMaxWidth(),
        emphasis = emphasis,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalSpacing = 4.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    supporting: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.compact),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    dismissEnabled: Boolean = true,
    dismissTag: String? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.compact)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.related),
        ) {
            icon?.let {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(it, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            ExpressiveTonalIconButton(
                onClick = onDismiss,
                enabled = dismissEnabled,
                modifier = dismissTag?.let { Modifier.testTag(it) } ?: Modifier,
            ) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
            }
        }
        supporting?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = AppTheme.surfaces.canvas,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.width(36.dp).height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                ) {}
            }
        },
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

/** For non-clickable collection rows with independent trailing actions (e.g. reorder handles). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun connectedItemShape(index: Int, count: Int): Shape = ListItemDefaults.segmentedShapes(index, count).shape

/** Keep connected-row boundaries crisp against AppTheme.surfaces.canvas in both Light and Dark modes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun connectedListItemColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = AppTheme.surfaces.card,
    disabledContainerColor = AppTheme.surfaces.card.copy(alpha = 0.65f),
)

@Composable
internal fun ExpressiveLeadingIcon(icon: ImageVector, highlighted: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (highlighted) scheme.primary else AppTheme.surfaces.inset
    val fg = if (highlighted) scheme.onPrimary else scheme.primary
    Surface(
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        contentColor = fg,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedActionItem(
    index: Int,
    count: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = connectedListItemColors(),
        leadingContent = icon?.let { { ExpressiveLeadingIcon(it) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailingContent,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedChoiceItem(
    index: Int,
    count: Int,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = connectedListItemColors(),
        leadingContent = icon?.let { { ExpressiveLeadingIcon(it, highlighted = selected) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
    ) {
        Text(title, style = if (selected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedSwitchItem(
    index: Int,
    count: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth().semantics { role = Role.Switch },
        enabled = enabled,
        colors = connectedListItemColors(),
        leadingContent = icon?.let { { ExpressiveLeadingIcon(it, highlighted = checked) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedCustomItem(
    index: Int,
    count: Int,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index, count),
        modifier = modifier.fillMaxWidth(),
        colors = connectedListItemColors(),
        leadingContent = icon?.let { { ExpressiveLeadingIcon(it) } },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailingContent,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoading(modifier: Modifier = Modifier, label: String? = null) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.related),
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(44.dp))
        label?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveProgressBar(
    progress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    if (progress != null) {
        LinearWavyProgressIndicator(progress = progress, modifier = modifier.fillMaxWidth())
    } else {
        LinearWavyProgressIndicator(modifier = modifier.fillMaxWidth())
    }
}

/** No idle animation. Uses Compose's duration-scale context, including system animations-off. */
@Composable
fun ExpressiveVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.94f),
        exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.96f),
        content = content,
    )
}

/** Modal semantics/focus and dismissal stay with the official dialog. Only entrance is emphasized. */
@Composable
fun ExpressiveAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.expressiveEntrance(),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = AppTheme.surfaces.elevated,
    )
}

/** Reusable entrance for custom modal content such as the crop editor. */
@Composable
fun Modifier.expressiveEntrance(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val motion = MaterialTheme.motionScheme
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.95f,
        animationSpec = motion.fastSpatialSpec(),
        label = "dialog_enter_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = motion.fastEffectsSpec(),
        label = "dialog_enter_alpha",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
