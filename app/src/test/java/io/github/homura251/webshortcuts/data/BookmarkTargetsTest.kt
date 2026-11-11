package io.github.homura251.webshortcuts.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BookmarkTargetsTest {
    @Test fun v4MigratesToWebWithAutomaticBrowserWithoutChangingIdsTagsOrOrder() {
        val old = """{"schemaVersion":4,"tags":[{"id":"work","name":"工作"}],"bookmarks":[
            {"id":"second","title":"工作账号","url":"https://example.com/","tagIds":["work"]},
            {"id":"first","title":"个人账号","url":"https://example.com/","tagIds":[]}
        ]}"""
        val library = BookmarkCodec.decodeLibrary(old)
        assertEquals(listOf("second", "first"), library.bookmarks.map { it.id })
        assertEquals(setOf("work"), library.bookmarks.first().tagIds)
        library.bookmarks.forEach { assertEquals(BookmarkSourceType.WEB, it.sourceType); assertNull(it.browserPackage); assertFalse(it.isLocal) }
        assertEquals(library, BookmarkCodec.decodeLibrary(BookmarkCodec.encode(library)))
    }

    @Test fun sameUrlCanKeepDifferentBrowserAccountsAcrossRestart() {
        val work = BookmarkItem(id = "work", title = "工作", url = "https://example.com/", browserPackage = "com.chrome.beta")
        val personal = work.copy(id = "personal", title = "个人", browserPackage = "org.mozilla.firefox")
        assertEquals(listOf(work, personal), BookmarkCodec.decode(BookmarkCodec.encode(listOf(work, personal))))
    }

    @Test fun bothLocalFormatsRoundTripWithoutInventingNetworkUrls() {
        val html = local()
        val mhtml = html.copy(id = "archive", sourceType = BookmarkSourceType.MHTML, localFileName = "archive.mhtml", localDisplayName = "保存的页面.mht")
        assertEquals(listOf(html, mhtml), BookmarkCodec.decode(BookmarkCodec.encode(listOf(html, mhtml))))
    }

    @Test fun localReferencesCannotEscapeStorageOrBecomeWebLinks() {
        val invalid = listOf(
            local().copy(localFileName = "../private.html"),
            local().copy(localFileName = "/storage/index.html"),
            local().copy(localFileName = "nested/index.html"),
            local().copy(localFileName = "archive.mhtml"),
            local().copy(localFileName = null),
            local().copy(url = "https://example.com/"),
            local().copy(browserPackage = "com.android.chrome"),
            local().copy(localDisplayName = "bad\nname.html"),
        )
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { BookmarkCodec.encode(listOf(it)) } }
        val unknown = JSONObject(BookmarkCodec.encode(listOf(local())))
        unknown.getJSONArray("bookmarks").getJSONObject(0).put("sourceType", "FUTURE")
        assertThrows(IllegalArgumentException::class.java) { BookmarkCodec.decode(unknown.toString()) }
    }

    @Test fun corruptBrowserPackageCannotFallBackToAutomaticSelection() {
        val web = BookmarkItem(title = "Site", url = "https://example.com/", browserPackage = "com.chrome.beta")
        val json = JSONObject(BookmarkCodec.encode(listOf(web)))
        json.getJSONArray("bookmarks").getJSONObject(0).put("browserPackage", "com.chrome.beta/OtherActivity")
        assertThrows(IllegalArgumentException::class.java) { BookmarkCodec.decode(json.toString()) }
        assertThrows(IllegalArgumentException::class.java) {
            BookmarkCodec.encode(listOf(web.copy(localFileName = "unexpected.html")))
        }
    }

    @Test fun filenameSearchRespectsTagFiltersAndFindsLocalDocuments() {
        val page = local().copy(tagIds = setOf("travel"), localDisplayName = "Ticket-2026.HTML")
        val tags = listOf(BookmarkTag("travel", "旅行"), BookmarkTag("work", "工作"))
        assertEquals(listOf(page), LibraryTags.filter(listOf(page), tags, "ticket", TagFilter(setOf("travel"))))
        assertTrue(LibraryTags.filter(listOf(page), tags, "ticket", TagFilter(setOf("work"))).isEmpty())
    }

    private fun local() = BookmarkItem(id = "local", title = "离线资料", url = "", sourceType = BookmarkSourceType.HTML,
        localFileName = "document.html", localDisplayName = "原始资料.html", selectedIconSource = IconSourceMode.GENERATED)
}
