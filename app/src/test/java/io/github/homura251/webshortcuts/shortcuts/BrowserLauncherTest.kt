package io.github.homura251.webshortcuts.shortcuts

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
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

    @Test fun removedSelectionReturnsActionableErrorWithoutLaunchingAnything() {
        installBrowser("com.android.chrome", "Chrome", true)
        val result = BrowserLauncher.openWeb(application, "https://example.com/", "com.chrome.beta")
        assertFalse(result.opened)
        assertTrue(result.message!!.contains("Chrome Beta"))
        assertNull(shadowOf(application).nextStartedActivity)
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

    private fun installBrowser(packageName: String, label: String, customTabs: Boolean, host: String? = null) {
        val appInfo = ApplicationInfo().apply { this.packageName = packageName; enabled = true }
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); addCategory(Intent.CATEGORY_DEFAULT); addDataScheme("https")
            if (host != null) addDataAuthority(host, null)
        }
        val activity = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName; name = "$packageName.Browser"; enabled = true; exported = true; applicationInfo = appInfo
            }
            nonLocalizedLabel = label
            this.filter = filter
        }
        val manager = shadowOf(application.packageManager)
        manager.addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE), activity)
        if (customTabs) manager.addResolveInfoForIntent(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply {
                this.packageName = packageName; name = "$packageName.CustomTabs"; enabled = true; exported = true; applicationInfo = appInfo
            }
        })
    }
}
