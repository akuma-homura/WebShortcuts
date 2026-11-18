package io.github.homura251.webshortcuts.shortcuts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.homura251.webshortcuts.data.BookmarkItem

/** One document task per saved bookmark, independent of the collection's launcher task. */
object BookmarkWindows {
    fun open(context: Context, bookmark: BookmarkItem): LaunchResult {
        if (bookmark.isLocal) return BrowserLauncher.open(context, bookmark)
        return try {
            context.startActivity(intent(context, bookmark.id))
            LaunchResult(true)
        } catch (_: ActivityNotFoundException) {
            LaunchResult(false, "无法打开网页窗口，请重试")
        } catch (_: SecurityException) {
            LaunchResult(false, "无法打开网页窗口，请重试")
        }
    }

    internal fun intent(context: Context, id: String): Intent =
        Intent(context, BookmarkWindowActivity::class.java)
            .setAction(ShortcutIntents.ACTION_OPEN_BOOKMARK)
            .setData(Uri.Builder().scheme("webshortcuts").authority("bookmark").appendPath(id).build())
            .putExtra(ShortcutIntents.EXTRA_BOOKMARK_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
}
