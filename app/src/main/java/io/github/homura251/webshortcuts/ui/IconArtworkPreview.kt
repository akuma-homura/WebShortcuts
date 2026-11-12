package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.icons.IconPolicy
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme

/** The same adaptive layer is shown with a launcher mask or as the complete 108dp canvas. */
@Composable
internal fun IconArtworkPreview(
    bitmap: Bitmap?,
    round: Boolean,
    fullCanvas: Boolean,
    showSafeZone: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = when {
        fullCanvas -> RoundedCornerShape(10.dp)
        round -> CircleShape
        else -> RoundedCornerShape(percent = 26)
    }
    val safeColor = MaterialTheme.colorScheme.tertiary
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    Surface(
        modifier = modifier
            .border(1.dp, borderColor, shape)
            .testTag(if (fullCanvas) "full_canvas_preview" else "launcher_preview"),
        shape = shape,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.icon_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val scale = if (fullCanvas) 1f else 1f / IconPolicy.ADAPTIVE_VIEWPORT_RATIO
                        scaleX = scale
                        scaleY = scale
                    },
                )
            }
            if (showSafeZone) Canvas(Modifier.fillMaxSize().testTag("safe_zone_overlay")) {
                val diameter = size.minDimension * IconPolicy.SAFE_ZONE_RATIO /
                    if (fullCanvas) 1f else IconPolicy.ADAPTIVE_VIEWPORT_RATIO
                val center = Offset(size.width / 2f, size.height / 2f)
                val outside = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                    addOval(androidx.compose.ui.geometry.Rect(center - Offset(diameter / 2, diameter / 2), Size(diameter, diameter)))
                }
                drawPath(outside, Color.Black.copy(alpha = 0.24f))
                drawCircle(safeColor, diameter / 2, center, style = Stroke(2.dp.toPx()))
                if (fullCanvas) {
                    val viewport = size.minDimension * IconPolicy.ADAPTIVE_VIEWPORT_RATIO
                    val topLeft = center - Offset(viewport / 2f, viewport / 2f)
                    val stroke = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                    if (round) drawCircle(Color.White, viewport / 2, center, style = stroke)
                    else drawRoundRect(Color.White, topLeft, Size(viewport, viewport), CornerRadius(viewport / 4), style = stroke)
                }
            }
        }
    }
}

@Preview(name = "Full Canvas and safe zone", widthDp = 240, heightDp = 280)
@Composable
internal fun FullCanvasArtworkPreview() {
    ArtworkSample(fullCanvas = true)
}

@Preview(name = "Launcher mask and safe zone", widthDp = 240, heightDp = 280)
@Composable
internal fun LauncherArtworkPreview() {
    ArtworkSample(fullCanvas = false)
}

@Composable
private fun ArtworkSample(fullCanvas: Boolean) {
    val bitmap = remember {
        Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
            android.graphics.Canvas(this).drawCircle(256f, 256f, 145f, android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(52, 83, 173)
                isAntiAlias = true
            })
        }
    }
    WebShortcutsTheme {
        Surface(color = AppTheme.surfaces.canvas) {
            Column(Modifier.padding(24.dp)) {
                IconArtworkPreview(bitmap, round = true, fullCanvas = fullCanvas, showSafeZone = true, modifier = Modifier.size(192.dp))
                Text(if (fullCanvas) "Full Canvas" else "桌面遮罩", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
