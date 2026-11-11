package io.github.homura251.webshortcuts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Retain the Material scale and system font fallback (including CJK); emphasize hierarchy,
// rather than changing density or preventing the user's font scaling.
private val BaselineTypography = Typography()
private val AppTypography = Typography(
    displayLarge = BaselineTypography.displayLarge.copy(fontWeight = FontWeight.Bold),
    displayMedium = BaselineTypography.displayMedium.copy(fontWeight = FontWeight.Bold),
    displaySmall = BaselineTypography.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = BaselineTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = BaselineTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = BaselineTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaselineTypography.titleLarge.copy(fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Shared layout tokens; business screens compose components instead of choosing styling. */
object AppSpacing {
    val page = 20.dp
    val section = 24.dp
    val content = 16.dp
    val related = 12.dp
    val compact = 8.dp
    val minimumTouchTarget = 48.dp
    val contentMaxWidth = 760.dp
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
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = remember { MotionScheme.expressive() },
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
