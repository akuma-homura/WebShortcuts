package io.github.homura251.webshortcuts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BookmarkCodecTest {
    private val id = "d75e094f-e593-4835-874b-2526e8c63dbd"

    @Test fun `all v1_1 metadata survives a round trip including inactive custom image`() {
        val item = BookmarkItem(
            id = id, title = "文档", url = "https://example.com/", selectedIconSource = IconSourceMode.AUTO,
            autoImagePath = "icons/auto/${id}_v3.webp", customImagePath = "icons/custom/${id}_v2.webp",
            sourceUrl = "https://example.com/icon.png", imageVersion = 3, lastFetchedAt = 12345,
            iconDescription = "Manifest · 512×512", createdAt = 10, updatedAt = 20,
        )
        assertEquals(listOf(item), BookmarkCodec.decode(BookmarkCodec.encode(listOf(item))))
    }

    @Test fun `legacy bookmarks keep their stable UUID and generated icon mode`() {
        val json = """[{"uuid":"$id","name":"旧收藏","url":"example.com"}]"""
        val item = BookmarkCodec.decode(json).single()
        assertEquals(id, item.id)
        assertEquals("旧收藏", item.title)
        assertEquals("https://example.com/", item.url)
        assertEquals(IconSourceMode.GENERATED, item.selectedIconSource)
        assertEquals(1, item.imageVersion)
    }

    @Test fun `migration without an id is deterministic and distinguishes duplicates`() {
        val json = """[{"title":"one","url":"example.com"},{"title":"two","url":"example.com"}]"""
        val first = BookmarkCodec.decode(json)
        val second = BookmarkCodec.decode(json)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertNotEquals(first[0].id, first[1].id)
    }

    @Test fun `unknown icon mode falls back to AUTO and unsafe paths never escape private icons`() {
        val json = """{"bookmarks":[{"id":"$id","title":"Example","url":"https://example.com/","selectedIconSource":"FUTURE","customImagePath":"../../private/key","autoImagePath":"/storage/picture.png"}]}"""
        val item = BookmarkCodec.decode(json).single()
        assertEquals(IconSourceMode.AUTO, item.selectedIconSource)
        assertNull(item.customImagePath)
        assertNull(item.autoImagePath)
    }

    @Test fun `corrupt duplicate and unsupported future data cannot silently replace the collection`() {
        val cases = listOf(
            "", "not json", "{}", "{\"schemaVersion\":99,\"bookmarks\":[]}",
            """[{"id":"$id","title":"one","url":"example.com"},{"id":"$id","title":"two","url":"example.org"}]""",
            """[{"id":"$id","title":"one","url":"javascript:alert(1)"}]""",
        )
        cases.forEach { json -> assertThrows(Exception::class.java) { BookmarkCodec.decode(json) } }
    }

    @Test fun `empty collection and null image metadata round trip`() {
        assertEquals(emptyList<BookmarkItem>(), BookmarkCodec.decode(BookmarkCodec.encode(emptyList())))
        val item = BookmarkItem(id = id, title = "Example", url = "https://example.com/", createdAt = 10, updatedAt = 10)
        assertEquals(item, BookmarkCodec.decode(BookmarkCodec.encode(listOf(item))).single())
    }
}
