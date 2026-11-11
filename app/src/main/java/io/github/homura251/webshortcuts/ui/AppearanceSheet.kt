package io.github.homura251.webshortcuts.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.ui.components.ConnectedChoiceItem
import io.github.homura251.webshortcuts.ui.components.ConnectedGroup
import io.github.homura251.webshortcuts.ui.components.ConnectedSwitchItem
import io.github.homura251.webshortcuts.ui.components.ExpressiveVisibility
import io.github.homura251.webshortcuts.ui.components.SectionHeader
import io.github.homura251.webshortcuts.ui.components.SheetHeader
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.theme.AppSpacing
import io.github.homura251.webshortcuts.ui.theme.AppearancePreferences
import io.github.homura251.webshortcuts.ui.theme.LayoutMode
import io.github.homura251.webshortcuts.ui.theme.ThemeMode
import io.github.homura251.webshortcuts.ui.theme.ThemePalette
import io.github.homura251.webshortcuts.ui.theme.colorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSheet(preferences: AppearancePreferences, onChange: (AppearancePreferences) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        AppearanceContent(preferences, onChange, onDismiss)
    }
}

@Composable
internal fun AppearanceContent(preferences: AppearancePreferences, onChange: (AppearancePreferences) -> Unit, onDismiss: () -> Unit) {
    val dark = preferences.themeMode.isDark(isSystemInDarkTheme())
    val dynamic = preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Column(Modifier.fillMaxWidth().testTag("appearance_sheet").verticalScroll(rememberScrollState())
        .navigationBarsPadding().padding(start = AppSpacing.page, end = AppSpacing.page, bottom = AppSpacing.section),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.section)) {
        SheetHeader(stringResource(R.string.appearance_title), onDismiss,
            supporting = stringResource(R.string.appearance_subtitle), icon = Icons.Default.Palette, dismissTag = "close_appearance")
        TonalCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(AppSpacing.content)) {
            SectionHeader(stringResource(R.string.palette_title))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemePalette.entries.forEach { palette ->
                    val colors = palette.colorScheme(dark)
                    val selected = !dynamic && preferences.palette == palette
                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).selectable(
                        selected = selected, role = Role.RadioButton,
                        onClick = { onChange(preferences.copy(palette = palette, dynamicColor = false)) },
                    ).testTag("palette_${palette.name.lowercase()}").heightIn(min = AppSpacing.minimumTouchTarget).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(44.dp)) {
                                drawArc(colors.primary, -90f, 180f, useCenter = true)
                                drawArc(colors.secondaryContainer, 90f, 90f, useCenter = true)
                                drawArc(colors.tertiaryContainer, 180f, 90f, useCenter = true)
                            }
                            ExpressiveVisibility(selected) {
                                Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
                                    Icon(Icons.Default.Check, null, Modifier.padding(4.dp).size(20.dp))
                                }
                            }
                        }
                        Text(stringResource(palette.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
            if (dynamic) Text(stringResource(R.string.palette_dynamic_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ConnectedSwitchItem(index = 0, count = 1, title = stringResource(R.string.dynamic_color_title),
                supporting = stringResource(R.string.dynamic_color_description), checked = preferences.dynamicColor,
                onCheckedChange = { onChange(preferences.copy(dynamicColor = it)) }, modifier = Modifier.testTag("dynamic_color"))
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.related)) {
            SectionHeader(stringResource(R.string.theme_mode_title))
            ConnectedGroup(Modifier.selectableGroup()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    ConnectedChoiceItem(index, ThemeMode.entries.size, stringResource(mode.label), preferences.themeMode == mode,
                        onClick = { onChange(preferences.copy(themeMode = mode)) }, modifier = Modifier.testTag("theme_${mode.name.lowercase()}"),
                        icon = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.related)) {
            SectionHeader(stringResource(R.string.layout_title))
            ConnectedGroup(Modifier.selectableGroup()) {
                LayoutMode.entries.forEachIndexed { index, mode ->
                    ConnectedChoiceItem(index, LayoutMode.entries.size, stringResource(mode.label), preferences.layoutMode == mode,
                        onClick = { onChange(preferences.copy(layoutMode = mode)) }, modifier = Modifier.testTag("layout_${mode.name.lowercase()}"),
                        icon = if (mode == LayoutMode.LIST) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView)
                }
            }
        }
    }
}
