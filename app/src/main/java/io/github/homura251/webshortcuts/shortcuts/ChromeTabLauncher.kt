package io.github.homura251.webshortcuts.shortcuts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.content.ContextCompat
import io.github.homura251.webshortcuts.data.UrlNormalizer

/** Uses the selected browser's normal profile, preserving its cookies and signed-in sessions. */
object ChromeTabLauncher {
    private val chromePackages = listOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
    )

    fun open(context: Context, url: String): Boolean {
        val uri = try { Uri.parse(UrlNormalizer.normalize(url)) } catch (_: IllegalArgumentException) {
            return false
        }
        val provider = runCatching { findProvider(context) }.getOrNull()
        if (provider != null) {
            val customTab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor(context)).build(),
                )
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            customTab.intent.setPackage(provider)
            if (context !is Activity) customTab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                customTab.launchUrl(context, uri)
                return true
            } catch (_: ActivityNotFoundException) {
                // A browser can be removed between provider discovery and launching it.
            } catch (_: SecurityException) {
                // Device policy may prohibit a selected browser; try the ordinary browser route.
            }
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun findProvider(context: Context): String? {
        val preferred = chromePackages.firstOrNull { packageName ->
            val service = Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(packageName)
            context.packageManager.resolveService(service, 0) != null
        }
        return preferred ?: CustomTabsClient.getPackageName(context, null)
    }

    private fun toolbarColor(context: Context): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getColor(context, if (night) android.R.color.system_accent1_800 else android.R.color.system_accent1_100)
        } else if (night) Color.rgb(33, 34, 42) else Color.rgb(235, 237, 255)
    }
}
