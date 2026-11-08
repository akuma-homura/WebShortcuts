package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
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

/** The visible chip is compact; the entire 48dp cell remains an independent touch target. */
@Composable
internal fun CompactTagChoice(name: String, selected: Boolean, count: Int?, key: String,
    enabled: Boolean = true, fill: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).heightIn(min = 48.dp).widthIn(min = 48.dp)
        .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() }).testTag(key),
        contentAlignment = Alignment.CenterStart) {
        Surface(shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).heightIn(min = 28.dp)
                .padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selected && fill) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                Text(name, if (fill) Modifier.weight(1f) else Modifier, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Search never feeds this field. Only an explicit submit or the enclosing Save uses its text. */
@Composable
internal fun TagNameInput(value: String, onChange: (String) -> Unit, onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier, busy: Boolean = false, error: Boolean = false,
    onComposingChange: (Boolean) -> Unit = {}) {
    var editing by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var composingEnter by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val latestChange by rememberUpdatedState(onChange)
    val latestSubmit by rememberUpdatedState(onSubmit)
    val latestComposition by rememberUpdatedState(onComposingChange)
    LaunchedEffect(value) {
        if (editing.text != value) {
            editing = TextFieldValue(value, TextRange(value.length))
            latestComposition(false)
        }
    }
    LaunchedEffect(busy, restoreFocus, value) {
        if (!busy && restoreFocus) { focus.requestFocus(); keyboard?.show(); restoreFocus = false }
    }
    fun submit() {
        if (!busy && !composingEnter && editing.composition == null && editing.text.isNotBlank()) {
            restoreFocus = true
            latestSubmit(editing.text)
        }
    }
    OutlinedTextField(editing, { next ->
        if (!busy) { editing = next; latestComposition(next.composition != null); latestChange(next.text) }
    }, modifier = modifier.focusRequester(focus).onPreviewKeyEvent { event ->
        if (busy || event.key != Key.Enter && event.key != Key.NumPadEnter) false
        else if (event.type == KeyEventType.KeyDown) {
            composingEnter = editing.composition != null
            !composingEnter
        } else if (event.type == KeyEventType.KeyUp) {
            if (composingEnter) { composingEnter = false; false } else { submit(); true }
        } else false
    }, label = { Text(stringResource(R.string.tag_new_name)) },
        placeholder = { Text(stringResource(R.string.tag_new_placeholder), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = !busy, isError = error, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
}
