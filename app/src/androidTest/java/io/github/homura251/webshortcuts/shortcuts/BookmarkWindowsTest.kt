package io.github.homura251.webshortcuts.shortcuts

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.graphics.Rect
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.filters.SdkSuppress
import io.github.homura251.webshortcuts.MainActivity
import io.github.homura251.webshortcuts.WebShortcutsApplication
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.io.File
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class BookmarkWindowsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val application get() = context.applicationContext as WebShortcutsApplication
    private val manager get() = context.getSystemService(ActivityManager::class.java)
    private val opened = LinkedBlockingQueue<Intent>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { opened.offer(intent) }
    }

    @Before fun prepare(): Unit = runBlocking {
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        application.container.repository.load()
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        setFixtureEnabled(true)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(WindowBrowserFixture.ACTION_RESUMED),
            ContextCompat.RECEIVER_EXPORTED)
        try {
            await("The enabled fixture must be discoverable before testing task routing") {
                BrowserCatalog.list(context).any { it.packageName == instrumentation.context.packageName && it.supportsCustomTabs }
            }
        } catch (failure: AssertionError) {
            throw AssertionError("${failure.message}\n${fixtureDiscovery()}", failure)
        }
    }

    @After fun close() {
        shell("am force-stop com.android.chrome")
        context.unregisterReceiver(receiver)
        manager.appTasks.filter { it.taskInfo?.baseIntent?.component?.className == BookmarkWindowActivity::class.java.name }
            .forEach { it.finishAndRemoveTask() }
        scenario?.close()
        setFixtureEnabled(false)
    }

    @Test fun bookmarkCardsAreSeparateReuseTheirIdAndNeverReplaceTheAppLauncher() = runBlocking {
        val browser = instrumentation.context.packageName
        val first = BookmarkItem(title = "First window", url = "https://example.invalid/shared",
            browserPackage = browser, selectedIconSource = IconSourceMode.GENERATED)
        val second = BookmarkItem(title = "Second window", url = first.url,
            browserPackage = browser, selectedIconSource = IconSourceMode.GENERATED)
        val repository = application.container.repository
        repository.save(first)
        repository.save(second)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val main = collection()

        // Reproduce a pre-upgrade mixed stack: a tab left directly above MainActivity.
        opened.clear()
        instrumentation.runOnMainSync {
            CustomTabsIntent.Builder().build().apply { intent.setPackage(browser) }
                .launchUrl(main, Uri.parse(first.url))
        }
        assertEquals(main.taskId, browserTask(first))
        home()
        assertEquals(main.taskId, collection().taskId)

        val firstTask = open(main, first)
        assertNotEquals(main.taskId, firstTask)
        assertEquals(BookmarkWindowActivity::class.java.name,
            manager.appTasks.first { it.taskInfo?.taskId == firstTask }.taskInfo!!.baseIntent.component?.className)

        home()
        val restoredMain = collection()
        assertEquals(main.taskId, restoredMain.taskId)
        val secondTask = open(restoredMain, second)
        assertNotEquals(firstTask, secondTask)

        // Reopening the same bookmark uses the same card and freshly saved URL/browser metadata.
        val edited = first.copy(title = "Edited window", url = "https://example.invalid/updated")
        repository.save(edited)
        assertEquals(firstTask, open(collection(), edited))
        assertEquals(2, manager.appTasks.count {
            it.taskInfo?.baseIntent?.component?.className == BookmarkWindowActivity::class.java.name
        })

        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        await("Closing a tab should also close its empty bookmark root") {
            manager.appTasks.none { it.taskInfo?.taskId == firstTask }
        }
        assertTrue(manager.appTasks.any { it.taskInfo?.taskId == secondTask })
        assertEquals(main.taskId, collection().taskId)
    }

    @Test
    fun minimizedFixtureSurvivesAndRestoresIntoItsBookmarkTask() = runBlocking {
        val bookmark = BookmarkItem(title = "Floating fixture", url = "https://example.invalid/preserved",
            browserPackage = instrumentation.context.packageName, selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(bookmark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val main = collection()
        val task = open(main, bookmark)
        await("The fixture must be ready to minimize") {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(bookmark.url)
                ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        assertMinimizedAndRestore(task, main.taskId, recreateHost = true)
        assertEquals(task, browserTask(bookmark))
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        await("A finished browser should remove its bookmark task") { manager.appTasks.none { it.taskInfo?.taskId == task } }
    }

    @Test
    fun chromeNativeMinimizeRestoresWithoutLosingTheBookmarkHost() = runBlocking {
        // M124 caches feature values for the next process. Fully initialize Chrome once with
        // the CI flags before inspecting its native CCT toolbar in a fresh browser process.
        shell("am start -W -a android.intent.action.VIEW -d chrome://version -p com.android.chrome")
        val version = context.packageManager.getPackageInfo("com.android.chrome", 0).versionName!!
        await("Chrome must initialize and cache its native feature flags") {
            instrumentation.uiAutomation.windows.mapNotNull { it.root }
                .any { it.findAccessibilityNodeInfosByText(version).isNotEmpty() }
        }
        shell("am force-stop com.android.chrome")
        val bookmark = BookmarkItem(title = "Chrome floating window", url = "https://example.com/#webshortcuts-pip",
            browserPackage = "com.android.chrome", selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(bookmark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val main = collection()
        instrumentation.runOnMainSync { assertTrue(BookmarkWindows.open(main, bookmark).opened) }
        var task = -1
        await("Chrome must open above a bookmark host") {
            task = manager.appTasks.firstOrNull { it.taskInfo?.topActivity?.packageName == "com.android.chrome" }
                ?.taskInfo?.taskId ?: -1
            task >= 0
        }
        clickView("com.android.chrome:id/custom_tabs_minimize_button")
        assertMinimizedAndRestore(task, main.taskId)
        await("Chrome must restore to its original bookmark task") {
            manager.appTasks.any { it.taskInfo?.taskId == task && it.taskInfo?.topActivity?.packageName == "com.android.chrome" }
        }
        clickView("com.android.chrome:id/close_button")
        await("Closing Chrome must remove its empty bookmark task") { manager.appTasks.none { it.taskInfo?.taskId == task } }
    }

    private fun assertMinimizedAndRestore(task: Int, mainTask: Int, recreateHost: Boolean = false) {
        await("The browser must enter real Android picture-in-picture") {
            instrumentation.uiAutomation.windows.any { it.isInPictureInPictureMode }
        }
        assertTrue("Minimization must preserve the bookmark root", manager.appTasks.any { it.taskInfo?.taskId == task })
        assertEquals(mainTask, collection().taskId)
        assertTrue("Opening the app must preserve the floating browser", instrumentation.uiAutomation.windows.any { it.isInPictureInPictureMode })
        if (recreateHost) {
            var host: BookmarkWindowActivity? = null
            await("The host must stop behind the collection before recreation") {
                instrumentation.runOnMainSync {
                    host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                        .filterIsInstance<BookmarkWindowActivity>().firstOrNull { it.taskId == task }
                }
                host != null
            }
            instrumentation.runOnMainSync { host!!.recreate() }
        }
        val bounds = Rect()
        instrumentation.uiAutomation.windows.first { it.isInPictureInPictureMode }.getBoundsInScreen(bounds)
        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
        clickView("com.android.systemui:id/expand_button")
        await("The browser must leave picture-in-picture after expanding") {
            instrumentation.uiAutomation.windows.none { it.isInPictureInPictureMode }
        }
    }

    private fun clickView(id: String) {
        try {
            await("Expected browser/system control: $id") {
                instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
                    .flatMap { it.findAccessibilityNodeInfosByViewId(id).asSequence() }
                    .any { node ->
                        var target: AccessibilityNodeInfo? = node
                        while (target != null && !target.isClickable) target = target.parent
                        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    }
            }
        } catch (failure: AssertionError) {
            val file = File(context.getExternalFilesDir(null), "screenshots/pip-${id.substringAfterLast('/')}.png")
            file.parentFile!!.mkdirs()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            fun describe(node: AccessibilityNodeInfo, depth: Int = 0): String = buildString {
                appendLine("${node.viewIdResourceName}: ${node.className} text=${node.text} desc=${node.contentDescription} clickable=${node.isClickable}")
                if (depth < 7) for (index in 0 until node.childCount) node.getChild(index)?.let { append(describe(it, depth + 1)) }
            }
            throw AssertionError("${failure.message}\n" + instrumentation.uiAutomation.windows
                .mapNotNull { it.root }.joinToString("\n") { describe(it) }, failure)
        }
    }

    private fun collection(): MainActivity {
        // Model an external launcher, not a background app trying to start itself. Wait for the
        // system command so a pending Home transition cannot cover the activity afterwards.
        val component = ComponentName(context, MainActivity::class.java).flattenToString()
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n $component")
        var activity: MainActivity? = null
        await("The application launcher should resume the collection, not a previous web page") {
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
            }
            activity != null
        }
        return activity!!
    }

    private fun home() {
        // Querying the Home package from the target is subject to package visibility. Let the
        // system resolve and wait for Home itself, just as for the external launcher above.
        shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME")
    }

    private fun shell(command: String) {
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes().toString(Charsets.UTF_8) }
        assertFalse(output, output.contains("Error:"))
    }

    private fun open(activity: MainActivity, bookmark: BookmarkItem): Int {
        opened.clear()
        instrumentation.runOnMainSync { assertTrue(BookmarkWindows.open(activity, bookmark).opened) }
        return browserTask(bookmark)
    }

    private fun browserTask(bookmark: BookmarkItem): Int {
        val event = opened.poll(15, TimeUnit.SECONDS) ?: error("The browser window did not resume")
        assertEquals(bookmark.url, event.getStringExtra("url"))
        assertTrue("The fixture must exercise Custom Tabs", event.getBooleanExtra("customTab", false))
        return event.getIntExtra("taskId", -1).also { assertTrue(it >= 0) }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail(message)
    }

    private fun setFixtureEnabled(enabled: Boolean) {
        instrumentation.uiAutomation.adoptShellPermissionIdentity()
        try {
            for (type in listOf(WindowBrowserFixture::class.java, WindowBrowserServiceFixture::class.java)) {
                context.packageManager.setComponentEnabledSetting(ComponentName(instrumentation.context, type),
                    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
            }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    private fun fixtureDiscovery(): String {
        val pkg = instrumentation.context.packageName
        val pm = context.packageManager
        val activities = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg), PackageManager.GET_RESOLVED_FILTER)
            .map { "${it.activityInfo?.name}: enabled=${it.activityInfo?.enabled}, exported=${it.activityInfo?.exported}, authorities=${it.filter?.countDataAuthorities()}" }
        val services = pm.queryIntentServices(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION).setPackage(pkg), 0)
            .map { "${it.serviceInfo?.name}: enabled=${it.serviceInfo?.enabled}, exported=${it.serviceInfo?.exported}" }
        val globalActivities = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.GET_RESOLVED_FILTER).map { it.activityInfo?.packageName }
        val globalServices = pm.queryIntentServices(Intent(CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION), 0)
            .map { it.serviceInfo?.packageName }
        val states = listOf(WindowBrowserFixture::class.java, WindowBrowserServiceFixture::class.java)
            .associate { it.simpleName to pm.getComponentEnabledSetting(ComponentName(instrumentation.context, it)) }
        return "caller=${context.packageName}, fixture=$pkg, states=$states, activities=$activities, services=$services, globalActivities=$globalActivities, globalServices=$globalServices, catalog=${BrowserCatalog.list(context)}"
    }
}
