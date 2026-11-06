package io.github.homura251.webshortcuts.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.homura251.webshortcuts.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(@param:StringRes val label: Int) {
    SYSTEM(R.string.theme_system), LIGHT(R.string.theme_light), DARK(R.string.theme_dark);

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

enum class ThemePalette(@param:StringRes val label: Int) {
    EMERALD(R.string.palette_emerald), OCEAN(R.string.palette_ocean),
    AMBER(R.string.palette_amber), IRIS(R.string.palette_iris), ROSE(R.string.palette_rose),
}

enum class LayoutMode(@param:StringRes val label: Int) {
    LIST(R.string.layout_list), GRID(R.string.layout_grid),
}

@Immutable
data class AppearancePreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val palette: ThemePalette = ThemePalette.EMERALD,
    val layoutMode: LayoutMode = LayoutMode.LIST,
)

/** Appearance has its own local file and never participates in bookmark or image transactions. */
class ThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<AppearancePreferences> = mutableState.asStateFlow()

    fun update(value: AppearancePreferences) {
        edit(value).apply()
        mutableState.value = value
    }

    /** Used on an I/O dispatcher so a completed restore includes persisted preferences. */
    fun restore(value: AppearancePreferences): Boolean {
        if (!edit(value).commit()) return false
        mutableState.value = value
        return true
    }

    private fun edit(value: AppearancePreferences) = preferences.edit()
        .putString("theme_mode", value.themeMode.name).putBoolean("dynamic_color", value.dynamicColor)
        .putString("palette", value.palette.name).putString("layout_mode", value.layoutMode.name)

    private fun read() = AppearancePreferences(
        themeMode = enumPreference("theme_mode", ThemeMode.SYSTEM),
        dynamicColor = try { preferences.getBoolean("dynamic_color", true) } catch (_: ClassCastException) { true },
        palette = enumPreference("palette", ThemePalette.EMERALD),
        layoutMode = enumPreference("layout_mode", LayoutMode.LIST),
    )

    private inline fun <reified T : Enum<T>> enumPreference(key: String, fallback: T): T {
        val stored = try { preferences.getString(key, null) } catch (_: ClassCastException) { null }
        return enumValues<T>().firstOrNull { it.name == stored } ?: fallback
    }
}
