package io.github.homura251.webshortcuts.transfer

import io.github.homura251.webshortcuts.data.BookmarkCodec
import io.github.homura251.webshortcuts.data.BookmarkItem
import java.nio.charset.Charset
import org.junit.Assert.*
import org.junit.Test

class BookmarkHtmlCodecTest {
    @Test fun nestedFoldersAreFlattenedAndInvalidSchemesAndDuplicatesAreCounted() {
        val result = parse("""<DT><H3>Folder</H3><DL><p>
            <DT><A HREF="HTTPS://EXAMPLE.COM:443">First</A>
            <DT><A HREF="https://example.com/">Duplicate</A>
            <DT><A HREF="https://example.com/path?q=1#section">独立页面</A>
            <DT><A HREF="javascript:alert(1)">Script</A>
            <DT><A HREF="file:///private/file">File</A>
            <DT><A HREF="relative/path">Relative</A></DL>""")
        assertEquals(listOf("First", "独立页面"), result.items.map { it.title })
        assertEquals(1, result.duplicateCount)
        assertEquals(3, result.invalidCount)
        assertEquals("https://example.com/", result.items.first().url)
        assertEquals(2, result.items.map { it.id }.toSet().size)
        assertTrue(result.items.all { it.autoImagePath == null && it.lastFetchedAt == null })
    }

    @Test fun exportedMarkupEscapesContentAndRoundTripsUnicodeUrlsAndDates() {
        val item = BookmarkItem(title = "中文 <标题> & \"引号\"", url = "https://example.com/?a=1&b=%22#part",
            createdAt = 1_700_000_000_000, updatedAt = 1_700_000_005_000)
        val bytes = BookmarkHtmlCodec.encode(listOf(item))
        assertTrue(bytes.toString(Charsets.UTF_8).contains("&lt;标题&gt; &amp; &quot;"))
        val restored = BookmarkHtmlCodec.decode(bytes).items.single()
        assertEquals(item.title, restored.title)
        assertEquals(item.url, restored.url)
        assertEquals(item.createdAt, restored.createdAt)
        assertEquals(item.updatedAt, restored.updatedAt)
    }

    @Test fun declaredLegacyCharsetAndUnicodeBomAreRespected() {
        val html = "<!DOCTYPE NETSCAPE-Bookmark-file-1><META charset=GB18030><DL><DT><A HREF=\"https://example.com/\">开发文档</A></DL>"
        assertEquals("开发文档", BookmarkHtmlCodec.decode(html.toByteArray(Charset.forName("GB18030"))).items.single().title)
        val utf16 = "\uFEFF" + html.replace("GB18030", "UTF-8")
        assertEquals("开发文档", BookmarkHtmlCodec.decode(utf16.toByteArray(Charsets.UTF_16LE)).items.single().title)
    }

    @Test fun namesUseStorageLimitAndEmptyTitlesFallBackToTheHost() {
        val result = parse("<DT><A HREF=\"https://a.test/\">${"名".repeat(160)}</A>" +
            "<DT><A HREF=\"https://b.test/\">${"名".repeat(161)}</A><DT><A HREF=\"https://c.test/\"></A>")
        assertEquals(listOf("名".repeat(160), "c.test"), result.items.map { it.title })
        assertEquals(1, result.invalidCount)
    }

    @Test fun aWebpageOrEmptyInputIsNotAcceptedAsAnEmptyBackup() {
        assertThrows(IllegalArgumentException::class.java) { BookmarkHtmlCodec.decode(byteArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { BookmarkHtmlCodec.decode("<html><a href='https://example.com'>Site</a></html>".toByteArray()) }
        assertTrue(parse("").items.isEmpty())
        assertTrue(parse("<DT><A HREF='about:blank'>Empty</A>").items.isEmpty())
    }

    @Test fun oversizedInputsAndCollectionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BookmarkHtmlCodec.decode(ByteArray(BookmarkCodec.MAX_JSON_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            parse((0..2000).joinToString("") { "<DT><A HREF='https://example.com/$it'>Site</A>" })
        }
    }

    @Test fun checkpointCanCancelParsingBeforeAnyDataIsReturned() {
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            BookmarkHtmlCodec.decode("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL></DL>".toByteArray(), checkpoint = { throw kotlinx.coroutines.CancellationException() })
        }
    }

    private fun parse(body: String) = BookmarkHtmlCodec.decode(("<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p>" + body + "</DL>").toByteArray())
}
