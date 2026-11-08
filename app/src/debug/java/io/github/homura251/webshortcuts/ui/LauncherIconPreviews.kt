package io.github.homura251.webshortcuts.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import io.github.homura251.webshortcuts.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Debug-only launcher simulation: an adaptive layer is 108 units behind a 72-unit mask. */
internal object LauncherIconPreviewRenderer {
    enum class Mask(val label: String) { CIRCLE("圆形"), ROUNDED_SQUARE("圆角方形"), SQUIRCLE("超椭圆"), TEARDROP("水滴形") }

    fun render(context: Context, mask: Mask, size: Int, themed: Boolean = false, dark: Boolean = false): Bitmap {
        val icon = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(maskPath(mask, size.toFloat()))
        // Android reserves 18 units on each side for launcher cropping and motion.
        val outset = size / 4
        if (themed) canvas.drawColor(if (dark) 0xFF314C5F.toInt() else 0xFFD9EAF7.toInt())
        else icon.background.apply { setBounds(-outset, -outset, size + outset, size + outset); draw(canvas) }
        val foreground = if (themed) context.getDrawable(R.drawable.ic_launcher_monochrome)!!.mutate().apply {
            setTint(if (dark) 0xFFCBE8FF.toInt() else 0xFF204E6C.toInt())
        } else icon.foreground
        foreground.setBounds(-outset, -outset, size + outset, size + outset)
        foreground.draw(canvas)
        return bitmap
    }

    fun gallery(context: Context, dark: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, 1216, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (dark) 0xFF141A25.toInt() else 0xFFF7F9FD.toInt())
        val ink = if (dark) 0xFFE6ECF5.toInt() else 0xFF222D3D.toInt()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        fun label(value: String, x: Float, y: Float, size: Float) { text.textSize = size; canvas.drawText(value, x, y, text) }
        label("WebShortcuts", 48f, 88f, 52f)
        label("自适应应用图标", 48f, 144f, 32f)
        val masks = Mask.entries
        fun row(top: Float, size: Int, themed: Boolean) {
            masks.forEachIndexed { index, mask ->
                val center = 171f + index * 246f
                val icon = render(context, mask, size, themed, dark)
                canvas.drawBitmap(icon, center - size / 2f, top, null)
                text.textSize = 28f
                canvas.drawText(mask.label, center - text.measureText(mask.label) / 2f, top + size + 44f, text)
                icon.recycle()
            }
        }
        label("彩色图标", 48f, 210f, 32f)
        row(242f, 192, false)
        label("主题图标", 48f, 550f, 32f)
        row(582f, 192, true)
        label("小尺寸预览", 48f, 890f, 32f)
        row(922f, 128, false)
        label("完整主体 · 铺满背景 · 随桌面形状裁切", 48f, 1170f, 28f)
        return bitmap
    }

    private fun maskPath(mask: Mask, size: Float): Path = Path().apply {
        when (mask) {
            Mask.CIRCLE -> addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
            Mask.ROUNDED_SQUARE -> addRoundRect(RectF(0f, 0f, size, size), size * .22f, size * .22f, Path.Direction.CW)
            Mask.SQUIRCLE -> {
                for (step in 0..360) {
                    val angle = Math.toRadians(step.toDouble())
                    val cx = cos(angle); val cy = sin(angle)
                    val x = size * .5f * (1 + Math.signum(cx) * abs(cx).pow(.4)).toFloat()
                    val y = size * .5f * (1 + Math.signum(cy) * abs(cy).pow(.4)).toFloat()
                    if (step == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            Mask.TEARDROP -> {
                moveTo(size / 2, 0f)
                cubicTo(size * .776f, 0f, size, size * .224f, size, size / 2)
                lineTo(size, size * .88f); quadTo(size, size, size * .88f, size)
                lineTo(size / 2, size)
                cubicTo(size * .224f, size, 0f, size * .776f, 0f, size / 2)
                cubicTo(0f, size * .224f, size * .224f, 0f, size / 2, 0f)
                close()
            }
        }
    }
}

@Preview(name = "Adaptive launcher light", widthDp = 420, heightDp = 473, showBackground = true)
@Composable fun LauncherIconLightPreview() = LauncherIconGallery(false)

@Preview(name = "Adaptive launcher dark", widthDp = 420, heightDp = 473, showBackground = true)
@Composable fun LauncherIconDarkPreview() = LauncherIconGallery(true)

@Composable private fun LauncherIconGallery(dark: Boolean) {
    val context = LocalContext.current
    val bitmap = remember(context, dark) { LauncherIconPreviewRenderer.gallery(context, dark) }
    Image(bitmap.asImageBitmap(), "应用图标在四种桌面形状及主题下的预览", Modifier.fillMaxSize())
}
