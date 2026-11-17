package io.github.homura251.webshortcuts.shortcuts

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
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
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        assertEquals(main.taskId, collection().taskId)

        val firstTask = open(main, first)
        assertNotEquals(main.taskId, firstTask)
        assertEquals(BookmarkWindowActivity::class.java.name,
            manager.appTasks.first { it.taskInfo?.taskId == firstTask }.taskInfo!!.baseIntent.component?.className)

        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
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

    private fun collection(): MainActivity {
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
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
