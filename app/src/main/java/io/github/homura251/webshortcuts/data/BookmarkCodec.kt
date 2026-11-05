package io.github.homura251.webshortcuts.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Pure serialization, kept separate from Android I/O for deterministic migration tests. */
object BookmarkCodec {
    const val SCHEMA_VERSION = 2
    const val MAX_BOOKMARKS = 2000
    const val MAX_JSON_BYTES = 4 * 1024 * 1024
    private val iconPath = Regex("icons/(auto|custom)/[a-zA-Z0-9._-]+\\.(webp|png)")

    fun encode(items: List<BookmarkItem>): String {
        require(items.size <= MAX_BOOKMARKS) { "收藏数量已达到上限（$MAX_BOOKMARKS）" }
        require(items.map { it.id }.toSet().size == items.size) { "收藏 ID 重复" }
        val records = JSONArray()
        items.forEach { item ->
            validate(item)
            records.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("selectedIconSource", item.selectedIconSource.name)
                put("autoImagePath", item.autoImagePath ?: JSONObject.NULL)
                put("customImagePath", item.customImagePath ?: JSONObject.NULL)
                put("sourceUrl", item.sourceUrl ?: JSONObject.NULL)
                put("imageVersion", item.imageVersion)
                put("lastFetchedAt", item.lastFetchedAt ?: JSONObject.NULL)
                put("iconDescription", item.iconDescription ?: JSONObject.NULL)
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
            })
        }
        return JSONObject().put("schemaVersion", SCHEMA_VERSION).put("bookmarks", records).toString()
    }

    fun decode(json: String): List<BookmarkItem> {
        val trimmed = json.trim()
        require(trimmed.isNotEmpty()) { "收藏数据为空或已损坏" }
        val records = if (trimmed.startsWith('[')) JSONArray(trimmed) else {
            val root = JSONObject(trimmed)
            val version = root.optInt("schemaVersion", 1)
            require(version in 1..SCHEMA_VERSION) { "收藏来自较新版本，请先更新应用" }
            root.getJSONArray("bookmarks")
        }
        require(records.length() <= MAX_BOOKMARKS) { "收藏数量超过支持的上限" }
        val seen = mutableSetOf<String>()
        return (0 until records.length()).map { index ->
            val record = records.getJSONObject(index)
            val url = UrlNormalizer.normalize(record.getString("url"))
            val id = record.nullableString("id") ?: record.nullableString("uuid")
                // Deterministic legacy migration avoids replacing shortcut IDs on every app launch.
                ?: UUID.nameUUIDFromBytes("webshortcuts:$index:$url".toByteArray(Charsets.UTF_8)).toString()
            require(seen.add(id)) { "收藏 ID 重复，已停止读取以保护原始数据" }
            val modeName = record.nullableString("selectedIconSource")
            val mode = if (modeName == null) IconSourceMode.GENERATED
                else IconSourceMode.entries.firstOrNull { it.name == modeName } ?: IconSourceMode.AUTO
            BookmarkItem(
                id = id,
                title = record.nullableString("title") ?: record.nullableString("name")
                    ?: URIHostFallback.host(url),
                url = url,
                selectedIconSource = mode,
                autoImagePath = record.nullableString("autoImagePath")?.takeIf(::isSafeIconPath),
                customImagePath = record.nullableString("customImagePath")?.takeIf(::isSafeIconPath),
                sourceUrl = record.nullableString("sourceUrl")?.let { runCatching { UrlNormalizer.normalize(it) }.getOrNull() },
                imageVersion = record.optInt("imageVersion", 1).coerceAtLeast(1),
                lastFetchedAt = if (record.isNull("lastFetchedAt")) null else record.optLong("lastFetchedAt").takeIf { it > 0 },
                iconDescription = record.nullableString("iconDescription")?.take(256),
                createdAt = record.optLong("createdAt", 0).coerceAtLeast(0),
                updatedAt = record.optLong("updatedAt", record.optLong("createdAt", 0)).coerceAtLeast(0),
            ).also(::validate)
        }
    }

    fun validate(item: BookmarkItem) {
        require(item.id.isNotBlank() && item.id.length <= 128 && item.id.none { it.isISOControl() }) { "收藏 ID 无效" }
        require(item.title.isNotBlank() && item.title.length <= 160 && item.title.none { it.isISOControl() }) {
            "名称应为 1 至 160 个字符，且不能包含换行"
        }
        UrlNormalizer.normalize(item.url)
        require(item.imageVersion >= 1) { "图标版本无效" }
        require(item.autoImagePath == null || isSafeIconPath(item.autoImagePath)) { "自动图标路径无效" }
        require(item.customImagePath == null || isSafeIconPath(item.customImagePath)) { "自定义图标路径无效" }
    }

    private fun isSafeIconPath(value: String) = ".." !in value && iconPath.matches(value)
    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private object URIHostFallback {
        fun host(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: "网站"
    }
}
