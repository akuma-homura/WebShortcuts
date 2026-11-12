package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.homura251.webshortcuts.R
import io.github.homura251.webshortcuts.icons.CropRect
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton
import io.github.homura251.webshortcuts.ui.components.ExpressiveChoiceOption
import io.github.homura251.webshortcuts.ui.components.ExpressiveConnectedChoices
import io.github.homura251.webshortcuts.ui.components.ExpressiveStatusPill
import io.github.homura251.webshortcuts.ui.components.ExpressiveTextButton
import io.github.homura251.webshortcuts.ui.components.SectionHeader
import io.github.homura251.webshortcuts.ui.components.TonalCard
import io.github.homura251.webshortcuts.ui.components.TonalEmphasis
import io.github.homura251.webshortcuts.ui.components.expressiveEntrance
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import kotlin.math.min
import kotlin.math.roundToInt

private val CropRectSaver = listSaver<CropRect, Float>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropRect(it[0], it[1], it[2], it[3]) },
)

/** Edits source-relative coordinates only; the caller owns the bitmap and applies the crop. */
@Composable
fun FreeCropDialog(source: Bitmap, initialCrop: CropRect?, onDismiss: () -> Unit, onApply: (CropRect) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FreeCropContent(source, initialCrop, onDismiss, onApply)
    }
}

