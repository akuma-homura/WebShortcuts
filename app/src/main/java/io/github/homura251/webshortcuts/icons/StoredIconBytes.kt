package io.github.homura251.webshortcuts.icons

import android.graphics.BitmapFactory

/** Validate already processed layers without processing them a second time during restoration. */
internal object StoredIconBytes {
    fun validate(bytes: ByteArray, original: Boolean, extension: String) {
        val png = bytes.size >= 8 && bytes.copyOfRange(0, 8)
            .contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        val webp = bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        // Older private caches may use .webp names for PNG bytes. Trust decoded format, not suffix.
        require(extension in setOf("png", "webp") && (if (original) png else png || webp)) { "备份图像格式无效" }
        require(bytes.size <= if (original) IconProcessor.MAX_EDITABLE_BYTES else IconPolicy.MAX_IMAGE_BYTES) { "备份图像过大" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (original) require(bounds.outWidth in 1..IconProcessor.MAX_EDITABLE_SIZE && bounds.outHeight in 1..IconProcessor.MAX_EDITABLE_SIZE) { "备份原图尺寸无效" }
        else require(bounds.outWidth == IconPolicy.OUTPUT_SIZE && bounds.outHeight == IconPolicy.OUTPUT_SIZE) { "备份图标必须为 512×512 像素" }
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("备份图像已损坏")
        image.recycle()
    }
}
