# WebShortcuts v1.1 — 图标管理与桌面同步 产品需求文档 (PRD)

| 文档属性 | 详情 |
| :--- | :--- |
| **产品名称** | WebShortcuts |
| **版本号** | v1.1 |
| **设计规范** | Material Design 3 (Material You) |
| **文档状态** | 设计方案，待实现 |
| **核心目标** | 引入多模式图标管理系统与桌面快捷方式动态同步，全面升级 MD3 视觉与交互体验 |

---

## 一、 产品背景与系统目标

### 1.1 背景概述
WebShortcuts 旨在帮助用户通过单个 APK 集中管理多个网站收藏，并借助 Chrome Custom Tabs (CCT) 实现轻量级网页浏览与 Chrome 登录态复用。在 v1.0 版本中，快捷方式图标仅支持简单的首字母生成，辨识度较低且无法满足个性化需求。

### 1.2 核心目标
在原有多网站收藏夹和 Chrome Custom Tabs 的基础上，增加**自动获取网站图标**、**用户自定义图标**和**桌面快捷方式图标同步**功能，并采用 **Material Design 3 (MD3)** 规范重构配置界面：
1. **广泛兼容**：支持标准 PWA 网站及普通非 PWA 网站的图标解析。
2. **高清抓取**：支持从目标网站自动嗅探并获取最高清图标（Favicon / Apple Touch Icon / Manifest Icons）。
3. **自由定制**：支持通过系统相册或文件选择器导入本地自定义图标。
4. **所见即所得**：基于 MD3 组件提供实时图标预览、模式手动切换与回退机制。
5. **动态热更新**：支持原地更新已固定在系统桌面的快捷方式图标与名称，无需重新生成或重新编译 APK。
6. **核心保留**：100% 保留原有的 Chrome Custom Tabs 启动链路及登录态复用功能。

---

## 二、 用户故事与核心场景 (User Stories)

| 角色 | 期望操作 (Action) | 业务价值 (Value) | 关联模式 |
| :--- | :--- | :--- | :--- |
| 普通用户 | 输入网址保存书签时，App 自动拉取该网站的官方高清 Logo 作为图标 | 无需手动找图，桌面快捷方式与原生 App 视觉体验一致 | `AUTO` |
| 极客/美化控 | 从手机相册导入自己设计的图标包图片，覆盖默认网站图标 | 打造统一风格的桌面布局，且后续不会被网站自动刷新覆盖 | `CUSTOM` |
| 极简/内网用户 | 访问无图标的内网系统或加载失败时，使用美观的字母生成图标 | 保证任何网站都有清晰、符合 MD3 色彩规范的兜底图标可用 | `GENERATED` |
| 存量用户 | 修改已有书签的图标或名称并点击保存 | 桌面已固定的快捷方式自动刷新为新图标，无需删除重建 | 桌面同步 |

---

## 三、 Material Design 3 (MD3) 视觉与交互规范

本版本所有新增及改版界面均严格遵循 Google **Material Design 3** 指导原则，利用动态色彩（Dynamic Color）与标准组件提升易用性。

### 3.1 色彩系统 (Color Roles)
* **动态色彩支持 (Dynamic Color)**：在 Android 12+ 设备上开启 `DynamicColors`，界面主题跟随系统壁纸自动提取色板。
* **字母图标色板 (`GENERATED` 模式)**：
  * 摒弃高饱和度随机刺眼颜色，改用 MD3 Tonal Palettes（色调调色板）。
  * 根据网站域名 Hash 值映射到预设的 MD3 语义容器色组合：背景采用 `PrimaryContainer` / `SecondaryContainer` / `TertiaryContainer`，中心字母采用对应的 `OnPrimaryContainer` / `OnSecondaryContainer` / `OnTertiaryContainer`，确保对比度符合 WCAG AA 标准（对比度 $\ge 4.5:1$）。
* **异常状态提示**：图标抓取失败时的非阻断提示使用 `ErrorContainer` 与 `OnErrorContainer`。

