package io.github.homura251.webshortcuts.icons

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SiteIconDiscovererTest {
    private lateinit var server: MockWebServer
    private val discoverer = SiteIconDiscoverer(IconProcessor())

    @Before fun start() { server = MockWebServer().also { it.start() } }
    @After fun close() { server.shutdown() }

    @Test fun manifestHighestResolutionIsFetchedBeforeAppleIcon() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head><link rel="manifest" href="/pwa/app.json"><link rel="apple-touch-icon" href="/apple.png"></head>""")
                "/pwa/app.json" -> text("application/manifest+json", """{"icons":[{"src":"small.png","sizes":"192x192"},{"src":"large.png","sizes":"512x512"}]}""")
                "/pwa/large.png" -> png()
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())
        assertNotNull(result)
        assertTrue(result!!.sourceUrl.endsWith("/pwa/large.png"))
        assertEquals(3, server.requestCount)
    }

    @Test fun badDeclaredMimeFallsBackToRootFavicon() = runBlocking {
        server.enqueue(text("text/html", """<head><link rel="icon" sizes="192x192" href="/bad.png"></head>"""))
        server.enqueue(text("text/html", "<html>not an image</html>"))
        server.enqueue(png())
        val result = discoverer.fetch(server.url("/site").toString())
        assertTrue(result!!.sourceUrl.endsWith("/favicon.ico"))
        assertEquals(3, server.requestCount)
    }

    @Test fun manyBrokenDeclarationsStillReachRootFaviconWithinCandidateBudget() = runBlocking {
        val links = (1..20).joinToString("") { "<link rel='icon' sizes='192x192' href='/bad$it.png'>" }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", "<head>$links</head>")
                "/favicon.ico" -> png()
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())
        assertNotNull(result)
        assertTrue(result!!.sourceUrl.endsWith("/favicon.ico"))
        assertEquals(13, server.requestCount) // The HTML plus at most twelve candidate attempts.
    }

    @Test fun oversizedIconSkipsToFallback() = runBlocking {
        server.enqueue(text("text/html", """<head><link rel="icon" sizes="192x192" href="/large.png"></head>"""))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png")
            .setBody(Buffer().write(ByteArray(IconPolicy.MAX_IMAGE_BYTES + 1))))
        server.enqueue(png())
        val result = discoverer.fetch(server.url("/site").toString())
        assertTrue(result!!.sourceUrl.endsWith("/favicon.ico"))
    }

    @Test fun callerCancellationStopsAnUnresponsiveRequest() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val started = System.nanoTime()
        try {
            withTimeout(250) { discoverer.fetch(server.url("/site").toString()) }
            fail("Expected caller timeout")
        } catch (_: TimeoutCancellationException) {
            assertTrue((System.nanoTime() - started) / 1_000_000 < 2500)
        }
    }

    @Test fun redirectLoopsAreBounded() = runBlocking {
        repeat(5) { server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/loop")) }
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(discoverer.fetch(server.url("/loop").toString()))
        assertEquals(6, server.requestCount)
    }

    private fun text(type: String, body: String) = MockResponse().setHeader("Content-Type", type).setBody(body)
    private fun png(): MockResponse {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(stream.toByteArray()))
    }
}
