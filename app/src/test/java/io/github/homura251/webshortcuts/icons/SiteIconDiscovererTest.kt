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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

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
                "/pwa/large.png" -> png(512)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())
        assertNotNull(result)
        assertTrue(result!!.sourceUrl.endsWith("/pwa/large.png"))
        assertEquals(3, server.requestCount)
    }

    @Test fun lyingDeclaredSizeDoesNotPreventTryingAHigherResolutionSource() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head>
                    <link rel="manifest" href="/app.json">
                    <link rel="icon" sizes="512x512" href="/large.png">
                    </head>""")
                "/app.json" -> text("application/manifest+json", """{"icons":[{"src":"tiny.png","sizes":"1024x1024"}]}""")
                "/tiny.png" -> png(32)
                "/large.png" -> png(512)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())!!
        try {
            assertTrue(result.sourceUrl.endsWith("/large.png"))
            assertTrue(result.description.endsWith("512×512"))
            assertEquals(listOf("/site", "/app.json", "/tiny.png", "/large.png"), recordedPaths(4))
            assertEquals(4, server.requestCount)
        } finally { result.bitmap.recycle() }
    }

    @Test fun descriptionUsesActualDimensionsAndLowResolutionIsOnlyAFallback() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head>
                    <link rel="icon" sizes="512x512" href="/lying.png">
                    <link rel="apple-touch-icon" href="/actual.png">
                    </head>""")
                "/lying.png" -> png(48)
                "/actual.png" -> png(192)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())!!
        try {
            assertTrue(result.sourceUrl.endsWith("/actual.png"))
            assertEquals("Apple Touch Icon · 192×192", result.description)
        } finally { result.bitmap.recycle() }
    }

    @Test fun icoQualityUsesTheFrameActuallyDecodedWhenItsLargestFrameIsBroken() = runBlocking {
        val smallPng = pngBytes(64)
        val headerSize = 6 + 2 * 16
        val ico = ByteBuffer.allocate(headerSize + 1 + smallPng.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(1).putShort(2)
            .put(0).put(0).put(0).put(0).putShort(1).putShort(32).putInt(1).putInt(headerSize)
            .put(64).put(64).put(0).put(0).putShort(1).putShort(32).putInt(smallPng.size).putInt(headerSize + 1)
            .put(0).put(smallPng).array()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head>
                    <link rel="icon" sizes="512x512" href="/broken.ico">
                    <link rel="icon" sizes="192x192" href="/good.png">
                    </head>""")
                "/broken.ico" -> MockResponse().setHeader("Content-Type", "image/x-icon").setBody(Buffer().write(ico))
                "/good.png" -> png(192)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())!!
        try {
            assertTrue(result.sourceUrl.endsWith("/good.png"))
            assertTrue(result.description.endsWith("192×192"))
        } finally { result.bitmap.recycle() }
    }

    @Test fun svgIsAHighResolutionSourceRegardlessOfItsViewBoxSize() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head>
                    <link rel="apple-touch-icon" sizes="192x192" href="/medium.png">
                    <link rel="icon" href="/vector.svg">
                    </head>""")
                "/medium.png" -> png(192)
                "/vector.svg" -> text("image/svg+xml", """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" fill="#ff0000"/></svg>""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())!!
        try {
            assertTrue(result.sourceUrl.endsWith("/vector.svg"))
            assertTrue(result.description.contains("scalable"))
            assertEquals(listOf("/site", "/medium.png", "/vector.svg"), recordedPaths(3))
            assertEquals(3, server.requestCount)
        } finally { result.bitmap.recycle() }
    }

    @Test fun totalDeadlineKeepsTheBestImageAlreadyDownloaded() = runBlocking {
        // Warm up native graphics before starting the short network deadline on slower CI hosts.
        val small = pngBytes(64)
        IconProcessor().decode(small, "image/png").recycle()
        val bounded = SiteIconDiscoverer(IconProcessor(), fetchTimeoutMillis = 2_000)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head>
                    <link rel="icon" sizes="512x512" href="/small.png">
                    <link rel="icon" sizes="192x192" href="/slow.png">
                    </head>""")
                "/small.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(small))
                "/slow.png" -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = bounded.fetch(server.url("/site").toString())!!
        try {
            assertTrue(result.sourceUrl.endsWith("/small.png"))
            assertTrue(result.description.endsWith("64×64"))
            assertFalse(result.bitmap.isRecycled)
        } finally { result.bitmap.recycle() }
    }

    @Test fun transparentHighResolutionMaskableDoesNotBlockAVisibleLogo() = runBlocking {
        val empty = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val payload = ByteArrayOutputStream().also { empty.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        empty.recycle()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head><link rel="manifest" href="/app.json"><link rel="icon" sizes="192x192" href="/visible.png"></head>""")
                "/app.json" -> text("application/manifest+json", """{"icons":[{"src":"empty.png","sizes":"512x512","purpose":"maskable"}]}""")
                "/empty.png" -> MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(payload))
                "/visible.png" -> png(192)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())!!
        try { assertTrue(result.sourceUrl.endsWith("/visible.png")) } finally { result.bitmap.recycle() }
    }

    @Test fun badDeclaredMimeFallsBackToRootFavicon() = runBlocking {
        // QueueDispatcher special-cases /favicon.ico as 404 without consuming enqueued responses.
        // Route explicitly so this tests the application's fallback, not the fixture's browser shortcut.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head><link rel="icon" sizes="192x192" href="/bad.png"></head>""")
                "/bad.png" -> text("text/html", "<html>not an image</html>")
                "/favicon.ico" -> png()
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())
        assertNotNull(result)
        assertTrue(result!!.sourceUrl.endsWith("/favicon.ico"))
        assertEquals(listOf("/site", "/bad.png", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/favicon.ico"),
            recordedPaths(server.requestCount))
    }

    @Test fun undeclaredAppleTouchIconBeatsSmallDeclaredAndRootFavicons() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head><link rel="icon" sizes="32x32" href="/tiny.png"></head>""")
                "/tiny.png" -> png(32)
                "/apple-touch-icon.png" -> png(256)
                "/favicon.ico" -> png(64)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())!!
        try {
            assertEquals(server.url("/apple-touch-icon.png").toString(), result.sourceUrl)
            assertEquals("Apple Touch Icon · 256×256", result.description)
            assertEquals(listOf("/site", "/tiny.png", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/favicon.ico"),
                recordedPaths(server.requestCount))
        } finally { result.bitmap.recycle() }
    }

    @Test fun precomposedFallbackUsesTheFinalRedirectOriginAndStopsAt512Pixels() = runBlocking {
        val destination = MockWebServer()
        destination.start()
        try {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse().setResponseCode(302).setHeader("Location", destination.url("/nested/landing?q=1"))
            }
            destination.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/nested/landing?q=1" -> text("text/html", "<head><base href='/unrelated/assets/'></head>")
                    "/apple-touch-icon-precomposed.png" -> png(512)
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val result = discoverer.fetch(server.url("/start").toString())!!
            try {
                assertEquals(destination.url("/apple-touch-icon-precomposed.png").toString(), result.sourceUrl)
                assertEquals(listOf("/start"), recordedPaths(server.requestCount))
                val paths = (1..destination.requestCount).map { destination.takeRequest(1, TimeUnit.SECONDS)?.path }
                assertEquals(listOf("/nested/landing?q=1", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png"), paths)
            } finally { result.bitmap.recycle() }
        } finally { destination.shutdown() }
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
        assertEquals(listOf("/site") + (1..9).map { "/bad$it.png" } +
            listOf("/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/favicon.ico"),
            recordedPaths(server.requestCount))
    }

    @Test fun oversizedIconSkipsToFallback() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/site" -> text("text/html", """<head><link rel="icon" sizes="192x192" href="/large.png"></head>""")
                "/large.png" -> MockResponse().setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(ByteArray(IconPolicy.MAX_IMAGE_BYTES + 1)))
                "/favicon.ico" -> png()
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = discoverer.fetch(server.url("/site").toString())
        assertNotNull(result)
        assertTrue(result!!.sourceUrl.endsWith("/favicon.ico"))
        assertEquals(listOf("/site", "/large.png", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/favicon.ico"),
            recordedPaths(server.requestCount))
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
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(302).setHeader("Location", "/loop")
        }
        assertNull(discoverer.fetch(server.url("/loop").toString()))
        // The page and each of the three root resources get at most four redirect attempts.
        val loop = listOf("/loop", "/loop", "/loop")
        assertEquals(listOf("/loop") + loop + listOf("/apple-touch-icon.png") + loop +
            listOf("/apple-touch-icon-precomposed.png") + loop + listOf("/favicon.ico") + loop,
            recordedPaths(server.requestCount))
    }

    private fun recordedPaths(count: Int): List<String?> = (1..count).map {
        server.takeRequest(1, TimeUnit.SECONDS)?.path
    }

    private fun text(type: String, body: String) = MockResponse().setHeader("Content-Type", type).setBody(body)
    private fun png(size: Int = 64): MockResponse = MockResponse().setHeader("Content-Type", "image/png")
        .setBody(Buffer().write(pngBytes(size)))

    private fun pngBytes(size: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val stream = ByteArrayOutputStream()
        try { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) } finally { bitmap.recycle() }
        return stream.toByteArray()
    }
}
