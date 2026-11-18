package io.github.homura251.webshortcuts.shortcuts

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
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
import android.graphics.Color
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
    private var browserInstance: String? = null
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
            browserPackage = browser, autoImagePath = saveIcon(IconSourceMode.AUTO, Color.RED))
        val second = BookmarkItem(title = "Second window", url = first.url,
            browserPackage = browser, selectedIconSource = IconSourceMode.CUSTOM,
            customImagePath = saveIcon(IconSourceMode.CUSTOM, Color.BLUE))
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
        assertTaskArtwork(firstTask, first)
        assertNotEquals(main.taskId, firstTask)
        assertEquals(BookmarkWindowActivity::class.java.name,
            manager.appTasks.first { it.taskInfo?.taskId == firstTask }.taskInfo!!.baseIntent.component?.className)

        home()
        val restoredMain = collection()
        assertEquals(main.taskId, restoredMain.taskId)
        val secondTask = open(restoredMain, second)
        assertNotEquals(firstTask, secondTask)
        assertTaskArtwork(secondTask, second)
        assertTaskArtwork(firstTask, first)

        // Reopening the same bookmark uses the same card and freshly saved URL/browser metadata.
        val edited = first.copy(title = "Edited window", url = "https://example.invalid/updated",
            selectedIconSource = IconSourceMode.CUSTOM, imageVersion = 2,
            customImagePath = saveIcon(IconSourceMode.CUSTOM, Color.GREEN))
        repository.save(edited)
        assertEquals(firstTask, open(collection(), edited))
        assertTaskArtwork(firstTask, edited)
        assertEquals(2, manager.appTasks.count {
            it.taskInfo?.baseIntent?.component?.className == BookmarkWindowActivity::class.java.name
        })

        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        await("Closing a tab must hide its empty bookmark card") { !hasVisibleTask(firstTask) }
        assertTrue(manager.appTasks.any { it.taskInfo?.taskId == secondTask })
        val returnedMain = collection()
        assertEquals(main.taskId, returnedMain.taskId)
        assertSame(main, returnedMain)
        assertEquals("A hidden anchor must remain reusable", firstTask, open(returnedMain, edited))
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        await("The reused empty card must hide again") { !hasVisibleTask(firstTask) }
    }

    @Test
    fun minimizedFixturePreservesItsBrowserAcrossHostRecreation() = runBlocking {
        val bookmark = BookmarkItem(title = "Floating fixture", url = "https://example.invalid/preserved",
            browserPackage = instrumentation.context.packageName, selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(bookmark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val main = collection()
        val task = open(main, bookmark)
        val instance = browserInstance
        assertNotNull(instance)
        recreateHost(task)
        assertTaskArtwork(task, bookmark)
        opened.clear()
        await("The fixture must be ready to minimize") {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(bookmark.url)
                ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        assertMinimizedAndRestore(task, main)
        // Android may retain a separate task after PiP; the actual browser instance and page
        // must survive, irrespective of the system-assigned browser task ID.
        browserTask(bookmark)
        assertEquals(instance, browserInstance)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        await("A finished browser must not expose an empty bookmark card") { !hasVisibleTask(task) }
    }

    @Test
    fun missingAutoIconUsesGeneratedTaskArtwork(): Unit = runBlocking {
        val bookmark = BookmarkItem(title = "Missing artwork", url = "https://example.invalid/missing",
            browserPackage = instrumentation.context.packageName, autoImagePath = "icons/auto/missing.png")
        application.container.repository.save(bookmark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val task = open(collection(), bookmark)
        assertTaskArtwork(task, bookmark)
        recreateHost(task)
        assertTaskArtwork(task, bookmark)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test fun shortcutMinimizationDoesNotCreateACollection() = minimizeFromHome(false)

    @Test fun shortcutMinimizationDoesNotBringBackAStoppedCollection() = minimizeFromHome(true)

    @Test fun fullBrowserFallbackRemainsInFrontWhenReusingAnOldTabHost(): Unit = runBlocking {
        val bookmark = BookmarkItem(title = "Browser fallback", url = "https://example.invalid/fallback",
            browserPackage = instrumentation.context.packageName, selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(bookmark)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        open(collection(), bookmark)
        val main = collection()
        instrumentation.uiAutomation.adoptShellPermissionIdentity()
        try {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(instrumentation.context, WindowBrowserServiceFixture::class.java),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        await("The fixture must now expose only its full browser") {
            BrowserCatalog.list(context).any {
                it.packageName == instrumentation.context.packageName && !it.supportsCustomTabs
            }
        }
        opened.clear()
        instrumentation.runOnMainSync { assertTrue(BookmarkWindows.open(main, bookmark).opened) }
        val event = opened.poll(15, TimeUnit.SECONDS) ?: error("The full browser did not open")
        assertEquals(bookmark.url, event.getStringExtra("url"))
        assertFalse(event.getBooleanExtra("customTab", true))
        await("The fallback browser must keep the foreground after hiding its old tab host") {
            manager.appTasks.filter { it.taskInfo?.baseIntent?.component?.className == BookmarkWindowActivity::class.java.name }
                .none { hasVisibleTask(it.taskInfo!!.taskId) } &&
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == instrumentation.context.packageName
        }
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun minimizeFromHome(withCollection: Boolean): Unit = runBlocking {
        if (withCollection) scenario = ActivityScenario.launch(MainActivity::class.java)
        else manager.appTasks.filter { it.taskInfo?.baseIntent?.component?.className == MainActivity::class.java.name }
            .forEach { it.finishAndRemoveTask() }
        home()
        val homePackage = shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME")
            .trim().lineSequence().last().substringBefore('/')
        val bookmark = BookmarkItem(title = "Desktop shortcut", url = "https://example.invalid/desktop",
            browserPackage = instrumentation.context.packageName, selectedIconSource = IconSourceMode.GENERATED)
        application.container.repository.save(bookmark)
        val router = ComponentName(context, ShortcutRouterActivity::class.java).flattenToString()
        shell("am start -W -a ${ShortcutIntents.ACTION_OPEN_BOOKMARK} --es ${ShortcutIntents.EXTRA_BOOKMARK_ID} ${bookmark.id} -n $router")
        val task = browserTask(bookmark)
        val instance = browserInstance
        await("The shortcut browser must minimize") {
            instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(bookmark.url)
                ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        await("The desktop must remain under the floating browser") {
            !hasVisibleTask(task) && instrumentation.uiAutomation.windows.any {
                !it.isInPictureInPictureMode && it.root?.packageName?.toString() == homePackage
            } && instrumentation.uiAutomation.windows.any { it.isInPictureInPictureMode }
        }
        instrumentation.runOnMainSync {
            assertTrue(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .none { it is MainActivity })
        }
        if (!withCollection) assertTrue(manager.appTasks.none {
            it.taskInfo?.baseIntent?.component?.className == MainActivity::class.java.name
        })
        opened.clear()
        await("The desktop's floating browser must still expand") {
            instrumentation.uiAutomation.windows.firstOrNull { it.isInPictureInPictureMode }
                ?.root?.performAction(AccessibilityNodeInfo.ACTION_EXPAND) == true
        }
        browserTask(bookmark)
        assertEquals(instance, browserInstance)
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test
    fun chromeNativeMinimizeRestoresWithoutLosingTheBookmarkHost() = runBlocking {
        val bookmark = BookmarkItem(title = "Chrome floating window", url = "https://example.com/#webshortcuts-pip",
            browserPackage = "com.android.chrome", autoImagePath = saveIcon(IconSourceMode.AUTO, Color.CYAN))
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
        recreateHost(task)
        assertTaskArtwork(task, bookmark)
        var browserWindow = -1
        await("Chrome's original browser window must be ready") {
            instrumentation.uiAutomation.rootInActiveWindow?.takeIf {
                it.findAccessibilityNodeInfosByViewId("com.android.chrome:id/custom_tabs_minimize_button").isNotEmpty()
            }?.also { browserWindow = it.windowId } != null
        }
        recordMinimization {
            clickView("com.android.chrome:id/custom_tabs_minimize_button")
            assertMinimizedAndRestore(task, main)
        }
        await("Chrome must restore its browser window") {
            instrumentation.uiAutomation.rootInActiveWindow?.let {
                it.packageName?.toString() == "com.android.chrome" && it.windowId == browserWindow
            } == true
        }
        await("Restoration must not show an empty bookmark card") { !hasVisibleTask(task) }
        clickView("com.android.chrome:id/close_button")
        await("Closing Chrome must dismiss its browser window") {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != "com.android.chrome"
        }
        assertFalse(hasVisibleTask(task))
        assertEquals(main.taskId, collection().taskId)
    }

    private fun assertMinimizedAndRestore(task: Int, main: MainActivity) {
        await("The browser must enter real Android picture-in-picture") {
            instrumentation.uiAutomation.windows.any { it.isInPictureInPictureMode }
        }
        await("Minimization must hide its empty host without closing the floating browser") { !hasVisibleTask(task) }
        await("The original collection must resume automatically, without a launcher intent") {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it === main }
            }
            resumed
        }
        assertTrue("Returning to the visible collection must suppress its extra transition",
            main.intent.flags and Intent.FLAG_ACTIVITY_NO_ANIMATION != 0)
        assertEquals(main.taskId, collection().taskId)
        await("Opening the app must preserve the floating browser after its window transition") {
            instrumentation.uiAutomation.windows.any { it.isInPictureInPictureMode }
        }
        // PiP accessibility bounds can describe the unscaled app surface. Use Android's actual
        // full-screen action instead of coordinates, which can hit the collection underneath.
        await("Android must accept the floating window's full-screen action") {
            instrumentation.uiAutomation.windows.firstOrNull { it.isInPictureInPictureMode }
                ?.root?.performAction(AccessibilityNodeInfo.ACTION_EXPAND) == true
        }
        await("The browser must leave picture-in-picture after expanding") {
            instrumentation.uiAutomation.windows.none { it.isInPictureInPictureMode }
        }
    }

    private suspend fun saveIcon(mode: IconSourceMode, color: Int): String {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            application.container.iconStore.save(java.util.UUID.randomUUID().toString(), 1, mode, bitmap)
        } finally { bitmap.recycle() }
    }

    @Suppress("DEPRECATION")
    private suspend fun assertTaskArtwork(task: Int, bookmark: BookmarkItem) {
        val expected = application.container.iconResolver.resolve(bookmark)
        try {
            await("The task must expose the latest bookmark label and artwork after loading") {
                val description = manager.appTasks.firstOrNull { it.taskInfo?.taskId == task }?.taskInfo?.taskDescription
                description?.label == bookmark.title && description.icon?.let {
                    it.getPixel(0, 0) == expected.getPixel(0, 0)
                } == true
            }
        }
        finally { expected.recycle() }
    }

    private fun recordMinimization(action: () -> Unit) {
        val scales = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
            .associateWith { shell("settings get global $it").trim() }
        val existingRecorders = shell("pidof screenrecord").trim().split(Regex("\\s+")).toSet()
        var recorder: ParcelFileDescriptor? = null
        var recorderPid: String? = null
        try {
            scales.keys.forEach { shell("settings put global $it 1") }
            val file = File(context.getExternalFilesDir(null), "screenshots/chrome-minimize.mp4")
            file.parentFile!!.mkdirs()
            file.delete()
            // UiAutomation runs an executable directly; shell operators such as & are not parsed.
            // Keep its output pipe open while screenrecord runs asynchronously in the shell UID.
            recorder = instrumentation.uiAutomation.executeShellCommand(
                "screenrecord --size 540x960 --time-limit 15 ${file.absolutePath}")
            await("Recording must capture the browser before minimizing") {
                recorderPid = shell("pidof screenrecord").trim().split(Regex("\\s+"))
                    .firstOrNull { it !in existingRecorders && it.matches(Regex("[0-9]+")) }
                recorderPid != null && file.length() > 1024
            }
            action()
        } finally {
            try {
                recorderPid?.let { shell("kill -2 $it") }
                recorder?.close()
            } finally {
                scales.forEach { (key, value) ->
                    shell(if (value == "null") "settings delete global $key" else "settings put global $key $value")
                }
            }
        }
    }

    private fun hasVisibleTask(task: Int): Boolean = manager.appTasks.any {
        val info = it.taskInfo
        info?.taskId == task && info.baseIntent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS == 0
    }

    private fun recreateHost(task: Int) {
        var host: BookmarkWindowActivity? = null
        await("The browser must cover its host before recreation") {
            instrumentation.runOnMainSync {
                host = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                    .filterIsInstance<BookmarkWindowActivity>().firstOrNull { it.taskId == task }
            }
            host != null
        }
        val original = host!!
        instrumentation.runOnMainSync { original.recreate() }
        await("The host must finish recreation without replacing the browser") {
            var recreated = false
            instrumentation.runOnMainSync {
                recreated = original.isDestroyed && ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.STOPPED).filterIsInstance<BookmarkWindowActivity>()
                    .any { it.taskId == task && it !== original }
            }
            recreated
        }
    }

    private fun clickView(id: String) {
        await("Expected browser/system control: $id") {
            instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByViewId(id).asSequence() }
                .any { node ->
                    var target: AccessibilityNodeInfo? = node
                    while (target != null && !target.isClickable) target = target.parent
                    target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }
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

    private fun shell(command: String): String {
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes().toString(Charsets.UTF_8) }
        assertFalse(output, output.contains("Error:"))
        return output
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
        browserInstance = event.getStringExtra("instanceId")
        return event.getIntExtra("taskId", -1).also { assertTrue(it >= 0) }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        val file = File(context.getExternalFilesDir(null), "screenshots/pip-timeout-${System.currentTimeMillis()}.png")
        file.parentFile!!.mkdirs()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val windows = instrumentation.uiAutomation.windows.joinToString("\n") {
            "window=${it.id}, pip=${it.isInPictureInPictureMode}, package=${it.root?.packageName}"
        }
        val tasks = manager.appTasks.joinToString("\n") { runCatching { it.taskInfo.toString() }.getOrDefault("task unavailable") }
        fail("$message\n$windows\n$tasks")
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
