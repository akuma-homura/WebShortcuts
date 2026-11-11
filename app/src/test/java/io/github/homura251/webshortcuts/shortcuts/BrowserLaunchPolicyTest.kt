package io.github.homura251.webshortcuts.shortcuts

import org.junit.Assert.*
import org.junit.Test

class BrowserLaunchPolicyTest {
    private val chrome = BrowserOption("com.android.chrome", "Chrome", true)
    private val beta = BrowserOption("com.chrome.beta", "Chrome Beta", true)
    private val firefox = BrowserOption("org.mozilla.firefox", "Firefox", true)
    private val ordinary = BrowserOption("org.example.browser", "Browser", false)

    @Test fun explicitChromeBetaOrFirefoxUsesOnlyThatProfile() {
        val available = listOf(chrome, beta, firefox, ordinary)
        assertEquals(listOf(beta), BrowserLaunchPolicy.candidates(beta.packageName, available))
        assertEquals(listOf(firefox), BrowserLaunchPolicy.candidates(firefox.packageName, available))
    }

    @Test fun missingSelectedBrowserNeverFallsBackToAnInstalledAccount() {
        assertTrue(BrowserLaunchPolicy.candidates(beta.packageName, listOf(chrome, firefox)).isEmpty())
        assertTrue(BrowserLaunchPolicy.candidates("org.removed.browser", listOf(chrome, ordinary)).isEmpty())
    }

    @Test fun ordinaryExplicitBrowserRemainsExplicitEvenWithCustomTabsAvailable() {
        assertEquals(listOf(ordinary), BrowserLaunchPolicy.candidates(ordinary.packageName, listOf(chrome, ordinary)))
    }

    @Test fun automaticModePrefersCustomTabsAndPreservesChromePriority() {
        assertEquals(listOf(chrome, beta, firefox, ordinary),
            BrowserLaunchPolicy.candidates(null, listOf(ordinary, firefox, beta, chrome)))
        assertEquals(listOf(firefox, ordinary), BrowserLaunchPolicy.candidates(null, listOf(ordinary, firefox)))
    }
}
