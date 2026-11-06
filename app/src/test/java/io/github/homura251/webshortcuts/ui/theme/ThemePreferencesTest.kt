package io.github.homura251.webshortcuts.ui.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clearAppearance() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun firstRunUsesSystemModeDynamicColorEmeraldAndList() {
        assertEquals(AppearancePreferences(), ThemePreferences(context).state.value)
    }

    @Test fun allOptionsSurviveRecreationWithoutResettingOtherChoices() {
        val preferences = ThemePreferences(context)
        for (palette in ThemePalette.entries) for (mode in ThemeMode.entries) {
            val selected = AppearancePreferences(mode, dynamicColor = false, palette, LayoutMode.GRID)
            preferences.update(selected)
            assertEquals(selected, preferences.state.value)
            assertEquals(selected, ThemePreferences(context).state.value)
        }
        preferences.update(preferences.state.value.copy(dynamicColor = true, layoutMode = LayoutMode.LIST))
        val restored = ThemePreferences(context).state.value
        assertTrue(restored.dynamicColor)
        assertEquals(ThemePalette.ROSE, restored.palette)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertEquals(LayoutMode.LIST, restored.layoutMode)
    }

    @Test fun unknownOrMalformedStoredValuesFallBackIndependently() {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("theme_mode", "FUTURE_MODE").putInt("dynamic_color", 2)
            .putString("palette", ThemePalette.OCEAN.name).putBoolean("layout_mode", true).commit()
        assertEquals(AppearancePreferences(palette = ThemePalette.OCEAN), ThemePreferences(context).state.value)
    }

    @Test fun explicitModesOverrideSystemWhileSystemTracksIt() {
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.LIGHT.isDark(true))
        assertTrue(ThemeMode.DARK.isDark(false))
    }
}
