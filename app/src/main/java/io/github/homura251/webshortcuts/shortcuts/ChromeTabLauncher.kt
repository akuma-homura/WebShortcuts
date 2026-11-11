package io.github.homura251.webshortcuts.shortcuts

import android.content.Context

/** Compatibility entry point for callers that use automatic browser selection. */
object ChromeTabLauncher {
    fun open(context: Context, url: String): Boolean = BrowserLauncher.openWeb(context, url).opened
}
