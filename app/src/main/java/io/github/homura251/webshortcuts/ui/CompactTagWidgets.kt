package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.ui.components.ChoiceChipSurface
import io.github.homura251.webshortcuts.ui.components.ExpressiveIconButton
import io.github.homura251.webshortcuts.ui.theme.AppTheme

/** The visible chip is compact; the entire 48dp cell remains an independent touch target. */
@Composable
internal fun CompactTagChoice(
    name: String,
    selected: Boolean,
    count: Int?,
    key: String,
    enabled: Boolean = true,
    fill: Boolean = true,
    onCardSurface: Boolean = false,
    onRemove: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .toggleable(
                value = selected,
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            )
            .testTag(key),
        contentAlignment = Alignment.CenterStart,
    ) {
        ChoiceChipSurface(selected, interaction, onCardSurface = onCardSurface) {
            Row(
                Modifier
                    .then(if (fill) Modifier.fillMaxWidth() else Modifier)
                    .heightIn(min = 30.dp)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (selected && fill && onRemove == null) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                Text(
                    name,
                    if (fill) Modifier.weight(1f) else Modifier,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                if (onRemove != null) Spacer(Modifier.width(40.dp))
            }
        }
        if (onRemove != null) {
            ExpressiveIconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterEnd).size(48.dp).testTag("${key}_remove"),
            ) {
                Icon(Icons.Default.Close, stringResource(R.string.remove_selected_tag, name), Modifier.size(16.dp))
            }
        }
    }
}

/** Enter searches; only the explicit add action submits a name. */
@Composable
internal fun TagSearchInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    error: Boolean = false,
    onAdd: ((String) -> Unit)? = null,
    addKey: String = "add_tag",
    onSearch: () -> Unit = {},
    onComposingChange: (Boolean) -> Unit = {},
) {
    var editing by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var composingEnter by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val latestChange by rememberUpdatedState(onChange)
    val latestAdd by rememberUpdatedState(onAdd)
    val latestSearch by rememberUpdatedState(onSearch)
    val latestComposition by rememberUpdatedState(onComposingChange)

    LaunchedEffect(value) {
        if (editing.text != value) {
            editing = TextFieldValue(value, TextRange(value.length))
            latestComposition(false)
        }
    }
    LaunchedEffect(busy, restoreFocus, value) {
        if (!busy && restoreFocus) {
            focus.requestFocus()
            keyboard?.show()
            restoreFocus = false
        }
    }
    fun search() {
        if (!busy && !composingEnter && editing.composition == null) {
            latestSearch()
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    fun add() {
        if (!busy && !composingEnter && editing.composition == null && editing.text.isNotBlank()) {
            restoreFocus = true
            latestAdd?.invoke(editing.text)
        }
    }

    val surfaces = AppTheme.surfaces
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = editing,
        onValueChange = { next ->
            if (!busy) {
                editing = next
                latestComposition(next.composition != null)
                latestChange(next.text)
            }
        },
        modifier = modifier
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (busy || event.key != Key.Enter && event.key != Key.NumPadEnter) false
                else if (event.type == KeyEventType.KeyDown) {
                    composingEnter = editing.composition != null
                    !composingEnter
                } else if (event.type == KeyEventType.KeyUp) {
                    if (composingEnter) {
                        composingEnter = false
                        false
                    } else {
                        search()
                        true
                    }
                } else false
            },
        label = { Text(stringResource(if (onAdd == null) R.string.search_tags else R.string.search_or_add_tags)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = if (onAdd == null) null else {
            {
                ExpressiveIconButton(
                    onClick = { add() },
                    enabled = !busy && editing.composition == null && editing.text.isNotBlank(),
                    modifier = Modifier.size(48.dp).testTag(addKey),
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.add_entered_tag))
                }
            }
        },
        enabled = !busy,
        isError = error,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = surfaces.card,
            unfocusedContainerColor = surfaces.card,
            disabledContainerColor = surfaces.card.copy(alpha = 0.6f),
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant.copy(alpha = 0.6f),
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { search() }),
    )
}
