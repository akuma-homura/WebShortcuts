package io.github.homura251.webshortcuts

import android.app.Application
import android.content.Context
import io.github.homura251.webshortcuts.data.BookmarkRepository
import io.github.homura251.webshortcuts.icons.IconImporter
import io.github.homura251.webshortcuts.icons.IconProcessor
import io.github.homura251.webshortcuts.icons.IconResolver
import io.github.homura251.webshortcuts.icons.IconStore
import io.github.homura251.webshortcuts.icons.SiteIconDiscoverer
import io.github.homura251.webshortcuts.shortcuts.ShortcutPublisher
import kotlinx.coroutines.sync.Mutex

class WebShortcutsApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val repository = BookmarkRepository(context)
    val iconProcessor = IconProcessor()
    val iconStore = IconStore(context)
    val iconResolver = IconResolver(context, iconStore, iconProcessor)
    val iconImporter = IconImporter(context, iconProcessor)
    val siteIconDiscoverer = SiteIconDiscoverer(iconProcessor)
    val shortcutPublisher = ShortcutPublisher(context, iconResolver)
    // Serializes files + metadata + cleanup, including concurrent editor/batch saves.
    val mutationMutex = Mutex()
}
