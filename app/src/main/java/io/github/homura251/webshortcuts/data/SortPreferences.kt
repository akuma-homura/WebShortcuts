package io.github.homura251.webshortcuts.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local display preferences are deliberately excluded from backups. */
class SortPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("sorting", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(LibrarySortPreferences(
        bookmarks = read("bookmarks", BookmarkSortField.CREATED_AT),
        bookmarkDirection = read("bookmark_direction", SortDirection.DESCENDING),
        tags = read("tags", TagSortField.NAME),
        tagDirection = read("tag_direction", SortDirection.ASCENDING),
    ))
    val state = mutableState.asStateFlow()

    fun update(value: LibrarySortPreferences) {
        preferences.edit().putString("bookmarks", value.bookmarks.name)
            .putString("bookmark_direction", value.bookmarkDirection.name)
            .putString("tags", value.tags.name).putString("tag_direction", value.tagDirection.name).apply()
        mutableState.value = value
    }

    private inline fun <reified T : Enum<T>> read(key: String, fallback: T): T {
        val stored = try { preferences.getString(key, null) } catch (_: ClassCastException) { null }
        return enumValues<T>().firstOrNull { it.name == stored } ?: fallback
    }
}
