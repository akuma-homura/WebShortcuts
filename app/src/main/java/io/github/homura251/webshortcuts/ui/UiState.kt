package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.icons.CropRect

data class HomeUiState(
    val bookmarks: List<BookmarkItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val refreshing: Boolean = false,
    val refreshingIds: Set<String> = emptySet(),
    val refreshCompleted: Int = 0,
    val refreshTotal: Int = 0,
    val refreshSummary: String? = null,
    val refreshMessages: Map<String, String> = emptyMap(),
    val query: String = "",
    val editor: EditorUiState? = null,
    val transferBusy: Boolean = false,
)

data class EditorUiState(
    val id: String,
    val isNew: Boolean,
    val title: String = "",
    val url: String = "",
    val mode: IconSourceMode = IconSourceMode.AUTO,
    val preview: Bitmap? = null,
    val sourceLabel: String? = null,
    val sourceUrl: String? = null,
    val lastFetchedAt: Long? = null,
    val statusMessage: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val urlError: String? = null,
    val titleError: String? = null,
    val hasCustomImage: Boolean = false,
    val dirty: Boolean = false,
    val canCrop: Boolean = false,
    val centerCrop: Boolean = false,
    val cropSource: Bitmap? = null,
    val cropRect: CropRect? = null,
)
