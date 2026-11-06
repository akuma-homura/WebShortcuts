package io.github.homura251.webshortcuts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlNormalizerTest {
    @Test fun `adds https and normalizes host while preserving path query and fragment`() {
        assertEquals("https://example.com/path?q=A%20B#part", UrlNormalizer.normalize("  EXAMPLE.com/path?q=A%20B#part  "))
    }

    @Test fun `supports intranet ports IDN IPv4 and bracketed IPv6`() {
        val cases = mapOf(
            "intranet:8080/app" to "https://intranet:8080/app",
            "http://192.168.1.20:8000" to "http://192.168.1.20:8000/",
            "https://[::1]:443" to "https://[::1]/",
            "http://[2001:DB8::1]:8080/a" to "http://[2001:db8::1]:8080/a",
            "https://例子.测试/你好" to "https://xn--fsqu00a.xn--0zwm56d/你好",
            "//example.com" to "https://example.com/",
            "HTTP://LOCALHOST:80" to "http://localhost/",
            "https://example.com." to "https://example.com./",
        )
        cases.forEach { (input, expected) -> assertEquals(input, expected, UrlNormalizer.normalize(input)) }
    }

    @Test fun `rejects non web schemes credentials malformed host and unsafe characters`() {
        listOf(
            "", "javascript:alert(1)", "data:text/html,x", "file:///etc/passwd",
            "intent://app", "ftp://example.com", "mailto:a@example.com", "content://image/1",
            "https://user:password@example.com", "https://a%40b.example", "https://example.com\\@evil.com",
            "https://example.com/a b", "https://example.com/a\nb", "https:///path", "https://-host.example",
            "https://example..com", "https://[::1", "http://256.1.2.3", "http://01.2.3.4", "http://1.2.3",
            "https://example.com:", "https://example.com:0", "https://example.com:65536", "https://example.com:-1",
            "https://example.com:port", "https://example.com:999999999999999", "https://2001:db8::1/",
        ).forEach { input ->
            assertThrows("Should reject: $input", IllegalArgumentException::class.java) { UrlNormalizer.normalize(input) }
        }
    }

    @Test fun `does not convert secure domains to cleartext or drop encoded paths`() {
        assertEquals("https://example.com/a%2Fb?next=https%3A%2F%2Fexample.org", UrlNormalizer.normalize("example.com/a%2Fb?next=https%3A%2F%2Fexample.org"))
        assertEquals("http://example.com/", UrlNormalizer.normalize("http://example.com"))
    }
}