### 3.2 核心组件映射 (MD3 Components)

#### 1. 书签列表页 (主页)
* **Top App Bar**：采用 `MediumTopAppBar` 或 `CenterAlignedTopAppBar`，滚动时平滑过渡至 `SurfaceContainer` 颜色。
* **Bookmark Card**：使用 `OutlinedCard` 或 `FilledCard`（圆角 token：`ShapeAppearance.Material3.Corner.Medium`，即 $12\text{ dp}$）。卡片左侧展示当前生效的圆角图标预览（尺寸 $48 \times 48\text{ dp}$），右侧提供“更多操作”图标按钮（触发编辑/更新桌面快捷方式）。
* **FAB**：右下角采用标准 `FloatingActionButton`（或 `ExtendedFloatingActionButton`）用于新建网站快捷方式。

#### 2. 书签与图标编辑面板 (新增/编辑弹窗)
* **容器选择**：采用 **Modal Bottom Sheet**（模态底部工作表，圆角 $28\text{ dp}$）或 **Full-screen Dialog**（全屏对话框），内部结构自上而下分为：**图标预览区**、**模式切换器**、**表单输入区**、**操作按钮区**。
* **图标实时预览区 (Icon Preview Stage)**：
  * 居中展示 $96 \times 96\text{ dp}$ 的自适应图标预览框，背景采用微弱网格或 `SurfaceContainerHigh` 衬托。
  * **状态指示**：当处于 `AUTO` 模式且正在网络抓取时，预览框外围环绕 MD3 `CircularProgressIndicator`（不定长加载动画）。
* **图标模式切换器 (Segmented Button)**：
  * 使用 MD3 **Single-Select Segmented Button（单选分段按钮）** 实现三种图标模式的切换：
    * `[ 自动获取 (Auto) ]` | `[ 自定义 (Custom) ]` | `[ 字母生成 (Text) ]`
  * 选中项自动显示 `Check` 图标并填充 `SecondaryContainer` 背景色。
* **辅助操作区**：
  * 当选中 `AUTO` 时：显示“重新抓取”文本按钮（`TextButton` with Refresh Icon）及当前图标来源标签（如 `AssistChip` 显示 `Apple Touch Icon · 180x180`）。
  * 当选中 `CUSTOM` 时：显示“从相册选择图片”按钮（`FilledTonalButton` with Image Icon），点击直接拉起 Android 系统 Photo Picker。
  * 当选中 `GENERATED` 时：自动提取当前输入的“网站名称”首字/首字母实时渲染预览。
* **文本输入框**：网址（URL）与名称（Title）均采用 MD3 `OutlinedTextField`，支持 `SupportingText` 展示 URL 校验提示。
* **结果反馈**：保存并触发桌面快捷方式更新后，底部弹出 MD3 `Snackbar`（提示：“已保存并请求更新桌面快捷方式”）。

### 3.3 形状与自适应图标规范 (Shapes & Adaptive Icons)
* **App 内展示形状**：统一采用 `Corner.Large` ($16\text{ dp}$) 或圆形剪裁展示图标预览。
* **桌面快捷方式适配**：
  * 输出给 `ShortcutManager` 的 Bitmap 需兼顾传统图标与 `IconCompat.createWithAdaptiveBitmap`。
  * 针对尺寸过小（如 $32 \times 32\text{ px}$ 的传统 Favicon）或非正方形的网站图标，不得直接强制拉伸铺满；应使用 MD3 风格的柔和背景底板（取自图标主色调或纯白/`Surface` 色）进行居中衬托，确保在系统启动器的圆形、圆角矩形、水滴形遮罩（Mask）下均不会裁切到核心 Logo 边缘（遵循核心安全区域直径 $66\text{ dp}$ / 总尺寸 $108\text{ dp}$ 的比例规范）。

---

## 四、 功能需求说明与业务规则

