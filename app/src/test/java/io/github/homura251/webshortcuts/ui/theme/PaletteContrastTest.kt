package io.github.homura251.webshortcuts.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    @Test fun everyPaletteHasAccessibleTextRolesInBothModesAndOrderedSurfaceTones() {
        for (palette in ThemePalette.entries) for (dark in listOf(false, true)) {
            val scheme = palette.colorScheme(dark)
            with(scheme) {
                val textPairs = listOf(
                    onPrimary to primary, onPrimaryContainer to primaryContainer,
                    onSecondary to secondary, onSecondaryContainer to secondaryContainer,
                    onTertiary to tertiary, onTertiaryContainer to tertiaryContainer,
                    onError to error, onErrorContainer to errorContainer,
                    onBackground to background, inverseOnSurface to inverseSurface,
                    onSurfaceVariant to surfaceVariant,
                    onPrimaryFixed to primaryFixed, onPrimaryFixedVariant to primaryFixedDim,
                    onSecondaryFixed to secondaryFixed, onSecondaryFixedVariant to secondaryFixedDim,
                    onTertiaryFixed to tertiaryFixed, onTertiaryFixedVariant to tertiaryFixedDim,
                )
                textPairs.forEach { (foreground, background) ->
                    assertTrue("$palette / dark=$dark / $foreground on $background", contrastRatio(foreground, background) >= 4.5f)
                }
                val containers = listOf(surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
                    surfaceContainerHigh, surfaceContainerHighest)
                containers.forEach {
                    assertTrue(contrastRatio(onSurface, it) >= 4.5f)
                    assertTrue(contrastRatio(onSurfaceVariant, it) >= 4.5f)
                }
                containers.zipWithNext().forEach { (first, second) ->
                    assertTrue("$palette surface tone order", if (dark) first.luminance() < second.luminance()
                        else first.luminance() > second.luminance())
                }
            }
        }
    }

    @Test fun iconTintsPreserveBodyAndBadgeContrastEvenForExtremeArtwork() {
        val accents = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color.Yellow,
            Color.Cyan, Color.Magenta, Color(0xFF8899AA))
        for (palette in ThemePalette.entries) for (dark in listOf(false, true)) {
            val colors = palette.colorScheme(dark)
            for (accent in accents) {
                val card = tonalAccent(colors.surfaceContainerLow, accent, colors.onSurface, colors.onSurfaceVariant)
                val badge = tonalAccent(colors.secondaryContainer, accent, colors.onSecondaryContainer)
                assertTrue(contrastRatio(colors.onSurface, card) >= 4.5f)
                assertTrue(contrastRatio(colors.onSurfaceVariant, card) >= 4.5f)
                assertTrue(contrastRatio(colors.onSecondaryContainer, badge) >= 4.5f)
            }
            assertEquals(colors.surfaceContainerLow, tonalAccent(colors.surfaceContainerLow, null, colors.onSurface))
        }
    }
}
