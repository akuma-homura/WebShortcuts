package io.github.homura251.webshortcuts.shortcuts

import android.app.Application
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.test.core.app.ApplicationProvider
import io.github.homura251.webshortcuts.data.BookmarkItem
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BrowserLauncherTest {
    private lateinit var application: Application

    @Before fun setUp() { application = ApplicationProvider.getApplicationContext() }

    @Test fun catalogDiscoversBetaFirefoxAndExternalBrowserWithoutShowingDeepLinkApps() {
        installBrowser("com.chrome.beta", "Chrome Beta", true)
        installBrowser("org.mozilla.firefox", "Firefox", true)
        installBrowser("org.example.browser", "Other Browser", false)
        installBrowser("org.example.deep_link", "A website's app", false, "www.example.com")
        val available = BrowserCatalog.list(application)
        assertEquals(setOf("com.chrome.beta", "org.mozilla.firefox", "org.example.browser"), available.map { it.packageName }.toSet())
        assertTrue(available.first { it.packageName == "org.mozilla.firefox" }.supportsCustomTabs)
        assertFalse(available.first { it.packageName == "org.example.browser" }.supportsCustomTabs)
    }

    @Test fun customTabIntentPinsFirefoxAndKeepsNormalSessionSemantics() {
        installBrowser("org.mozilla.firefox", "Firefox", true)
        val result = BrowserLauncher.open(application,
            BookmarkItem(title = "Work", url = "https://example.com/work", browserPackage = "org.mozilla.firefox"))
        assertTrue(result.opened)
        val launched = shadowOf(application).nextStartedActivity
        assertEquals("org.mozilla.firefox", launched.`package`)
        assertEquals("https://example.com/work", launched.dataString)
        assertTrue(launched.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertFalse(launched.getBooleanExtra("androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING", false))
        assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun runtimeEnabledProvidersAreNotRejectedByTheirManifestDefault() {
        val pkg = "org.example.reenabled"
        installBrowser(pkg, "Re-enabled Browser", true, declaredEnabled = false)
        val manager = application.packageManager
        for (name in listOf("$pkg.Browser", "$pkg.CustomTabs")) {
            manager.setComponentEnabledSetting(ComponentName(pkg, name),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        assertTrue(BrowserCatalog.list(application).any { it.packageName == pkg && it.supportsCustomTabs })
    }

    @Test fun removedSelectionReturnsActionableErrorWithoutLaunchingAnything() {
        installBrowser("com.android.chrome", "Chrome", true)
        val result = BrowserLauncher.openWeb(application, "https://example.com/", "com.chrome.beta")
        assertFalse(result.opened)
        assertTrue(result.message!!.contains("Chrome Beta"))
        assertNull(shadowOf(application).nextStartedActivity)
    }

    @Test fun directActivityLaunchDoesNotPutTheBrowserAboveTheCollection() {
        installBrowser("org.mozilla.firefox", "Firefox", true)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(BrowserLauncher.openWeb(activity, "https://example.com/", "org.mozilla.firefox").opened)
            val launched = shadowOf(activity).nextStartedActivity
            assertEquals("https://example.com/", launched.dataString)
            assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun bookmarkHostKeepsTheCustomTabInItsOwnDocumentTask() {
        installBrowser("org.mozilla.firefox", "Firefox", true)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(BrowserLauncher.openInBookmarkTask(activity,
                BookmarkItem(title = "Work", url = "https://example.com/work", browserPackage = "org.mozilla.firefox")).opened)
            val launched = shadowOf(activity).nextStartedActivity
            assertEquals("org.mozilla.firefox", launched.`package`)
            assertTrue(launched.hasExtra(CustomTabsIntent.EXTRA_SESSION))
            assertEquals(0, launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun documentIdentityUsesBookmarkIdInsteadOfItsEditableUrl() {
        val first = BookmarkItem(title = "First", url = "https://example.com/")
        val second = first.copy(id = java.util.UUID.randomUUID().toString())
        val original = BookmarkWindows.intent(application, first.id)
        assertEquals(original.data, BookmarkWindows.intent(application, first.copy(url = "https://changed.example/").id).data)
        assertNotEquals(original.data, BookmarkWindows.intent(application, second.id).data)
        assertNull(original.data?.host?.takeIf { it == "example.com" })
        assertEquals(first.id, original.getStringExtra(ShortcutIntents.EXTRA_BOOKMARK_ID))
    }

    @Test fun bookmarkHostCanOwnTheBrowserResultWithoutChangingItsProviderOrTask() {
        installBrowser("org.mozilla.firefox", "Firefox", true)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            var launched: Intent? = null
            val result = BrowserLauncher.openInBookmarkTask(activity,
                BookmarkItem(title = "Floating", url = "https://example.com/preserved", browserPackage = "org.mozilla.firefox")) { intent, _ ->
                launched = intent
            }
            assertTrue(result.opened)
            assertTrue(result.customTab)
            assertEquals("org.mozilla.firefox", launched!!.`package`)
            assertEquals("https://example.com/preserved", launched!!.dataString)
            assertTrue(launched!!.hasExtra(CustomTabsIntent.EXTRA_SESSION))
            assertEquals(0, launched!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun plainBrowserFallbackDoesNotNeedAnEmptyBookmarkHost() {
        installBrowser("org.example.browser", "Other Browser", false)
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val result = BrowserLauncher.openInBookmarkTask(activity,
                BookmarkItem(title = "Work", url = "https://example.com/work", browserPackage = "org.example.browser"))
            assertTrue(result.opened)
            assertFalse(result.customTab)
            val launched = shadowOf(activity).nextStartedActivity
            assertEquals("org.example.browser", launched.`package`)
            assertTrue(launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun providerFailureRetriesOnlyTheSelectedPackage() {
        installBrowser("com.chrome.beta", "Chrome Beta", true)
        installBrowser("com.android.chrome", "Chrome", true)
        val attempts = mutableListOf<Intent>()
        val context = object : ContextWrapper(application) {
            override fun startActivity(intent: Intent) { attempts += intent; throw ActivityNotFoundException() }
            override fun startActivity(intent: Intent, options: Bundle?) { attempts += intent; throw ActivityNotFoundException() }
        }
        val result = BrowserLauncher.openWeb(context, "https://example.com/", "com.chrome.beta")
        assertFalse(result.opened)
        assertEquals(2, attempts.size)
        assertEquals(setOf("com.chrome.beta"), attempts.map { it.`package` }.toSet())
    }

    private fun installBrowser(packageName: String, label: String, customTabs: Boolean, host: String? = null,
                               declaredEnabled: Boolean = true) {
        val appInfo = ApplicationInfo().apply { this.packageName = packageName; enabled = true }
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("https")
            if (host != null) addDataAuthority(host, null)
        }
        val activity = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName; name = "$packageName.Browser"; enabled = declaredEnabled; exported = true; applicationInfo = appInfo
            }
            nonLocalizedLabel = label
            this.filter = filter
        }
        val manager = shadowOf(application.packageManager)
        manager.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE), activity)
        if (customTabs) manager.addResolveInfoForIntent(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                this.packageName = packageName; name = "$packageName.CustomTabs"; enabled = declaredEnabled; exported = true; applicationInfo = appInfo
            }
        })
    }
}