### 4.1 图标优先级与模式逻辑
系统摒弃复杂的隐式覆盖逻辑，采用**三种显式独立模式**（由用户在 Segmented Button 中指定，默认新建时为 `AUTO`）：

1. **`AUTO`（自动模式）**
   * **触发时机**：用户输入/修改完有效 URL 失去焦点时，或点击“重新抓取”按钮时。
   * **发现策略**：按清晰度与规范优先级解析 HTML 与标准路径：
     1. Web App Manifest (`manifest.json`) 中声明的最高分辨率图标（优先选择 $\ge 192 \times 192\text{ px}$ 的 PNG/WebP）。
     2. HTML `<head>` 中的 `apple-touch-icon` / `apple-touch-icon-precomposed`。
     3. HTML `<head>` 中带有 `sizes` 属性的大尺寸 `<link rel="icon">`。
     4. 网站根目录默认 `/favicon.ico`。
   * **回退机制 (Fallback)**：若网络超时、解析无结果或图片解码失败，`IconResolver` 在渲染时**自动回退至 `GENERATED` 字母图标算法**，并在 UI 界面以柔和提示告知用户“未能获取高清图标，已使用默认字母图标”，但模式枚举仍保持 `AUTO` 以便日后重试。
2. **`CUSTOM`（自定义模式）**
   * **触发时机**：用户点击选择本地图片并完成导入后，自动切换至 `CUSTOM` 模式。
   * **隔离保护**：在此模式下，即使修改了书签的 URL 或触发全局刷新，**绝不覆盖**用户已选的本地自定义图标。
   * **历史留存**：若用户从 `CUSTOM` 切回 `AUTO` 查看效果，原自定义图片路径仍保留在元数据中（除非书签被删除或导入新自定义图），再次切回 `CUSTOM` 时无需重新选图。
3. **`GENERATED`（字母生成模式）**
   * **生成规则**：提取书签标题的第一个有效字符（支持中文汉字、英文字母自动转大写、数字），结合域名/ID 哈希分配的 MD3 色板动态绘制高清 Bitmap。

### 4.2 桌面快捷方式同步机制
当用户在编辑面板修改了网站名称、URL 或切换/更换了图标并点击“保存”后，系统按以下标准流水线执行：
1. **持久化数据**：将新图标文件落盘至私有存储，更新 `BookmarkRepository` 中的元数据（递增 `imageVersion`）。
2. **生成最终位图**：调用 `IconResolver` 根据当前 `selectedIconSource` 生成标准化尺寸的最终 `Bitmap`。
3. **更新固定快捷方式**：
   * `ShortcutPublisher` 保持使用该书签原有的稳定 **UUID** 作为 Shortcut ID。
   * 构建新的 `ShortcutInfoCompat`，调用 Android 原生 `ShortcutManagerCompat.updateShortcuts(context, listOf(shortcutInfo))`。
   * 系统桌面启动器（Launcher）接收到更新广播后，根据自身刷新机制原地替换桌面图标与文字标签。
4. **点击路由与登录态保持**：
   * 桌面快捷方式的 `Intent` 始终指向 App 内部的透明路由 Activity，并仅携带书签 **UUID**（不把 URL 写死在静态 Intent 中，保证修改 URL 后旧快捷方式无需重建即可跳转新地址）。
   * 路由 Activity 根据 UUID 从 `BookmarkRepository` 读取最新 URL，调用 `ChromeTabLauncher` 启动 Chrome Custom Tabs，完整保留 Cookie 与登录态复用能力。

---

## 五、 系统架构与数据模型设计

### 5.1 核心模块职责划分

