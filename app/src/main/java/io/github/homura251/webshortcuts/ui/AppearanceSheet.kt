package io.github.homura251.webshortcuts.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.LayoutMode
import io.github.homura251.webshortcuts.ui.theme.ThemeMode
import io.github.homura251.webshortcuts.ui.theme.ThemePalette
import io.github.homura251.webshortcuts.ui.theme.colorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(
    preferences: AppearancePreferences,
    onChange: (AppearancePreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = preferences.themeMode.isDark(isSystemInDarkTheme())
    val dynamic = preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("appearance_sheet").verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.Palette, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_appearance")) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                Text(stringResource(R.string.appearance_subtitle), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).toggleable(
                        value = preferences.dynamicColor, role = Role.Switch,
                        onValueChange = { onChange(preferences.copy(dynamicColor = it)) },
                    ).testTag("dynamic_color").padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.dynamic_color_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.dynamic_color_description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = preferences.dynamicColor, onCheckedChange = null)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceHeading(stringResource(R.string.palette_title))
                Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ThemePalette.entries.forEach { palette ->
                        val colors = palette.colorScheme(dark)
                        val selected = !dynamic && preferences.palette == palette
                        Column(
                            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).selectable(
                                selected = selected, role = Role.RadioButton,
                                onClick = { onChange(preferences.copy(palette = palette, dynamicColor = false)) },
                            ).testTag("palette_${palette.name.lowercase()}").padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(44.dp)) {
                                    drawArc(colors.primary, -90f, 180f, useCenter = true)
                                    drawArc(colors.secondaryContainer, 90f, 90f, useCenter = true)
                                    drawArc(colors.tertiaryContainer, 180f, 90f, useCenter = true)
                                }
                                androidx.compose.animation.AnimatedVisibility(selected, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                                    Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                                        Icon(Icons.Default.Check, null, Modifier.padding(4.dp).size(20.dp))
                                    }
                                }
                            }
                            Text(stringResource(palette.label), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (dynamic) Text(stringResource(R.string.palette_dynamic_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceHeading(stringResource(R.string.theme_mode_title))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onChange(preferences.copy(themeMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            modifier = Modifier.testTag("theme_${mode.name.lowercase()}"),
                        ) { Text(stringResource(mode.label), style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceHeading(stringResource(R.string.layout_title))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    LayoutMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.layoutMode == mode,
                            onClick = { onChange(preferences.copy(layoutMode = mode)) },
                            shape = SegmentedButtonDefaults.itemShape(index, LayoutMode.entries.size),
                            modifier = Modifier.testTag("layout_${mode.name.lowercase()}"),
                            icon = {
                                Icon(if (mode == LayoutMode.LIST) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                        ) { Text(stringResource(mode.label), style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}
