# 架构与实现约束

## 数据与事务

`BookmarkItem.id` 是不可变 UUID，同时作为桌面快捷方式 ID。`BookmarkRepository` 使用私有 `bookmarks.json`，通过 Android `AtomicFile` 写入。磁盘提交成功后才发布新的 `StateFlow`，文件损坏时保留原文件并阻止写入，不把读取失败当作空列表。

JSON schemaVersion 为 2，读取兼容旧列表格式和旧字段。模型只保存图标相对路径和来源元数据，不把图片写入 JSON 或 SharedPreferences。主应用、透明路由 Activity 使用同一个 Application 容器中的 Repository。

保存顺序为：规范化输入 → 写入新版本图片 → 原子写入元数据 → 解析最终图标并请求桌面更新 → 清理不再引用的图片。应用级 Mutex 将文件写入、元数据和清理串行化；桌面更新失败不回滚已经保存的收藏。

## 图标与并发

- AUTO、CUSTOM、GENERATED 各自独立；切换模式不会删除另一个模式的有效图片。
- URL 变化会使旧网站的自动图片失效，自定义图片继续保留。
- 临时导入图片保存在私有草稿缓存，用户点击保存前不改变书签；文字和选定图片草稿可随保存状态恢复。
- 网络请求可取消，有连接、读取及整个发现流程的时间上限。
- 编辑器检查请求的书签、模式和 URL，防止迟到的图片覆盖新输入。
- 批量刷新在网络返回后重新查询当前元数据。如果用户已修改模式、URL 或图片版本，跳过该结果。
- 保存后的图标补全在当前 ViewModel 生命周期内异步执行。进程或页面被结束时任务可被取消，不承诺退出应用后持续下载；收藏仍保持 AUTO 并使用可用缓存或生成图标，之后可手动重新抓取。
- 所有图标收敛为 512×512 图片，保留纵横比，并在自适应图标安全区内居中放置。图片缺失或损坏时始终可生成替代图标。

网络仅请求 HTTP(S)。HTML/Manifest 至多 512 KiB，HTML 读到 head 结束即停止；单个图片至多 5 MiB。解码前检查签名、MIME 和尺寸，拒绝边长超过 4096 的图片。SVG 拒绝外部引用、脚本、DOCTYPE/实体等不安全内容，并限制结构复杂度。ICO 目录和帧数据先检查边界，再按分辨率尝试解码。

## 桌面与浏览器

固定与更新都使用稳定 UUID 和显式 `ShortcutRouterActivity` Intent，Intent 中不保存 URL。透明路由按 UUID 读取最新 URL，再进入 `ChromeTabLauncher`。删除收藏会禁用对应的系统快捷方式；即使设备没有立即禁用，路由也不会打开被删除的旧 URL。

`requestPinShortcut()` 返回 true 表示系统接受请求，仍需用户在启动器中确认。`updateShortcuts()` 返回 true 表示平台接受更新，并不能保证 OEM 启动器已经立即重绘；限流或拒绝时提供重试入口。

优先选择 Chrome 的 Custom Tabs 提供者，否则使用其他提供者，最后退回可处理网址的系统浏览器。不创建 WebView，也不使用隔离的临时浏览会话。应用无权读取浏览器 Cookie。

## 构建与验证

- Gradle Wrapper 8.13 的脚本和 JAR 来自 Gradle 官方 v8.13.0 标签。
- JDK 17 / AGP 8.13.2 / Kotlin 2.2.21 / compileSdk & targetSdk 36 / minSdk 26。
- CI 执行 debug 构建、JVM/Robolectric 测试、Lint、R8 release 构建与 Android 35 仪器测试。
- PR 不分发密钥、不自动发布商店版本，也不自动合并。

官方参考：

- [AGP 8.13 兼容性](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [创建快捷方式](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts)
- [管理与更新快捷方式](https://developer.android.com/develop/ui/compose/system/shortcuts/managing-shortcuts)
- [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker)
