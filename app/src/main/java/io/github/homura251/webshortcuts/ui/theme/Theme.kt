package io.github.homura251.webshortcuts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified tonal surface hierarchy for Material 3 Expressive.
 * Light mode uses a soft tinted architectural canvas (`surfaceContainer`) beneath pure white cards (`surfaceContainerLowest`),
 * while Dark mode uses deep obsidian ink (`surface`) beneath velvet elevated containers (`surfaceContainerHigh`).
 */
@Immutable
data class AppSurfaces(
    val canvas: Color,
    val card: Color,
    val inset: Color,
    val elevated: Color,
    val scrolledBar: Color,
    val isDark: Boolean,
)

internal fun resolveAppSurfaces(colors: ColorScheme, darkTheme: Boolean): AppSurfaces =
    if (darkTheme) {
        AppSurfaces(
            canvas = colors.surface,
            card = colors.surfaceContainerHigh,
            inset = colors.surfaceContainerLow,
            elevated = colors.surfaceContainerHighest,
            scrolledBar = colors.surfaceContainer,
            isDark = true,
        )
    } else {
        AppSurfaces(
            canvas = colors.surfaceContainer,
            card = colors.surfaceContainerLowest,
            inset = colors.surfaceContainerLow,
            elevated = colors.surfaceContainerLowest,
            scrolledBar = colors.surfaceContainerHigh,
            isDark = false,
        )
    }

private val LocalAppSurfaces = staticCompositionLocalOf<AppSurfaces?> { null }

object AppTheme {
    val surfaces: AppSurfaces
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSurfaces.current ?: run {
            val scheme = MaterialTheme.colorScheme
            resolveAppSurfaces(scheme, scheme.surface.luminance() < 0.5f)
        }
}

// Retain the Material scale and system font fallback (including CJK); emphasize hierarchy
// through weight and display tracking while keeping sp scaling intact up to 200%.
private val BaselineTypography = Typography()
private val AppTypography = Typography(
    displayLarge = BaselineTypography.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = BaselineTypography.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    displaySmall = BaselineTypography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineLarge = BaselineTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineMedium = BaselineTypography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    headlineSmall = BaselineTypography.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp),
    titleLarge = BaselineTypography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.15).sp),
    titleMedium = BaselineTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaselineTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaselineTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaselineTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Shared layout tokens; business screens compose components instead of choosing styling. */
object AppSpacing {
    val page = 20.dp
    val section = 20.dp
    val content = 16.dp
    val compactCard = 12.dp
    val related = 12.dp
    val compact = 8.dp
    val micro = 4.dp
    val minimumTouchTarget = 48.dp
    val contentMaxWidth = 760.dp
    val editorMaxWidth = 640.dp
}

@Composable
fun WebShortcutsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    palette: ThemePalette = ThemePalette.EMERALD,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> palette.colorScheme(darkTheme)
    }
    val surfaces = remember(colors, darkTheme) { resolveAppSurfaces(colors, darkTheme) }
    CompositionLocalProvider(LocalAppSurfaces provides surfaces) {
        MaterialExpressiveTheme(
            colorScheme = colors,
            motionScheme = remember { MotionScheme.expressive() },
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
