package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.BookmarkSortField
import io.github.homura251.webshortcuts.data.BookmarkTag
import io.github.homura251.webshortcuts.data.LibrarySortPreferences
import io.github.homura251.webshortcuts.data.SortDirection
import io.github.homura251.webshortcuts.data.TagFilter
import io.github.homura251.webshortcuts.data.TagSortField
import io.github.homura251.webshortcuts.ui.theme.AppTheme
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme

@Preview(widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun BookmarkSortingPreview() = SortPreview(LibrarySortPreferences(), OrderTarget.BOOKMARKS)

@Preview(widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun TagSortingDarkPreview() = SortPreview(
    LibrarySortPreferences(tags = TagSortField.COUNT, tagDirection = SortDirection.DESCENDING),
    OrderTarget.TAGS,
    true,
)

@Preview(widthDp = 320, heightDp = 640, fontScale = 2f, showBackground = true)
@Composable
fun SortingLargeFontPreview() = SortPreview(LibrarySortPreferences(bookmarks = BookmarkSortField.MANUAL), OrderTarget.BOOKMARKS)

@Preview(widthDp = 360, heightDp = 720, showBackground = true)
@Composable
fun ReorderBookmarksPreview() = ReorderPreview(false)

@Preview(widthDp = 320, heightDp = 640, fontScale = 2f, showBackground = true)
@Composable
fun ReorderLargeFontPreview() = ReorderPreview(false)

@Preview(widthDp = 360, heightDp = 720, showBackground = true)
@Composable
fun ReorderDarkPreview() = ReorderPreview(true)

@Composable
private fun SortPreview(value: LibrarySortPreferences, target: OrderTarget, dark: Boolean = false) {
    WebShortcutsTheme(darkTheme = dark, dynamicColor = false) {
        Surface(Modifier.fillMaxSize(), color = AppTheme.surfaces.canvas) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (target == OrderTarget.BOOKMARKS) "收藏排序" else "标签排序",
                    style = MaterialTheme.typography.headlineSmall,
                )
                SortingContent(value, target, {}, {})
            }
        }
    }
}

@Composable
private fun ReorderPreview(dark: Boolean) {
    val tags = listOf(BookmarkTag("work", "工作"), BookmarkTag("reference", "参考资料"))
    val items = listOf("Android 开发者", "GitHub", "Material Design", "维基百科", "Kotlin 文档").mapIndexed { i, title ->
        BookmarkItem(id = "demo-$i", title = title, url = "https://example.com/$i", tagIds = setOf("reference"))
    }
    val ids = items.map { it.id }
    WebShortcutsTheme(darkTheme = dark, dynamicColor = false) {
        ReorderContent(
            HomeUiState(
                loading = false,
                bookmarks = items,
                tags = tags,
                reorder = ReorderUiState(OrderTarget.BOOKMARKS, ids, ids, filter = TagFilter(setOf("reference"))),
            ),
            SortActions(),
        )
    }
}
