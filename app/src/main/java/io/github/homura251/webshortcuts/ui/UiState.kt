package io.github.homura251.webshortcuts.ui

import android.graphics.Bitmap
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode

data class HomeUiState(
    val bookmarks: List<BookmarkItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val refreshing: Boolean = false,
    val query: String = "",
    val editor: EditorUiState? = null,
)

data class EditorUiState(
    val id: String,
    val isNew: Boolean,
    val title: String = "",
    val url: String = "",
    val mode: IconSourceMode = IconSourceMode.AUTO,
    val preview: Bitmap? = null,
    val sourceLabel: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val urlError: String? = null,
    val titleError: String? = null,
    val hasCustomImage: Boolean = false,
    val dirty: Boolean = false,
    val canCrop: Boolean = false,
    val centerCrop: Boolean = false,
)
