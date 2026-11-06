package io.github.homura251.webshortcuts.data

import java.net.IDN
import java.net.URI
import java.util.Locale

/** Browser destinations and icon requests share the same deliberately narrow URL policy. */
object UrlNormalizer {
    private val explicitScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
    private val unsafeScheme = Regex("^(javascript|data|file|content|intent|about|blob|mailto|tel|ftp):", RegexOption.IGNORE_CASE)
    private val hostname = Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        val input = raw.trim()
        require(input.isNotEmpty()) { "请输入网站地址" }
        require(input.length <= 8192) { "网址过长，请使用较短的地址" }
        require(input.none { it.isWhitespace() || it.isISOControl() } && '\\' !in input) {
            "网址不能包含空格、换行或反斜杠"
        }
        require(!unsafeScheme.containsMatchIn(input)) { "只支持 HTTP 或 HTTPS 网站地址" }
        val prepared = when {
            input.startsWith("//") -> "https:$input"
            explicitScheme.containsMatchIn(input) -> input
            else -> "https://$input"
        }
        val uri = try { URI(prepared) } catch (_: Exception) {
            throw IllegalArgumentException("网址格式不正确，请检查域名和特殊字符")
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https") { "只支持 HTTP 或 HTTPS 网站地址" }
        val authority = uri.rawAuthority ?: throw IllegalArgumentException("请输入完整的网站域名")
        require('@' !in authority && '%' !in authority) { "网址不能包含登录凭据或编码后的域名" }
        require(authority.isNotEmpty()) { "请输入完整的网站域名" }
        val parsed = parseAuthority(authority)
        val host = normalizeHost(parsed.first)
        val port = parsed.second
        val normalizedPort = if ((scheme == "https" && port == 443) || (scheme == "http" && port == 80)) null else port
        return buildString {
            append(scheme).append("://").append(host)
            if (normalizedPort != null) append(':').append(normalizedPort)
            append(uri.rawPath.orEmpty().ifEmpty { "/" })
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    private fun parseAuthority(authority: String): Pair<String, Int?> {
        if (authority.startsWith('[')) {
            val closing = authority.indexOf(']')
            require(closing > 1) { "IPv6 地址格式不正确" }
            val host = authority.substring(0, closing + 1)
            val remainder = authority.substring(closing + 1)
            require(remainder.isEmpty() || remainder.startsWith(':')) { "IPv6 地址格式不正确" }
            // java.net.URI validates IPv6 syntax without DNS/network access.
            require(runCatching { URI("https://$host/").host != null }.getOrDefault(false)) { "IPv6 地址格式不正确" }
            return host to if (remainder.isEmpty()) null else parsePort(remainder.substring(1))
        }
        require(authority.count { it == ':' } <= 1 && '[' !in authority && ']' !in authority) {
            "IPv6 地址请放在方括号内，例如 https://[::1]"
        }
        val separator = authority.lastIndexOf(':')
        return if (separator < 0) authority to null
        else authority.substring(0, separator) to parsePort(authority.substring(separator + 1))
    }

    private fun parsePort(value: String): Int {
        require(value.isNotEmpty() && value.all { it in '0'..'9' }) { "端口应为 1 到 65535 的数字" }
        return value.toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw IllegalArgumentException("端口应为 1 到 65535 的数字")
    }

    private fun normalizeHost(rawHost: String): String {
        require(rawHost.isNotBlank()) { "请输入完整的网站域名" }
        if (rawHost.startsWith('[')) return rawHost.lowercase(Locale.ROOT)
        val host = try { IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT) }
        catch (_: Exception) { throw IllegalArgumentException("网站域名格式不正确") }
        val withoutTrailingDot = host.removeSuffix(".")
        require(withoutTrailingDot.length in 1..253 && withoutTrailingDot.split('.').all {
            it.length in 1..63 && hostname.matches(it)
        }) { "网站域名格式不正确" }
        if (withoutTrailingDot.all { it.isDigit() || it == '.' }) {
            val octets = withoutTrailingDot.split('.')
            require(octets.size == 4 && octets.all {
                it.toIntOrNull()?.let { n -> n in 0..255 } == true && (it.length == 1 || !it.startsWith('0'))
            }) { "IPv4 地址格式不正确" }
        }
        return host
    }
}
