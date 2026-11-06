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
import io.github.homura251.webshortcuts.icons.CropRect
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
    private var autoImageFetchedAt: Long? = null
    private var customImage: Bitmap? = null
    private var customOriginal: Bitmap? = null
    private var customOriginalDirty = false
    private var customDraftName: String? = null
    private var originalDraftName: String? = null
    private var lastAttemptedUrl: String? = null
    private var fetchJob: Job? = null
    private var previewJob: Job? = null
    private var importJob: Job? = null
    private var sourceJob: Job? = null
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
                drafts.prune(listOfNotNull(customDraftName, originalDraftName).toSet())
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
            hasCustomImage = item.customImagePath != null, cropRect = item.customCrop,
        ))
    }

    private fun beginEditor(item: BookmarkItem?, editor: EditorUiState, loadCropSource: Boolean = true) {
        cancelEditorJobs()
        original = item
        autoImage = null
        autoImageUrl = null
        autoImageFetchedAt = null
        customImage = null
        customOriginal = null
        customOriginalDirty = false
        customDraftName = null
        originalDraftName = null
        lastAttemptedUrl = null
        mutableState.update { it.copy(editor = editor) }
        persistEditor()
        updatePreview()
        if (loadCropSource && item?.customOriginalPath != null) {
            val epoch = editorEpoch
            sourceJob = viewModelScope.launch {
                val source = container.iconStore.readOriginal(item.customOriginalPath)
                if (epoch != editorEpoch) return@launch
                customOriginal = source
                changeEditor { it.copy(cropSource = source, canCrop = source != null) }
            }
        }
    }

    fun dismissEditor() {
        if (mutableState.value.editor?.saving == true) return
        closeEditor()
    }

    private fun closeEditor() {
        val discarded = customDraftName
        val discardedOriginal = originalDraftName
        cancelEditorJobs()
        original = null
        autoImage = null
        customImage = null
        customOriginal = null
        customOriginalDirty = false
        customDraftName = null
        originalDraftName = null
        mutableState.update { it.copy(editor = null) }
        DRAFT_KEYS.forEach { savedState.remove<Any?>(it) }
        viewModelScope.launch { drafts.delete(discarded); drafts.delete(discardedOriginal) }
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
        changeEditor { it.copy(url = value, urlError = null, loading = importJob?.isActive == true, error = null, statusMessage = null, dirty = true) }
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
        if (fetchJob?.isActive == true && lastAttemptedUrl == target) return
        if (!force && lastAttemptedUrl == target) return
        fetchJob?.cancel()
        lastAttemptedUrl = target
        val epoch = editorEpoch
        val requestEpoch = ++fetchEpoch
        changeEditor { it.copy(loading = true, error = null, statusMessage = null, urlError = null) }
        fetchJob = viewModelScope.launch {
            try {
                val result = container.siteIconDiscoverer.fetch(target)
                if (!isCurrentRequest(epoch, requestEpoch, target)) return@launch
                if (result != null) {
                    autoImage = result
                    autoImageUrl = target
                    autoImageFetchedAt = System.currentTimeMillis()
                    changeEditor { it.copy(loading = false, sourceLabel = result.description, error = null,
                        statusMessage = "已获取新图标，保存后应用并请求桌面同步", dirty = true) }
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
        sourceJob?.cancel()
        val epoch = editorEpoch
        val requestEpoch = ++importEpoch
        changeEditor { it.copy(loading = true, error = null) }
        importJob = viewModelScope.launch {
            try {
                val imported = container.iconImporter.importEditable(uri)
                val bitmap = imported.fitted
                if (epoch != editorEpoch || requestEpoch != importEpoch) return@launch
                val name = drafts.write(editor.id, bitmap)
                val sourceName = try { drafts.write(editor.id, imported.original) }
                    catch (error: Exception) { drafts.delete(name); throw error }
                if (epoch != editorEpoch || requestEpoch != importEpoch) {
                    drafts.delete(name); drafts.delete(sourceName); return@launch
                }
                val previousName = customDraftName
                val previousOriginal = originalDraftName
                customImage = bitmap
                customOriginal = imported.original
                customOriginalDirty = true
                customDraftName = name
                originalDraftName = sourceName
                importJob = null // The image is ready before the UI enables Save or another edit.
                changeEditor { it.copy(mode = IconSourceMode.CUSTOM, loading = false, error = null, statusMessage = null,
                    hasCustomImage = true, dirty = true, canCrop = true, centerCrop = false,
                    cropSource = imported.original, cropRect = null) }
                updatePreview()
                drafts.delete(previousName)
                drafts.delete(previousOriginal)
            } catch (error: Exception) {
                rethrowCancellation(error)
                if (epoch == editorEpoch && requestEpoch == importEpoch) {
                    importJob = null
                    changeEditor { it.copy(loading = false, error = "无法导入这张图片。请选择不超过 5 MB、边长不超过 4096 像素的 PNG、JPEG 或 WebP 图片。") }
                }
            }
        }
    }

    fun setCenterCrop(crop: Boolean) {
        val source = customOriginal ?: return
        val side = minOf(source.width, source.height).toFloat()
        val rect = if (crop) CropRect((source.width - side) / (2f * source.width),
            (source.height - side) / (2f * source.height),
            (source.width + side) / (2f * source.width), (source.height + side) / (2f * source.height)) else null
        applyCustomCrop(rect, centerCrop = crop)
    }

    fun applyCustomCrop(crop: CropRect?) = applyCustomCrop(crop, centerCrop = false)

    private fun applyCustomCrop(crop: CropRect?, centerCrop: Boolean) {
        val editor = mutableState.value.editor ?: return
        val source = customOriginal ?: return
        if (editor.saving || importJob?.isActive == true || editor.mode != IconSourceMode.CUSTOM) return
        val epoch = editorEpoch
        val requestEpoch = ++importEpoch
        changeEditor { it.copy(loading = true, error = null, statusMessage = null) }
        importJob = viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) { container.iconProcessor.crop(source, crop) }
                val name = drafts.write(editor.id, bitmap)
                if (epoch != editorEpoch || requestEpoch != importEpoch) { drafts.delete(name); return@launch }
                val previousName = customDraftName
                customImage = bitmap
                customDraftName = name
                importJob = null
                changeEditor { it.copy(centerCrop = centerCrop, cropRect = crop, loading = false, dirty = true) }
                updatePreview()
                drafts.delete(previousName)
            } catch (error: Exception) {
                rethrowCancellation(error)
                if (epoch == editorEpoch && requestEpoch == importEpoch) {
                    importJob = null
                    changeEditor { it.copy(loading = false, error = "无法保存裁切预览，请重试") }
                }
            }
        }
    }

    fun save() {
        val editor = mutableState.value.editor ?: return
        if (editor.saving) return
        if (importJob?.isActive == true) { notify("请等待图片处理完成后保存"); return }
        if (customOriginalDirty && customOriginal == null) {
            changeEditor { it.copy(error = "原图草稿已过期，请重新导入图片后保存，或放弃本次编辑") }
            return
        }
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
        val continueAutomaticFetch = editor.mode == IconSourceMode.AUTO &&
            (fetchJob?.isActive == true || lastAttemptedUrl != url)
        fetchJob?.cancel()
        fetchEpoch++
        changeEditor { it.copy(saving = true, loading = false, error = null) }
        val pendingAuto = autoImage.takeIf { autoImageUrl == url }
        val pendingCustom = customImage
        val pendingOriginal = customOriginal.takeIf { customOriginalDirty }
        viewModelScope.launch {
            var saved = false
            var wasPinned = false
            var synced = false
            var backgroundFetch: BookmarkItem? = null
            try {
                container.mutationMutex.withLock {
                    val latest = repository.find(editor.id)
                    check(editor.isNew || latest != null) { "此收藏已被删除" }
                    val previous = latest ?: original
                    val version = (previous?.imageVersion ?: 0) + 1
                    val sameUrl = previous?.url == url
                    var autoPath = previous?.autoImagePath.takeIf { sameUrl }
                    var customPath = previous?.customImagePath
                    var customOriginalPath = previous?.customOriginalPath
                    if (pendingAuto != null) autoPath = container.iconStore.save(editor.id, version, IconSourceMode.AUTO, pendingAuto.bitmap)
                    if (pendingCustom != null) customPath = container.iconStore.save(editor.id, version, IconSourceMode.CUSTOM, pendingCustom)
                    if (pendingOriginal != null) customOriginalPath = container.iconStore.saveOriginal(editor.id, version, pendingOriginal)
                    val now = System.currentTimeMillis()
                    val item = BookmarkItem(
                        id = editor.id, title = title, url = url, selectedIconSource = editor.mode,
                        autoImagePath = autoPath, customImagePath = customPath,
                        customOriginalPath = customOriginalPath,
                        customCrop = if (pendingCustom != null) editor.cropRect else previous?.customCrop,
                        sourceUrl = pendingAuto?.sourceUrl ?: previous?.sourceUrl.takeIf { sameUrl },
                        imageVersion = version,
                        lastFetchedAt = if (pendingAuto != null) autoImageFetchedAt ?: now else previous?.lastFetchedAt.takeIf { sameUrl },
                        iconDescription = pendingAuto?.description ?: previous?.iconDescription.takeIf { sameUrl },
                        createdAt = previous?.createdAt ?: now, updatedAt = now,
                    )
                    repository.save(item)
                    saved = true
                    if (continueAutomaticFetch && item.autoImagePath == null) backgroundFetch = item
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
                backgroundFetch != null -> "已保存，正在继续获取网站图标"
                !synced -> "已保存；桌面更新暂未完成，可在收藏菜单中重试"
                wasPinned -> "已保存并请求更新桌面快捷方式"
                else -> "已保存，可在收藏菜单中添加到桌面"
            })
            // Save is immediate. A late AUTO image still goes through refreshOne's mode/URL/version
            // checks, so it cannot replace a custom image selected after the editor closes.
            backgroundFetch?.let(::refresh)
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
        if (item.id in mutableState.value.refreshingIds) return
        viewModelScope.launch {
            val result = refreshOne(item)
            // Another refresh already owns the row; do not replace its live feedback.
            if (item.id !in mutableState.value.refreshingIds) notify(result.message)
        }
    }

    fun clearRefreshSummary() { mutableState.update { it.copy(refreshSummary = null) } }

    fun refreshAll() {
        if (mutableState.value.refreshing) return
        val candidates = repository.bookmarks.value.filter { it.selectedIconSource == IconSourceMode.AUTO }
        if (candidates.isEmpty()) { notify("暂无需要刷新的自动图标"); return }
        mutableState.update { it.copy(refreshing = true, refreshCompleted = 0, refreshTotal = candidates.size, refreshSummary = null) }
        viewModelScope.launch {
            var updated = 0
            var pendingSync = 0
            var failed = 0
            var skipped = 0
            var completed = 0
            try {
                for (item in candidates) {
                    when (refreshOne(item)) {
                        RefreshResult.UPDATED, RefreshResult.SYNC_REQUESTED -> updated++
                        RefreshResult.SYNC_PENDING -> { updated++; pendingSync++ }
                        RefreshResult.FAILED -> failed++
                        RefreshResult.SKIPPED -> skipped++
                    }
                    completed++
                    mutableState.update { it.copy(refreshCompleted = completed) }
                }
            } finally {
                val summary = (if (completed < candidates.size) "刷新已停止（$completed/${candidates.size}）：" else "刷新完成：") +
                    "已更新 $updated 个图标，$failed 个保留原图" +
                    (if (skipped > 0) "，$skipped 个已跳过" else "") +
                    (if (pendingSync > 0) "；$pendingSync 个桌面同步待重试" else "")
                mutableState.update { it.copy(refreshing = false, refreshSummary = summary) }
                notify(summary)
            }
        }
    }

    private suspend fun refreshOne(snapshot: BookmarkItem): RefreshResult {
        synchronized(refreshingIds) {
            if (!refreshingIds.add(snapshot.id)) return RefreshResult.SKIPPED
            mutableState.update { it.copy(
                refreshingIds = refreshingIds.toSet(),
                refreshMessages = it.refreshMessages + (snapshot.id to "正在获取网站图标…"),
            ) }
        }
        var fetched: IconFetchResult? = null
        var outcome = RefreshResult.FAILED
        var cancelled = false
        try {
            val result = container.siteIconDiscoverer.fetch(snapshot.url) ?: return outcome
            fetched = result
            outcome = container.mutationMutex.withLock {
                val latest = repository.find(snapshot.id) ?: return@withLock RefreshResult.SKIPPED
                // An editor may have saved a custom icon, different URL, or newer auto image during IO.
                if (latest.selectedIconSource != IconSourceMode.AUTO || latest.url != snapshot.url || latest.imageVersion != snapshot.imageVersion) return@withLock RefreshResult.SKIPPED
                val version = latest.imageVersion + 1
                val path = container.iconStore.save(latest.id, version, IconSourceMode.AUTO, result.bitmap)
                val now = System.currentTimeMillis()
                val updated = latest.copy(autoImagePath = path, sourceUrl = result.sourceUrl, iconDescription = result.description, imageVersion = version, lastFetchedAt = now, updatedAt = now)
                repository.save(updated)
                // A saved image is already a successful refresh, even if its launcher update
                // is rate-limited or rejected. Do not report it as a failed download.
                outcome = RefreshResult.UPDATED
                val sync = try {
                    if (!container.shortcutPublisher.isPinned(updated.id)) RefreshResult.UPDATED
                    else if (container.shortcutPublisher.update(updated)) RefreshResult.SYNC_REQUESTED
                    else RefreshResult.SYNC_PENDING
                } catch (error: Exception) { rethrowCancellation(error); RefreshResult.SYNC_PENDING }
                outcome = sync
                cleanUnusedIcons()
                sync
            }
            return outcome
        } catch (error: Exception) {
            cancelled = error is CancellationException
            rethrowCancellation(error)
            return outcome
        } finally {
            fetched?.bitmap?.recycle()
            synchronized(refreshingIds) {
                refreshingIds.remove(snapshot.id)
                mutableState.update { it.copy(
                    refreshingIds = refreshingIds.toSet(),
                    refreshMessages = it.refreshMessages + (snapshot.id to
                        if (cancelled && outcome == RefreshResult.FAILED) "刷新已停止，已保留原图" else outcome.message),
                ) }
            }
        }
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
        val sourceUrl = if (editor.mode == IconSourceMode.AUTO)
            autoImage?.sourceUrl.takeIf { autoImageUrl == normalizedUrl } ?: origin?.sourceUrl.takeIf { oldAutoValid } else null
        val fetchedAt = if (editor.mode == IconSourceMode.AUTO)
            autoImageFetchedAt.takeIf { autoImageUrl == normalizedUrl } ?: origin?.lastFetchedAt.takeIf { oldAutoValid } else null
        mutableState.update { it.copy(editor = it.editor?.copy(sourceLabel = label, sourceUrl = sourceUrl, lastFetchedAt = fetchedAt)) }
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
        savedState["draft.original"] = originalDraftName
        savedState["draft.crop"] = editor.cropRect?.let { floatArrayOf(it.left, it.top, it.right, it.bottom) }
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
        val sourceName = savedState.get<String>("draft.original")
        val crop = if (savedState.contains("draft.crop")) savedState.get<FloatArray>("draft.crop")?.let { values ->
            runCatching { require(values.size == 4); CropRect(values[0], values[1], values[2], values[3]) }.getOrNull()
        } else previous?.customCrop
        beginEditor(previous, EditorUiState(id = id, isNew = isNew, title = title, url = url, mode = mode, dirty = dirty,
            hasCustomImage = previous?.customImagePath != null, cropRect = crop), loadCropSource = false)
        customImage = drafts.read(customName)
        customDraftName = customName.takeIf { customImage != null }
        val restoredSource = drafts.read(sourceName)
        customOriginal = if (sourceName != null) restoredSource else container.iconStore.readOriginal(previous?.customOriginalPath)
        originalDraftName = sourceName
        customOriginalDirty = sourceName != null
        if (customImage == null && customOriginal != null && (customName != null || sourceName != null)) {
            // A disposable preview can be rebuilt; the original plus coordinates are authoritative.
            try {
                val recovered = withContext(Dispatchers.Default) { container.iconProcessor.crop(customOriginal!!, crop) }
                customDraftName = drafts.write(id, recovered)
                customImage = recovered
            } catch (error: Exception) { rethrowCancellation(error) }
        }
        changeEditor { it.copy(hasCustomImage = customImage != null || previous?.customImagePath != null,
            cropSource = customOriginal, canCrop = customOriginal != null,
            error = when {
                sourceName != null && restoredSource == null -> "原图草稿已过期，请重新导入图片后保存，或放弃本次编辑"
                customName != null && customImage == null -> "图片草稿已过期，请重新选择图片"
                else -> null
            }) }
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
        sourceJob?.cancel()
    }

    fun notify(message: String) { messageChannel.trySend(message) }
    private fun rethrowCancellation(error: Exception) { if (error is CancellationException) throw error }
    private enum class RefreshResult(val message: String) {
        UPDATED("已更新图标，保存在此设备"),
        SYNC_REQUESTED("已更新图标并请求桌面同步，显示时间由启动器决定"),
        SYNC_PENDING("图标已更新，桌面同步待重试"),
        FAILED("未获取到新图标，已保留原图"),
        SKIPPED("收藏已修改或正在刷新，本次未覆盖"),
    }
    private companion object {
        val DRAFT_KEYS = listOf("draft.id", "draft.new", "draft.title", "draft.url", "draft.mode", "draft.dirty", "draft.custom", "draft.original", "draft.crop")
    }
}
