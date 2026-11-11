# v1.5 实施与验证

目标：官方 Material 3 Expressive 全面 UI 重构、每快捷方式多浏览器 CCT、私有副本形式的 HTML/MHTML 离线阅读与桌面入口。

## 阶段与完成标准

| 阶段 | 交付 | 完成标准 |
| --- | --- | --- |
| 1 基础主题 | ColorScheme、Typography、Shapes、MotionScheme、表面层级 | 明暗/动态色与现有偏好兼容，使用官方 Expressive API |
| 2 公共组件 | TonalCard、交互卡片、连体列表、胶囊按钮、加载与弹窗 | 页面复用集中规则，选中/按压状态有清晰反馈，系统动画缩放生效 |
| 3 页面重构 | 首页/列表/网格、编辑器、外观/标签/排序/备份、裁切 | 保留原有流程，支持字体缩放及滚动访问 |
| 4 浏览器 | 能力发现、每项选择、正常会话、严格同包启动 | 新/旧数据、应用/桌面入口一致；缺失浏览器不跨账号回退 |
| 5 本地网页 | SAF 导入、受限私有文件存储、离线阅读、桌面路由 | 原授权失效仍可查看；MHTML 嵌入资源实际渲染；缺失与损坏可恢复 |
| 6 数据与回归 | schema 5、ZIP v2、迁移、并发与回滚 | 旧数据/备份可用，新增字段和字节往返，完整构建与设备测试 |

阶段 1–6 的代码与测试已实现。首次完整验证及发现的问题见下文；后续提交的准确状态以 [PR #7 的最新检查](https://github.com/akuma-homura/WebShortcuts/pull/7/checks)为准。

## 决策

- 当前稳定 Material 3 1.4.0 不包含完整 Expressive API，显式采用官方 `1.5.0-rc01` 发布候选版本。Compose 依赖通过 Gradle 正常解决，不强制锁回较低传递依赖。后续升级需继续执行相同回归门槛。[官方版本记录](https://developer.android.com/jetpack/androidx/releases/compose-material3)。
- 多账号以不同浏览器的正常配置隔离。相同浏览器共享自己的会话；应用不承诺在同一浏览器内创建多个账号容器。[Custom Tabs](https://developer.chrome.com/docs/android/custom-tabs)。
- 文件离线阅读独立于在线 CCT；禁用导入文件脚本和网络访问。独立 HTML 不连带导入资源目录，完整页面优先 MHTML。
- 备份包含私有文件内容，格式有明确迁移；旧桌面入口保留 UUID 并读取最新目标，避免把已失效 URI 固化到桌面。
- 本地文件/图标与元数据提交共用 mutationMutex；TransferGate 防止旧编辑、刷新与恢复操作互相覆盖。

## 验证证据

### 首次完整验证：2026-11-11

对应提交 `c521e0679342a119d4e8ff5a480f8ec89078fb6f`，见 [Android CI #53](https://github.com/akuma-homura/WebShortcuts/actions/runs/42960740724)。

| 检查 | 实际结果 |
| --- | --- |
| Debug APK 构建 | 通过，可安装 APK 已生成 |
| JVM 测试 | 42 个测试套件、285 项测试全部通过，0 跳过 |
| Android Lint | 0 错误、54 条非阻断建议；已检查新增功能相关项 |
| Release / R8 | 通过，生成未签名 Release APK |
| APK 权限检查 | 通过，未增加宽泛存储或全应用查询权限 |
| Android 35 设备测试 | 首轮 50 项中 47 项通过，3 项失败，原因和修复如下 |
| 设备截图 | 生成 24 张，已检查明暗首页、网格、编辑器、外观及大字体布局 |

首轮回归发现并修复的问题：

- 离线页面测试原先通过 `evaluateJavascript` 检查 DOM，但 HTML/MHTML sandbox 会阻止该测试脚本。改用 WebView 原生标题与文本查找，保持生产脚本限制；MHTML 的内嵌 PNG 和归档 CSS 通过实际渲染像素验证。网络观察覆盖完整页面加载。
- 编辑器曾在 Dialog 外读取 `LocalFocusManager`，导致清焦点操作落到底层 Activity。现从 Dialog 内获取焦点管理器，保存前清焦点；生命周期测试验证真实 IME Done、失焦、Activity 重建及菜单弹窗。
- 截图检查发现混合列表把本地页面计入“网站数”。现在线网站与离线网页独立计数，筛选状态显示匹配收藏数；截图等待异步图标，使用固定夹具顺序。

修复提交继续执行同一套构建、测试与权限门槛。合并前要求 [PR #7 的最新检查](https://github.com/akuma-homura/WebShortcuts/pull/7/checks)中 `Build, test and lint` 和 `Android 35 device tests` 均通过；测试报告、设备截图和 APK 随对应运行提供，避免把旧提交的通过状态当作新提交的结果。

真实 Chrome / Chrome Beta / Firefox 登录会话及 OEM 启动器属于目标设备验收范围，不由模拟 Intent 测试替代。应用使用各浏览器现有正常会话，不创建同一浏览器内部的独立账号容器。
