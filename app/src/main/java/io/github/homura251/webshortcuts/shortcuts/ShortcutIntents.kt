package io.github.homura251.webshortcuts.shortcuts

import android.content.Context
import android.content.Intent

/** No URL or image path belongs in a launcher intent: always resolve current data by ID. */
object ShortcutIntents {
    const val EXTRA_BOOKMARK_ID = "io.github.homura251.webshortcuts.BOOKMARK_ID"
    const val ACTION_OPEN_BOOKMARK = "io.github.homura251.webshortcuts.OPEN_BOOKMARK"

    fun forBookmark(context: Context, bookmarkId: String): Intent =
        Intent(context, ShortcutRouterActivity::class.java)
            .setAction(ACTION_OPEN_BOOKMARK)
            .putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