| 模块名称 | 主要职责 | 关键输入 / 输出 |
| :--- | :--- | :--- |
| **`BookmarkRepository`** | 保存、查询、更新和删除网站收藏元数据 | 输入：Bookmark 实体；输出：持久化状态 / 数据流 |
| **`SiteIconDiscoverer`** | 请求目标网页 HTML/Manifest，解析并按优先级排序候选图标 URL | 输入：网站 URL；输出：候选图标 URL 列表 |
| **`IconImporter`** | 封装 Android Photo Picker (`PickVisualMedia`)，安全读取用户所选图片流 | 输入：系统 Uri；输出：原始输入流 |
| **`IconProcessor`** | 图片解码、尺寸缩放、安全边距填充（Adaptive 适配）、格式统一转换为 WebP/PNG | 输入：原始流/Bitmap；输出：标准化高清 Bitmap ($512 \times 512\text{ px}$) |
| **`IconStore`** | 管理 App 私有目录下的图标文件缓存、落盘命名与过期文件清理 | 输入：标准化 Bitmap + UUID；输出：本地文件相对路径 |
| **`IconResolver`** | 核心决策器：根据书签 `selectedIconSource` 及文件可用性，输出最终展示图标 | 输入：Bookmark 实体；输出：最终可用 Bitmap |
| **`ShortcutPublisher`** | 封装 `ShortcutManagerCompat`，负责创建新桌面快捷方式与更新已固定快捷方式 | 输入：Bookmark + 最终 Bitmap；输出：系统级快捷方式同步结果 |
| **`ChromeTabLauncher`** | 构建 `CustomTabsIntent`，应用 MD3 主题色配置并启动 Chrome Custom Tabs | 输入：最新目标 URL；输出：拉起浏览器页面 |

### 5.2 数据持久化模型 (Data Schema)

每条网站收藏 (`BookmarkItem`) 保留全局唯一且不可变的稳定 `uuid`，并扩展以下图标管理字段：

```kotlin
enum class IconSourceMode {
    AUTO,       // 自动使用网站图标（失败时渲染层回退到 GENERATED）
    CUSTOM,     // 强制使用用户本地导入的图片
    GENERATED   // 强制使用 MD3 字母生成图标
}

data class BookmarkItem(
    val id: String,                         // 稳定 UUID，同时作为桌面 Shortcut ID
    val title: String,                      // 网站名称 / 快捷方式标签
    val url: String,                        // 目标网址
    val selectedIconSource: IconSourceMode, // 当前选中的图标模式
    val autoImagePath: String?,             // 网站图标在 App 私有目录中的相对路径
    val customImagePath: String?,           // 用户自定义图标在 App 私有目录中的相对路径
    val sourceUrl: String?,                 // 成功抓取到的网站图标原始远程地址（用于比对与展示）
    val imageVersion: Int = 1,              // 图片版本号（每次更新图片自增，用于破除内存/UI缓存）
    val lastFetchedAt: Long? = null         // 最近一次成功抓取网站图标的时间戳 (Epoch Millis)
)
```

* **存储分离原则**：
  * `BookmarkItem` 元数据可保存在轻量级本地存储（如 Room 数据库、DataStore 或 JSON/SharedPreferences）中。
  * **严禁将图片 Base64 或二进制数据写入 SharedPreferences**。所有图标文件统一保存在 App 内部私有目录：
    * 自动抓取图标：`context.filesDir/icons/auto/{uuid}_v{version}.webp`
    * 自定义图标：`context.filesDir/icons/custom/{uuid}_v{version}.webp`
  * 更新或删除书签时，`IconStore` 负责异步清理旧版本的废弃图标文件，防止存储膨胀。

---

## 六、 安全、权限与非功能性需求

### 6.1 权限最小化原则
1. **网络权限**：新增 `<uses-permission android:name="android.permission.INTERNET" />`，仅用于 `SiteIconDiscoverer` 访问目标网站抓取 HTML 标签与图标资源。
2. **零存储权限选图**：`IconImporter` 必须使用 Android 官方 **Photo Picker** (`ActivityResultContracts.PickVisualMedia`) 或系统文档选择器 (`OpenDocument`)。**严禁申请** `READ_EXTERNAL_STORAGE` 或 `READ_MEDIA_IMAGES` 等宽泛的相册读取权限。
3. **纯本地隐私承诺**：所有网站收藏数据、抓取的图标及用户导入的个人图片 100% 保存在设备本地私有沙盒中，不包含任何云端上传或第三方统计上报逻辑。