/** Kept separate from the Android dialog window so layout previews render the actual editor. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun FreeCropContent(source: Bitmap, initialCrop: CropRect?, onDismiss: () -> Unit, onApply: (CropRect) -> Unit) {
    require(!source.isRecycled)
    val minimum = remember(source.width, source.height) { CropRect.minimumSize(source.width, source.height) }
    var crop by rememberSaveable(source, stateSaver = CropRectSaver) {
        mutableStateOf((initialCrop ?: CropRect.FULL).clamped(minimum.width, minimum.height))
    }
    var square by rememberSaveable(source) { mutableStateOf(false) }
    val aspect = if (square) source.height.toFloat() / source.width else null
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val previewHeight = (screenHeight * 0.28f).coerceIn(136f, 224f).dp
    val dimensions = stringResource(
        R.string.free_crop_dimensions,
        (crop.width * source.width).roundToInt().coerceAtLeast(1),
        (crop.height * source.height).roundToInt().coerceAtLeast(1),
    )

    fun resizeCentered(width: Float, height: Float) {
        crop = CropRect.clamp(
            crop.centerX - width / 2f,
            crop.centerY - height / 2f,
            crop.centerX + width / 2f,
            crop.centerY + height / 2f,
            minimum.width,
            minimum.height,
        )
    }

    val surfaces = AppTheme.surfaces
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = surfaces.canvas,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(12.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .heightIn(max = (screenHeight - 48).coerceAtLeast(200).dp)
            .expressiveEntrance(),
    ) {
        Column {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.free_crop_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    ExpressiveStatusPill(text = dimensions, emphasis = TonalEmphasis.PRIMARY)
                }
                Text(
                    stringResource(R.string.free_crop_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveConnectedChoices(
                        options = listOf(
                            ExpressiveChoiceOption(false, stringResource(R.string.free_crop_free_ratio), testTag = "crop_ratio_free"),
                            ExpressiveChoiceOption(true, stringResource(R.string.free_crop_square_ratio), testTag = "crop_ratio_square"),
                        ),
                        selected = square,
                        onSelect = { nextSquare ->
                            square = nextSquare
                            if (nextSquare) {
                                crop = crop.withAspectRatio(source.height.toFloat() / source.width, minimum.width, minimum.height)
                            }
                        },
                    )
                    ExpressiveTextButton(
                        onClick = {
                            crop = if (square) {
                                CropRect.FULL.withAspectRatio(source.height.toFloat() / source.width, minimum.width, minimum.height)
                            } else {
                                CropRect.FULL
                            }
                        },
                        modifier = Modifier.testTag("reset_crop"),
                    ) {
                        Text(stringResource(R.string.free_crop_reset))
                    }
                }
                CropSelection(
                    source, crop, { crop = it }, minimum.width, minimum.height, aspect,
                    dimensions, Modifier.fillMaxWidth().height(previewHeight),
                )
                SectionHeader(stringResource(R.string.free_crop_precise))

                val minWidth = if (aspect == null) minimum.width else maxOf(minimum.width, minimum.height * aspect)
                val maxWidth = if (aspect == null) 1f else min(1f, aspect)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CropSlider(
                        stringResource(R.string.free_crop_width),
                        fraction(crop.width, minWidth, maxWidth),
                        modifier = Modifier.weight(1f),
                        enabled = maxWidth - minWidth > 0.000001f,
                        tag = "crop_width",
                        onValueChange = { value ->
                            val width = minWidth + value * (maxWidth - minWidth)
                            resizeCentered(width, if (aspect == null) crop.height else width / aspect)
                        },
                    )
                    if (!square) {
                        CropSlider(
                            stringResource(R.string.free_crop_height),
                            fraction(crop.height, minimum.height, 1f),
                            modifier = Modifier.weight(1f),
                            enabled = minimum.height < 0.999999f,
                            tag = "crop_height",
                            onValueChange = { value ->
                                resizeCentered(crop.width, minimum.height + value * (1f - minimum.height))
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CropSlider(
                        stringResource(R.string.free_crop_horizontal),
                        fraction(crop.left, 0f, 1f - crop.width),
                        enabled = crop.width < 0.999999f,
                        tag = "crop_horizontal",
                        modifier = Modifier.weight(1f),
                        onValueChange = { value ->
                            crop = crop.moved(value * (1f - crop.width) - crop.left, 0f)
                        },
                    )
                    CropSlider(
                        stringResource(R.string.free_crop_vertical),
                        fraction(crop.top, 0f, 1f - crop.height),
                        enabled = crop.height < 0.999999f,
                        tag = "crop_vertical",
                        modifier = Modifier.weight(1f),
                        onValueChange = { value ->
                            crop = crop.moved(0f, value * (1f - crop.height) - crop.top)
                        },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Surface(
                color = surfaces.card,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExpressiveTextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_crop")) {
                        Text(stringResource(R.string.free_crop_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    ExpressiveButton(
                        onClick = { onApply(crop.clamped(minimum.width, minimum.height)) },
                        modifier = Modifier.testTag("apply_crop"),
                    ) {
                        Text(stringResource(R.string.free_crop_apply))
                    }
                }
            }
        }
    }
}

@Preview(name = "Free crop editor", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
internal fun FreeCropEditorPreview() {
    val bitmap = remember {
        Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(220, 235, 247))
            val canvas = android.graphics.Canvas(this)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.rgb(33, 105, 145)
            canvas.drawCircle(205f, 200f, 145f, paint)
            paint.color = android.graphics.Color.rgb(237, 168, 89)
            canvas.drawRect(325f, 55f, 550f, 345f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 74f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("ICON", 92f, 225f, paint)
        }
    }
    WebShortcutsTheme(dynamicColor = false) {
        FreeCropContent(bitmap, CropRect(0.12f, 0.15f, 0.84f, 0.85f), {}, {})
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CropSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onValueChange: (Float) -> Unit,
) {
    TonalCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalSpacing = 2.dp,
    ) {
        val percentage = stringResource(R.string.free_crop_percent, (value * 100).roundToInt())
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            ExpressiveStatusPill(text = percentage, emphasis = TonalEmphasis.SECONDARY)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics {
                    contentDescription = label
                    stateDescription = percentage
                },
        )
    }
}

@Composable
private fun CropSelection(
    source: Bitmap,
    crop: CropRect,
    onCropChange: (CropRect) -> Unit,
    minWidth: Float,
    minHeight: Float,
    aspect: Float?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val image = remember(source) { source.asImageBitmap() }
    val latestCrop by rememberUpdatedState(crop)
    val latestChange by rememberUpdatedState(onCropChange)
    val selectionColor = MaterialTheme.colorScheme.primary
    val selectionContrast = MaterialTheme.colorScheme.onPrimary
    val canvasDescription = stringResource(R.string.free_crop_original)
    val frameShape = RoundedCornerShape(18.dp)

    Canvas(
        modifier
            .clip(frameShape)
            .background(Color(0xFF1A1F24))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), frameShape)
            .testTag("free_crop_canvas")
            .semantics {
                contentDescription = canvasDescription
                stateDescription = description
            }
            .pointerInput(source, minWidth, minHeight, aspect) {
                var start = latestCrop
                var total = Offset.Zero
                var corner: CropRect.Corner? = null
                var moving = false
                var active = false
                val touchRadius = 32.dp.toPx()
                detectDragGestures(
                    onDragStart = { point ->
                        start = latestCrop
                        total = Offset.Zero
                        val imageBounds = fittedImageBounds(size.width.toFloat(), size.height.toFloat(), source.width, source.height)
                        val selected = selectionBounds(imageBounds, start)
                        val nearest = listOf(
                            CropRect.Corner.TOP_LEFT to selected.topLeft,
                            CropRect.Corner.TOP_RIGHT to selected.topRight,
                            CropRect.Corner.BOTTOM_LEFT to selected.bottomLeft,
                            CropRect.Corner.BOTTOM_RIGHT to selected.bottomRight,
                        ).minByOrNull { (_, position) -> (position - point).getDistance() }
                        corner = nearest?.takeIf { (_, position) -> (position - point).getDistance() <= touchRadius }?.first
                        moving = corner == null && selected.contains(point)
                        active = corner != null || moving
                    },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false },
                ) { change, delta ->
                    if (active) {
                        change.consume()
                        total += delta
                        val imageBounds = fittedImageBounds(size.width.toFloat(), size.height.toFloat(), source.width, source.height)
                        val dx = total.x / imageBounds.width
                        val dy = total.y / imageBounds.height
                        latestChange(
                            if (moving) start.moved(dx, dy)
                            else start.resized(corner!!, dx, dy, minWidth, minHeight, aspect)
                        )
                    }
                }
            },
    ) {
        val imageBounds = fittedImageBounds(size.width, size.height, source.width, source.height)
        drawImage(
            image,
            dstOffset = IntOffset(imageBounds.left.roundToInt(), imageBounds.top.roundToInt()),
            dstSize = IntSize(imageBounds.width.roundToInt().coerceAtLeast(1), imageBounds.height.roundToInt().coerceAtLeast(1)),
            filterQuality = FilterQuality.High,
        )
        val selected = selectionBounds(imageBounds, crop)
        val shade = Color.Black.copy(alpha = 0.58f)
        drawRect(shade, imageBounds.topLeft, Size(imageBounds.width, (selected.top - imageBounds.top).coerceAtLeast(0f)))
        drawRect(shade, Offset(imageBounds.left, selected.bottom), Size(imageBounds.width, (imageBounds.bottom - selected.bottom).coerceAtLeast(0f)))
        drawRect(shade, Offset(imageBounds.left, selected.top), Size((selected.left - imageBounds.left).coerceAtLeast(0f), selected.height))
        drawRect(shade, Offset(selected.right, selected.top), Size((imageBounds.right - selected.right).coerceAtLeast(0f), selected.height))
        for (index in 1..2) {
            val x = selected.left + selected.width * index / 3f
            val y = selected.top + selected.height * index / 3f
            drawLine(Color.White.copy(alpha = 0.6f), Offset(x, selected.top), Offset(x, selected.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.6f), Offset(selected.left, y), Offset(selected.right, y), 1.dp.toPx())
        }
        drawRect(selectionContrast, selected.topLeft, selected.size, style = Stroke(4.dp.toPx()))
        drawRect(selectionColor, selected.topLeft, selected.size, style = Stroke(2.dp.toPx()))
        listOf(selected.topLeft, selected.topRight, selected.bottomLeft, selected.bottomRight).forEach { position ->
            drawCircle(selectionContrast, 7.5.dp.toPx(), position)
            drawCircle(selectionColor, 4.5.dp.toPx(), position)
        }
    }
}

private fun fittedImageBounds(width: Float, height: Float, sourceWidth: Int, sourceHeight: Int): Rect {
    val scale = min(width / sourceWidth, height / sourceHeight)
    val imageWidth = sourceWidth * scale
    val imageHeight = sourceHeight * scale
    val left = (width - imageWidth) / 2f
    val top = (height - imageHeight) / 2f
    return Rect(left, top, left + imageWidth, top + imageHeight)
}

private fun selectionBounds(image: Rect, crop: CropRect): Rect = Rect(
    image.left + image.width * crop.left,
    image.top + image.height * crop.top,
    image.left + image.width * crop.right,
    image.top + image.height * crop.bottom,
)

private fun fraction(value: Float, minimum: Float, maximum: Float): Float =
    if (maximum - minimum <= 0.000001f) 0f else ((value - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
