package io.github.homura251.webshortcuts

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.homura251.webshortcuts.data.BookmarkItem
import io.github.homura251.webshortcuts.data.IconSourceMode
import io.github.homura251.webshortcuts.data.UrlNormalizer
import io.github.homura251.webshortcuts.icons.IconFetchResult
import io.github.homura251.webshortcuts.icons.ImportedIcon
import io.github.homura251.webshortcuts.ui.EditorUiState
import io.github.homura251.webshortcuts.ui.HomeUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Owns editor lifetime and guards late network results against URL/mode/editor changes. */
class MainViewModel(application: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(application) {
    private val container = (application as WebShortcutsApplication).container
    private val repository = container.repository
    private val drafts = EditorDraftStore(application)
    private val mutableState = MutableStateFlow(HomeUiState())
    val state = mutableState.asStateFlow()
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private var original: BookmarkItem? = null
    private var autoImage: IconFetchResult? = null
    private var autoImageUrl: String? = null
    private var customImage: Bitmap? = null
    private var importedVariants: ImportedIcon? = null
    private var customDraftName: String? = null
    private var lastAttemptedUrl: String? = null
    private var fetchJob: Job? = null
    private var previewJob: Job? = null
    private var importJob: Job? = null
    private var editorEpoch = 0
    private var fetchEpoch = 0
    private var importEpoch = 0
    private var previewEpoch = 0
    private val refreshingIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            repository.bookmarks.collect { items -> mutableState.update { it.copy(bookmarks = items) } }
        }
        viewModelScope.launch {
            repository.loadError.collect { error -> mutableState.update { it.copy(error = error) } }
        }
        viewModelScope.launch {
            try {
                repository.load()
                if (repository.loadError.value == null) {
                    restoreEditor()
                    container.mutationMutex.withLock { cleanUnusedIcons() }
                }
                drafts.prune(customDraftName)
                mutableState.update { it.copy(loading = false) }
            } catch (error: Exception) {
                rethrowCancellation(error)
                mutableState.update { it.copy(loading = false, error = "无法读取本地收藏，原文件已保留。请重新打开应用重试。") }
            }
        }
    }

    fun setQuery(value: String) { mutableState.update { it.copy(query = value) } }

    fun add(url: String = "", title: String = "") {
        if (mutableState.value.loading) return
        if (mutableState.value.error != null) {
            notify("本地数据尚未成功读取，请重新打开应用后重试")
            return
        }
        if (mutableState.value.editor?.dirty == true) {
            notify("请先保存或关闭正在编辑的收藏")
            return
        }
        beginEditor(null, EditorUiState(id = UUID.randomUUID().toString(), isNew = true, title = title, url = url, dirty = url.isNotBlank()))
        if (url.isNotBlank()) onUrlFocusLost()
    }

    fun edit(item: BookmarkItem) {
        beginEditor(item, EditorUiState(
            id = item.id, isNew = false, title = item.title, url = item.url,
            mode = item.selectedIconSource, sourceLabel = item.iconDescription,
            hasCustomImage = item.customImagePath != null,
        ))
    }

    private fun beginEditor(item: BookmarkItem?, editor: EditorUiState) {
        cancelEditorJobs()
        original = item
        autoImage = null
        autoImageUrl = null
        customImage = null
        importedVariants = null
        customDraftName = null
        lastAttemptedUrl = null
        mutableState.update { it.copy(editor = editor) }
        persistEditor()
        updatePreview()
    }

    fun dismissEditor() {
        if (mutableState.value.editor?.saving == true) return
        closeEditor()
    }

    private fun closeEditor() {
        val discarded = customDraftName
        cancelEditorJobs()
        original = null
        autoImage = null
        customImage = null
        importedVariants = null
        customDraftName = null
        mutableState.update { it.copy(editor = null) }
        DRAFT_KEYS.forEach { savedState.remove<Any?>(it) }
        viewModelScope.launch { drafts.delete(discarded) }
    }

    fun setTitle(value: String) {
        if (mutableState.value.editor?.saving == true) return
        changeEditor { it.copy(title = value, titleError = null, dirty = true) }
        updatePreview()
    }

    fun setUrl(value: String) {
        if (mutableState.value.editor?.saving == true) return
        fetchJob?.cancel()
        fetchEpoch++
        lastAttemptedUrl = null
        changeEditor { it.copy(url = value, urlError = null, loading = importJob?.isActive == true, error = null, dirty = true) }
        updatePreview()
    }

    fun setMode(value: IconSourceMode) {
        if (mutableState.value.editor?.saving == true || importJob?.isActive == true) return
        fetchJob?.cancel()
        fetchEpoch++
        changeEditor { it.copy(mode = value, loading = false, error = null, dirty = true) }
        updatePreview()
        if (value == IconSourceMode.AUTO && !hasAutoImageForCurrentUrl()) fetchIcon(force = true)
    }

    fun onUrlFocusLost() {
        val editor = mutableState.value.editor ?: return
        if (editor.saving || editor.url.isBlank()) return
        val normalized = normalizeOrShowError(editor.url) ?: return
        changeEditor { current -> current.copy(
            url = normalized,
            title = current.title.ifBlank { Uri.parse(normalized).host.orEmpty().removePrefix("www.") },
            urlError = null,
        ) }
        updatePreview()
        if (editor.mode == IconSourceMode.AUTO && !hasAutoImageForCurrentUrl()) fetchIcon(force = false)
    }

    fun refetchIcon() = fetchIcon(force = true)

    private fun fetchIcon(force: Boolean) {
        val editor = mutableState.value.editor ?: return
        if (editor.mode != IconSourceMode.AUTO || editor.saving || importJob?.isActive == true) return
        val target = normalizeOrShowError(editor.url) ?: return
        if (!force && lastAttemptedUrl == target) return
        fetchJob?.cancel()
        lastAttemptedUrl = target
        val epoch = editorEpoch
        val requestEpoch = ++fetchEpoch
        changeEditor { it.copy(loading = true, error = null, urlError = null) }
        fetchJob = viewModelScope.launch {
            try {
                val result = container.siteIconDiscoverer.fetch(target)
                if (!isCurrentRequest(epoch, requestEpoch, target)) return@launch
                if (result != null) {
                    autoImage = result
                    autoImageUrl = target
                    changeEditor { it.copy(loading = false, sourceLabel = result.description, error = null, dirty = true) }
                } else {
                    val cached = hasAutoImageForCurrentUrl()
                    changeEditor { it.copy(loading = false, error = if (cached) "暂未获取到新图标，已保留缓存图标" else "未能获取高清图标，已使用默认字母图标") }
                }
                updatePreview()
            } catch (error: Exception) {
                rethrowCancellation(error)
                if (isCurrentRequest(epoch, requestEpoch, target)) {
                    changeEditor { it.copy(loading = false, error = "未能获取高清图标，已保留可用图标或使用字母图标") }
                    updatePreview()
                }
            }
        }
    }

    fun importImage(uri: Uri) {
        val editor = mutableState.value.editor ?: return
        if (editor.saving) return
        fetchJob?.cancel()
        fetchEpoch++
        importJob?.cancel()
        val epoch = editorEpoch
        val requestEpoch = ++importEpoch
        changeEditor { it.copy(loading = true, error = null) }
        importJob = viewModelScope.launch {
            try {
                val variants = container.iconImporter.importVariants(uri)
                val bitmap = variants.fitted
                if (epoch != editorEpoch || requestEpoch != importEpoch) return@launch
                val name = drafts.write(editor.id, bitmap)
                if (epoch != editorEpoch || requestEpoch != importEpoch) { drafts.delete(name); return@launch }
                val previousName = customDraftName
                customImage = bitmap
                importedVariants = variants
                customDraftName = name
                changeEditor { it.copy(mode = IconSourceMode.CUSTOM, loading = false, error = null, hasCustomImage = true, dirty = true, canCrop = true, centerCrop = false) }
                updatePreview()
                drafts.delete(previousName)
            } catch (error: Exception) {
                rethrowCancellation(error)
                if (epoch == editorEpoch && requestEpoch == importEpoch) changeEditor { it.copy(loading = false, error = "无法导入这张图片。请选择不超过 5 MB、边长不超过 4096 像素的 PNG、JPEG 或 WebP 图片。") }
            }
        }
    }

    fun setCenterCrop(crop: Boolean) {
        val editor = mutableState.value.editor ?: return
        val variants = importedVariants ?: return
        if (editor.saving || importJob?.isActive == true || editor.centerCrop == crop) return
        val bitmap = if (crop) variants.cropped else variants.fitted
        val epoch = editorEpoch
        val requestEpoch = ++importEpoch
        changeEditor { it.copy(loading = true) }
        importJob = viewModelScope.launch {
            try {
                val name = drafts.write(editor.id, bitmap)
                if (epoch != editorEpoch || requestEpoch != importEpoch) { drafts.delete(name); return@launch }
                val previousName = customDraftName
                customImage = bitmap
                customDraftName = name
                changeEditor { it.copy(centerCrop = crop, loading = false, dirty = true) }
                updatePreview()
                drafts.delete(previousName)
            } catch (error: Exception) {
                rethrowCancellation(error)
                if (epoch == editorEpoch && requestEpoch == importEpoch) changeEditor { it.copy(loading = false, error = "无法保存裁切预览，请重试") }
            }
        }
    }

    fun save() {
        val editor = mutableState.value.editor ?: return
        if (editor.saving) return
        if (importJob?.isActive == true) { notify("请等待图片处理完成后保存"); return }
        val url = normalizeOrShowError(editor.url) ?: return
        val title = editor.title.trim()
        if (title.isEmpty() || title.length > 80 || title.any { it.isISOControl() }) {
            changeEditor { it.copy(titleError = "请输入 1–80 个字符的网站名称，不包含换行") }
            return
        }
        if (editor.mode == IconSourceMode.CUSTOM && customImage == null && original?.customImagePath == null) {
            changeEditor { it.copy(error = "请先选择一张自定义图片，或切换图标模式") }
            return
        }
        fetchJob?.cancel()
        fetchEpoch++
        changeEditor { it.copy(saving = true, loading = false, error = null) }
        val pendingAuto = autoImage.takeIf { autoImageUrl == url }
        val pendingCustom = customImage
        viewModelScope.launch {
            var saved = false
            var wasPinned = false
            var synced = false
            try {
                container.mutationMutex.withLock {
                    val latest = repository.find(editor.id)
                    check(editor.isNew || latest != null) { "此收藏已被删除" }
                    val previous = latest ?: original
                    val version = (previous?.imageVersion ?: 0) + 1
                    val sameUrl = previous?.url == url
                    var autoPath = previous?.autoImagePath.takeIf { sameUrl }
                    var customPath = previous?.customImagePath
                    if (pendingAuto != null) autoPath = container.iconStore.save(editor.id, version, IconSourceMode.AUTO, pendingAuto.bitmap)
                    if (pendingCustom != null) customPath = container.iconStore.save(editor.id, version, IconSourceMode.CUSTOM, pendingCustom)
                    val now = System.currentTimeMillis()
                    val item = BookmarkItem(
                        id = editor.id, title = title, url = url, selectedIconSource = editor.mode,
                        autoImagePath = autoPath, customImagePath = customPath,
                        sourceUrl = pendingAuto?.sourceUrl ?: previous?.sourceUrl.takeIf { sameUrl },
                        imageVersion = version,
                        lastFetchedAt = if (pendingAuto != null) now else previous?.lastFetchedAt.takeIf { sameUrl },
                        iconDescription = pendingAuto?.description ?: previous?.iconDescription.takeIf { sameUrl },
                        createdAt = previous?.createdAt ?: now, updatedAt = now,
                    )
                    repository.save(item)
                    saved = true
                    wasPinned = container.shortcutPublisher.isPinned(item.id)
                    synced = if (wasPinned) try { container.shortcutPublisher.update(item) } catch (error: Exception) { rethrowCancellation(error); false } else true
                    cleanUnusedIcons()
                }
            } catch (error: Exception) {
                rethrowCancellation(error)
                if (!saved) {
                    changeEditor { it.copy(saving = false, error = "保存失败，原有收藏未被覆盖。请检查设备剩余空间后重试。") }
                    return@launch
                }
            }
            closeEditor()
            notify(when {
                !synced -> "已保存；桌面更新暂未完成，可在收藏菜单中重试"
                wasPinned -> "已保存并请求更新桌面快捷方式"
                else -> "已保存，可在收藏菜单中添加到桌面"
            })
        }
    }

    fun delete(item: BookmarkItem) {
        viewModelScope.launch {
            try {
                container.mutationMutex.withLock {
                    repository.delete(item.id)
                    try { container.shortcutPublisher.disable(item.id) } catch (error: Exception) { rethrowCancellation(error) }
                    cleanUnusedIcons()
                }
                notify("已删除收藏；对应的旧桌面快捷方式将不可用")
            } catch (error: Exception) { rethrowCancellation(error); notify("删除失败，请重试") }
        }
    }

    fun pin(item: BookmarkItem) {
        viewModelScope.launch {
            try {
                val accepted = container.mutationMutex.withLock {
                    repository.find(item.id)?.let { container.shortcutPublisher.pin(it) } ?: false
                }
                notify(if (accepted) "已向桌面发送添加请求，请在系统提示中确认" else "当前桌面不支持添加，或系统暂时无法处理请求")
            } catch (error: Exception) { rethrowCancellation(error); notify("暂时无法添加到桌面，请稍后重试") }
        }
    }

    fun updateShortcut(item: BookmarkItem) {
        viewModelScope.launch {
            try {
                if (!container.shortcutPublisher.isPinned(item.id)) {
                    notify("请先将这个网站添加到桌面")
                    return@launch
                }
                val updated = container.mutationMutex.withLock {
                    repository.find(item.id)?.let { container.shortcutPublisher.update(it) } ?: false
                }
                notify(if (updated) "已请求更新桌面快捷方式，实际刷新时间取决于桌面启动器" else "系统暂未接受更新，请稍后重试")
            } catch (error: Exception) { rethrowCancellation(error); notify("桌面更新失败，请稍后重试") }
        }
    }

    fun refresh(item: BookmarkItem) {
        if (item.selectedIconSource != IconSourceMode.AUTO) { notify("只有自动模式的图标会被刷新"); return }
        viewModelScope.launch {
            val result = refreshOne(item)
            notify(when (result) {
                RefreshResult.UPDATED -> "已更新网站图标；已有桌面快捷方式会尝试同步"
                RefreshResult.SKIPPED -> "收藏已修改或正在刷新，本次未覆盖"
                RefreshResult.FAILED -> "未获取到新图标，已保留原图"
            })
        }
    }

    fun refreshAll() {
        if (mutableState.value.refreshing) return
        val candidates = repository.bookmarks.value.filter { it.selectedIconSource == IconSourceMode.AUTO }
        if (candidates.isEmpty()) { notify("暂无需要刷新的自动图标"); return }
        mutableState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            var updated = 0
            var failed = 0
            var skipped = 0
            try {
                for (item in candidates) when (refreshOne(item)) {
                    RefreshResult.UPDATED -> updated++
                    RefreshResult.FAILED -> failed++
                    RefreshResult.SKIPPED -> skipped++
                }
                notify("已更新 $updated 个图标，$failed 个保留原图" + if (skipped > 0) "，$skipped 个已跳过" else "")
            } finally { mutableState.update { it.copy(refreshing = false) } }
        }
    }

    private suspend fun refreshOne(snapshot: BookmarkItem): RefreshResult {
        if (!refreshingIds.add(snapshot.id)) return RefreshResult.SKIPPED
        try {
            val result = container.siteIconDiscoverer.fetch(snapshot.url) ?: return RefreshResult.FAILED
            return container.mutationMutex.withLock {
                val latest = repository.find(snapshot.id) ?: return@withLock RefreshResult.SKIPPED
                // An editor may have saved a custom icon, different URL, or newer auto image during IO.
                if (latest.selectedIconSource != IconSourceMode.AUTO || latest.url != snapshot.url || latest.imageVersion != snapshot.imageVersion) return@withLock RefreshResult.SKIPPED
                val version = latest.imageVersion + 1
                val path = container.iconStore.save(latest.id, version, IconSourceMode.AUTO, result.bitmap)
                val now = System.currentTimeMillis()
                val updated = latest.copy(autoImagePath = path, sourceUrl = result.sourceUrl, iconDescription = result.description, imageVersion = version, lastFetchedAt = now, updatedAt = now)
                repository.save(updated)
                try { container.shortcutPublisher.update(updated) } catch (error: Exception) { rethrowCancellation(error) }
                cleanUnusedIcons()
                RefreshResult.UPDATED
            }
        } catch (error: Exception) {
            rethrowCancellation(error)
            return RefreshResult.FAILED
        } finally { refreshingIds.remove(snapshot.id) }
    }

    private fun updatePreview() {
        val editor = mutableState.value.editor ?: return
        previewJob?.cancel()
        val epoch = ++previewEpoch
        val origin = original
        val normalizedUrl = runCatching { UrlNormalizer.normalize(editor.url) }.getOrNull()
        val newAuto = autoImage?.bitmap.takeIf { autoImageUrl == normalizedUrl }
        val newCustom = customImage
        val oldAutoValid = origin?.url == normalizedUrl
        val label = when (editor.mode) {
            IconSourceMode.CUSTOM -> if (editor.hasCustomImage) "本地自定义图片 · 仅存于此设备" else "请选择一张图片"
            IconSourceMode.GENERATED -> "字母生成 · 根据域名分配配色"
            IconSourceMode.AUTO -> autoImage?.description.takeIf { autoImageUrl == normalizedUrl } ?: origin?.iconDescription.takeIf { oldAutoValid } ?: "自动发现 · Manifest / Touch Icon / Favicon"
        }
        mutableState.update { it.copy(editor = it.editor?.copy(sourceLabel = label)) }
        previewJob = viewModelScope.launch {
            val bitmap = try {
                withContext(Dispatchers.Default) {
                    when (editor.mode) {
                        IconSourceMode.CUSTOM -> newCustom ?: origin?.let { container.iconResolver.resolve(it.copy(title = editor.title, url = editor.url, selectedIconSource = IconSourceMode.CUSTOM)) } ?: container.iconResolver.generated(editor.title, editor.url)
                        IconSourceMode.GENERATED -> container.iconResolver.generated(editor.title, editor.url)
                        IconSourceMode.AUTO -> newAuto ?: origin?.takeIf { oldAutoValid }?.let { container.iconResolver.resolve(it.copy(title = editor.title, selectedIconSource = IconSourceMode.AUTO)) } ?: container.iconResolver.generated(editor.title, editor.url)
                    }
                }
            } catch (error: Exception) { rethrowCancellation(error); container.iconResolver.generated(editor.title, editor.url) }
            if (epoch == previewEpoch && mutableState.value.editor?.id == editor.id) mutableState.update { it.copy(editor = it.editor?.copy(preview = bitmap)) }
        }
    }

    private fun hasAutoImageForCurrentUrl(): Boolean {
        val url = mutableState.value.editor?.url?.let { runCatching { UrlNormalizer.normalize(it) }.getOrNull() } ?: return false
        return (autoImage != null && autoImageUrl == url) || (original?.url == url && original?.autoImagePath != null)
    }

    private fun isCurrentRequest(epoch: Int, requestEpoch: Int, url: String): Boolean {
        val editor = mutableState.value.editor ?: return false
        return epoch == editorEpoch && requestEpoch == fetchEpoch && editor.mode == IconSourceMode.AUTO && !editor.saving && runCatching { UrlNormalizer.normalize(editor.url) }.getOrNull() == url
    }

    private fun normalizeOrShowError(url: String): String? = try {
        UrlNormalizer.normalize(url)
    } catch (error: IllegalArgumentException) {
        changeEditor { it.copy(urlError = error.message ?: "请输入有效的 http 或 https 网址") }
        null
    }

    private fun changeEditor(transform: (EditorUiState) -> EditorUiState) {
        mutableState.update { home -> home.copy(editor = home.editor?.let(transform)) }
        persistEditor()
    }

    private fun persistEditor() {
        val editor = mutableState.value.editor ?: return
        savedState["draft.id"] = editor.id
        savedState["draft.new"] = editor.isNew
        savedState["draft.title"] = editor.title
        savedState["draft.url"] = editor.url
        savedState["draft.mode"] = editor.mode.name
        savedState["draft.dirty"] = editor.dirty
        savedState["draft.custom"] = customDraftName
    }

    private suspend fun restoreEditor() {
        val id = savedState.get<String>("draft.id") ?: return
        val isNew = savedState.get<Boolean>("draft.new") ?: true
        val previous = if (isNew) null else repository.find(id)
        if (!isNew && previous == null) { DRAFT_KEYS.forEach { savedState.remove<Any?>(it) }; return }
        val title = savedState.get<String>("draft.title").orEmpty()
        val url = savedState.get<String>("draft.url").orEmpty()
        val mode = runCatching { IconSourceMode.valueOf(savedState.get<String>("draft.mode") ?: "AUTO") }.getOrDefault(IconSourceMode.AUTO)
        val dirty = savedState.get<Boolean>("draft.dirty") ?: false
        val customName = savedState.get<String>("draft.custom")
        beginEditor(previous, EditorUiState(id = id, isNew = isNew, title = title, url = url, mode = mode, dirty = dirty, hasCustomImage = previous?.customImagePath != null))
        customImage = drafts.read(customName)
        customDraftName = customName.takeIf { customImage != null }
        changeEditor { it.copy(hasCustomImage = customImage != null || previous?.customImagePath != null, error = if (customName != null && customImage == null) "图片草稿已过期，请重新选择图片" else null) }
        updatePreview()
        if (mode == IconSourceMode.AUTO && !hasAutoImageForCurrentUrl() && url.isNotBlank()) fetchIcon(force = true)
    }

    private suspend fun cleanUnusedIcons() {
        try { container.iconStore.cleanup(repository.bookmarks.value) } catch (error: Exception) { rethrowCancellation(error) }
    }

    private fun cancelEditorJobs() {
        editorEpoch++
        fetchEpoch++
        importEpoch++
        previewEpoch++
        fetchJob?.cancel()
        previewJob?.cancel()
        importJob?.cancel()
    }

    fun notify(message: String) { messageChannel.trySend(message) }
    private fun rethrowCancellation(error: Exception) { if (error is CancellationException) throw error }
    private enum class RefreshResult { UPDATED, FAILED, SKIPPED }
    private companion object {
        val DRAFT_KEYS = listOf("draft.id", "draft.new", "draft.title", "draft.url", "draft.mode", "draft.dirty", "draft.custom")
    }
}