### 6.2 网络与资源安全限制
为防止恶意网站或异常巨型文件导致 App 内存溢出（OOM）或卡死，`SiteIconDiscoverer` 与下载器需执行严格风控：
* **超时控制**：连接超时 $\le 5\text{ 秒}$，读取超时 $\le 8\text{ 秒}$。
* **体积上限**：
  * HTML / Manifest 文本响应体读取上限：$\le 512\text{ KB}$（仅流式解析 `<head>` 部分即刻中断连接）。
  * 单张图标文件下载上限：$\le 5\text{ MB}$。
* **MIME 类型与解码校验**：仅接受 `image/png`、`image/jpeg`、`image/webp`、`image/x-icon`、`image/vnd.microsoft.icon`、`image/svg+xml`（阶段三）。解码时使用 `BitmapFactory.Options.inJustDecodeBounds` 预检尺寸，拒绝异常超大分辨率炸弹图（如长宽 $> 4096\text{ px}$）。
* **规范化缩放**：所有入库保存的图标经 `IconProcessor` 统一收敛至最大 $512 \times 512\text{ px}$。

---

## 七、 实施路线图与验收标准

### 7.1 阶段实施计划 (Phased Rollout)

* **阶段一：基础架构、自定义图标与桌面热更新 (MVP)**
  * 完成数据模型升级与 `IconStore` 私有目录存储机制。
  * 实现 MD3 编辑弹窗与 `SegmentedButton` 模式切换 UI。
  * 完成 `IconImporter`（系统图片选择器接入）、`IconProcessor` 基础缩放及 `GENERATED` MD3 字母图标升级。
  * 跑通 `ShortcutPublisher` 基于 `ShortcutManagerCompat.updateShortcuts()` 的桌面图标动态更新链路。
* **阶段二：网站图标自动发现、缓存与回退机制**
  * 实现 `SiteIconDiscoverer`：支持解析 HTML `<link rel="icon">`、`apple-touch-icon` 及根目录 `/favicon.ico`。
  * 完善网络请求风控（超时、大小限制）与异步加载状态指示（MD3 进度条）。
  * 跑通 `AUTO` 模式下抓取失败自动平滑回退至 `GENERATED` 图标的容错逻辑。
* **阶段三：高级格式兼容与体验打磨**
  * 增加 Web App Manifest (`manifest.json`) 深度解析支持。
  * 引入 SVG 矢量图标渲染与多图层 `.ico` 文件最高清帧提取。
  * 增加导入自定义图片时的 1:1 居中裁切预览与圆角/自适应遮罩预览。
  * 在设置或主页菜单中增加“批量刷新所有自动图标”功能。

### 7.2 最终验收标准 (Acceptance Criteria)
1. **单包多站独立图标**：安装单个 APK 即可在桌面创建多个不同网站的快捷方式，每个快捷方式拥有各自独立的网站图标或自定义图片。
2. **免重编动态换图**：用户在 App 内修改任意已有书签的图标（如从自动切换为自定义相册图片）并保存后，无需重新编译 APK 或删除桌面图标，系统桌面上的已固定图标即可完成原地更新。
3. **非 PWA 站点兼容**：针对未配置 PWA Manifest 的普通传统网站，依然能正常抓取 Favicon / Touch Icon 或优雅回退至 MD3 字母图标。
4. **登录态无缝复用**：无论图标如何切换或更新，点击桌面快捷方式后依然通过原书签 ID 路由并拉起 Chrome Custom Tabs，原有 Chrome 账号及网站 Cookie 登录态 100% 保持不受影响。
5. **合规性检查**：App 权限列表中不出现相册/存储读取权限；断网或输入无效网址时 App 无崩溃、无 ANR，UI 给出符合 MD3 规范的友好状态提示。