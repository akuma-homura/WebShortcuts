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
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.UrlNormalizer
import io.github.homura251.webshortcuts.local.LocalPageActivity

data class LaunchResult(val opened: Boolean, val message: String? = null)

/** Opens the chosen browser's normal profile; no ephemeral session or cookie manipulation. */
object BrowserLauncher {
    fun open(context: Context, bookmark: BookmarkItem): LaunchResult {
        if (bookmark.isLocal) {
            return if (start(context, LocalPageActivity.intent(context, bookmark.id))) LaunchResult(true)
                else LaunchResult(false, "无法打开离线阅读器，请重试")
        }
        return openWeb(context, bookmark.url, bookmark.browserPackage)
    }

    fun openWeb(context: Context, url: String, browserPackage: String? = null): LaunchResult {
        val uri = try { Uri.parse(UrlNormalizer.normalize(url)) } catch (_: IllegalArgumentException) {
            return LaunchResult(false, "网址无效，请编辑此快捷方式")
        }
        val available = runCatching { BrowserCatalog.list(context) }.getOrDefault(emptyList())
        val candidates = BrowserLaunchPolicy.candidates(browserPackage, available)
        if (browserPackage != null && candidates.isEmpty()) {
            return LaunchResult(false, "所选浏览器 ${BrowserCatalog.knownLabel(browserPackage)} 不可用，请重新安装或在编辑页更换浏览器")
        }
        for (browser in candidates) {
            if (browser.supportsCustomTabs) {
                val tab = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
                    .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(toolbarColor(context)).build())
                    .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                    .build()
                tab.intent.setPackage(browser.packageName)
                if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    tab.launchUrl(context, uri)
                    return LaunchResult(true)
                } catch (_: ActivityNotFoundException) {
                    // A provider can disappear between discovery and launch.
                } catch (_: SecurityException) {
                    // Only the same package may be tried for an explicit selection.
                }
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(browser.packageName)
            if (start(context, intent)) return LaunchResult(true)
        }
        // Automatic mode may invoke Android's resolver for a non-standard undiscoverable browser.
        if (browserPackage == null && start(context,
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))) return LaunchResult(true)
        return LaunchResult(false, if (browserPackage == null) "未找到可用浏览器，请安装 Chrome、Firefox 等浏览器"
            else "无法启动所选浏览器 ${BrowserCatalog.knownLabel(browserPackage)}，请检查浏览器或更换设置")
    }

    private fun start(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true }
        catch (_: ActivityNotFoundException) { false }
        catch (_: SecurityException) { false }
    }

    private fun toolbarColor(context: Context): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.getColor(context, if (night) android.R.color.system_accent1_800 else android.R.color.system_accent1_100)
        } else if (night) Color.rgb(33, 34, 42) else Color.rgb(235, 237, 255)
    }
}
