package io.github.homura251.webshortcuts.shortcuts

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ShortcutIntentsTest {
    @Test fun `launcher intent points only to the internal router and stable bookmark ID`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val item = BookmarkItem(title = "Website", url = "https://first.example/")
        val intent = ShortcutIntents.forBookmark(context, item.id)
        assertEquals(ShortcutRouterActivity::class.java.name, intent.component?.className)
        assertEquals(context.packageName, intent.component?.packageName)
        assertEquals(ShortcutIntents.ACTION_OPEN_BOOKMARK, intent.action)
        assertEquals(item.id, intent.getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID))
        assertEquals(setOf(ShortcutIntents.EXTRA_BOOKMARK_ID), intent.extras?.keySet())
        assertNull(intent.data)
        val edited = item.copy(title = "New name", url = "https://second.example/")
        assertEquals(intent.getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID),
            ShortcutIntents.forBookmark(context, edited.id).getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID))
    }

    @Test fun `browser launcher rejects executable and non web destinations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(ChromeTabLauncher.open(context, "javascript:alert(1)"))
        assertFalse(ChromeTabLauncher.open(context, "file:///private/data"))
    }
}
