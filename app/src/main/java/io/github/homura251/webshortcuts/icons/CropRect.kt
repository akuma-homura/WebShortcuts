package io.github.homura251.webshortcuts.icons

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Image-relative coordinates. Safe to persist as four JSON numbers, independently of display size. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(listOf(left, top, right, bottom).all { it.isFinite() }) { "Crop coordinates must be finite." }
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && left < right && top < bottom) {
            "Crop must be a nonempty rectangle inside the image."
        }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /** Translate without changing the selection's size, stopping at the image edges. */
    fun moved(dx: Float, dy: Float): CropRect {
        require(dx.isFinite() && dy.isFinite())
        val x = dx.coerceIn(-left, 1f - right)
        val y = dy.coerceIn(-top, 1f - bottom)
        return CropRect((left + x).coerceIn(0f, 1f), (top + y).coerceIn(0f, 1f),
            (right + x).coerceIn(0f, 1f), (bottom + y).coerceIn(0f, 1f))
    }

    fun clamped(minWidth: Float = MIN_EDGE, minHeight: Float = MIN_EDGE): CropRect =
        clamp(left, top, right, bottom, minWidth, minHeight)

    /**
     * Drag one corner while keeping the opposite corner fixed. aspectRatio is normalized width /
     * height: a square in source pixels uses sourceHeight / sourceWidth, rather than 1f.
     */
    fun resized(corner: Corner, dx: Float, dy: Float, minWidth: Float = MIN_EDGE,
                minHeight: Float = MIN_EDGE, aspectRatio: Float? = null): CropRect {
        require(dx.isFinite() && dy.isFinite())
        validateMinimum(minWidth, minHeight)
        val base = if (aspectRatio == null) clamped(minWidth, minHeight)
            else withAspectRatio(aspectRatio, minWidth, minHeight)
        val moveLeft = corner == Corner.TOP_LEFT || corner == Corner.BOTTOM_LEFT
        val moveTop = corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT
        val anchorX = if (moveLeft) base.right else base.left
        val anchorY = if (moveTop) base.bottom else base.top
        val xDirection = if (moveLeft) -1f else 1f
        val yDirection = if (moveTop) -1f else 1f
        val requestedWidth = base.width + xDirection * dx
        val requestedHeight = base.height + yDirection * dy
        val availableWidth = if (moveLeft) anchorX else 1f - anchorX
        val availableHeight = if (moveTop) anchorY else 1f - anchorY
        val newWidth: Float
        val newHeight: Float
        if (aspectRatio == null) {
            newWidth = requestedWidth.coerceIn(minWidth.coerceAtMost(availableWidth), availableWidth)
            newHeight = requestedHeight.coerceIn(minHeight.coerceAtMost(availableHeight), availableHeight)
        } else {
            val minimum = max(minWidth, minHeight * aspectRatio)
            val maximum = min(availableWidth, availableHeight * aspectRatio)
            val requested = if (abs(dx) >= abs(dy * aspectRatio)) requestedWidth else requestedHeight * aspectRatio
            newWidth = requested.coerceIn(minimum.coerceAtMost(maximum), maximum)
            newHeight = newWidth / aspectRatio
        }
        val newLeft = if (moveLeft) anchorX - newWidth else anchorX
        val newTop = if (moveTop) anchorY - newHeight else anchorY
        return CropRect(newLeft.coerceIn(0f, 1f), newTop.coerceIn(0f, 1f),
            (newLeft + newWidth).coerceIn(0f, 1f), (newTop + newHeight).coerceIn(0f, 1f))
    }

    /** Fit the requested aspect around the current center; expand only to meet the minimum size. */
    fun withAspectRatio(aspectRatio: Float, minWidth: Float = MIN_EDGE,
                        minHeight: Float = MIN_EDGE): CropRect {
        require(aspectRatio.isFinite() && aspectRatio > 0f)
        validateMinimum(minWidth, minHeight)
        val minimum = max(minWidth, minHeight * aspectRatio)
        val maximum = min(1f, aspectRatio)
        require(minimum <= maximum + 0.000001f) { "The minimum crop cannot fit this aspect ratio." }
        val newWidth = min(width, height * aspectRatio).coerceIn(minimum.coerceAtMost(maximum), maximum)
        val newHeight = newWidth / aspectRatio
        return clamp(centerX - newWidth / 2f, centerY - newHeight / 2f,
            centerX + newWidth / 2f, centerY + newHeight / 2f, minWidth, minHeight)
    }

    companion object {
        private const val MIN_EDGE = 0.000001f
        val FULL = CropRect(0f, 0f, 1f, 1f)

        /** Clamp finite coordinates to the image, retaining their center and requested size. */
        fun clamp(left: Float, top: Float, right: Float, bottom: Float,
                  minWidth: Float = MIN_EDGE, minHeight: Float = MIN_EDGE): CropRect {
            require(listOf(left, top, right, bottom).all { it.isFinite() })
            validateMinimum(minWidth, minHeight)
            val width = abs(right - left).coerceIn(minWidth, 1f)
            val height = abs(bottom - top).coerceIn(minHeight, 1f)
            val centerX = (left / 2f + right / 2f).coerceIn(width / 2f, 1f - width / 2f)
            val centerY = (top / 2f + bottom / 2f).coerceIn(height / 2f, 1f - height / 2f)
            return CropRect((centerX - width / 2f).coerceAtLeast(0f), (centerY - height / 2f).coerceAtLeast(0f),
                (centerX + width / 2f).coerceAtMost(1f), (centerY + height / 2f).coerceAtMost(1f))
        }

        /** At least 5% of the source's shorter side or 32 pixels, limited by the source itself. */
        fun minimumSize(sourceWidth: Int, sourceHeight: Int): CropMinimumSize {
            require(sourceWidth > 0 && sourceHeight > 0)
            val pixels = max(32f, min(sourceWidth, sourceHeight) * 0.05f)
                .coerceAtMost(min(sourceWidth, sourceHeight).toFloat())
            return CropMinimumSize(pixels / sourceWidth, pixels / sourceHeight)
        }

        private fun validateMinimum(width: Float, height: Float) {
            require(width.isFinite() && height.isFinite() && width > 0f && height > 0f && width <= 1f && height <= 1f)
        }
    }
}

data class CropMinimumSize(val width: Float, val height: Float)
