package io.github.homura251.webshortcuts.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.browser.customtabs.CustomTabsService
import java.util.Locale

data class BrowserOption(
    val packageName: String,
    val label: String,
    val supportsCustomTabs: Boolean,
)

/** Discovers installed HTTPS handlers and Custom Tabs services instead of assuming Chrome. */
object BrowserCatalog {
    fun list(context: Context): List<BrowserOption> {
        val manager = context.packageManager
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val providers = manager.queryIntentServices(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), 0)
            .mapNotNull { it.serviceInfo?.takeIf { service -> service.enabled && service.exported }?.packageName }
            .toSet()
        return manager.queryIntentActivities(viewIntent, PackageManager.GET_RESOLVED_FILTER)
            .mapNotNull { resolved ->
                val activity = resolved.activityInfo ?: return@mapNotNull null
                if (!activity.enabled || !activity.exported || activity.packageName == context.packageName) return@mapNotNull null
                // Do not present domain-specific deep-link apps as browsers. Custom Tabs services
                // also identify browsers on OEMs that do not return the resolved filter.
                if (resolved.filter?.countDataAuthorities() != 0 && activity.packageName !in providers) return@mapNotNull null
                BrowserOption(activity.packageName, resolved.loadLabel(manager).toString(), activity.packageName in providers)
            }
            .distinctBy(BrowserOption::packageName)
            .sortedWith(compareBy<BrowserOption> { BrowserLaunchPolicy.priority(it.packageName) }
                .thenBy { it.label.lowercase(Locale.ROOT) })
    }

    fun knownLabel(packageName: String): String = when (packageName) {
        "com.android.chrome" -> "Chrome"
        "com.chrome.beta" -> "Chrome Beta"
        "com.chrome.dev" -> "Chrome Dev"
        "com.chrome.canary" -> "Chrome Canary"
        "org.mozilla.firefox" -> "Firefox"
        "org.mozilla.firefox_beta" -> "Firefox Beta"
        "org.mozilla.fenix" -> "Firefox Nightly"
        "com.microsoft.emmx" -> "Microsoft Edge"
        "com.brave.browser" -> "Brave"
        "com.sec.android.app.sbrowser" -> "Samsung Internet"
        else -> packageName
    }
}

/** A selected provider is an account boundary, including when that browser is uninstalled. */
internal object BrowserLaunchPolicy {
    private val preferred = listOf("com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary")
    fun priority(packageName: String): Int = preferred.indexOf(packageName).takeIf { it >= 0 } ?: preferred.size

    fun candidates(selectedPackage: String?, available: List<BrowserOption>): List<BrowserOption> =
        if (selectedPackage != null) available.filter { it.packageName == selectedPackage }
        else available.sortedWith(compareBy<BrowserOption> { !it.supportsCustomTabs }.thenBy { priority(it.packageName) })
}
