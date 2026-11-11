# Material 3 Expressive 设计契约

本文件规定 WebShortcuts 的主题、组件选择与动效边界。页面组合 `ui/components` 的组件，业务逻辑不定义自己的颜色、圆角动画或按钮规格。

## 1. 官方实现与版本

采用 `androidx.compose.material3:material3:1.5.0-rc01` 的官方 Compose 实现。核对日期：2026-11-11。此版本为候选发布版；官方稳定版仍为 1.4.0。1.4 系列在发布前移除了 Expressive 实验 API，所以仅使用原有 Compose BOM 2025.12.00 不能获得本次使用的完整 Expressive 组件。Gradle 显式指定 Material3 版本，让依赖解析选择其所需的 Compose 传递依赖，不强制降级到旧版本。

依据：[AndroidX Material3 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-material3)、[Compose December 2025 发布说明](https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-december-release)。

## 2. 基础主题

`WebShortcutsTheme` 只在应用主题边界调用 `MaterialExpressiveTheme`，统一提供 ColorScheme、Typography、Shapes 和 `MotionScheme.expressive()`。继续支持系统／浅色／深色、Android 12 以上动态色彩和原有五套静态 HCT 调色板，已有偏好和备份格式保持兼容。

| 用途 | 颜色角色或排版角色 |
| --- | --- |
| 页面与底层背景 | `surface` |
| 常规内容卡片 | `surfaceContainerLow` + `onSurface` |
| 连体设置项、次级内容容器 | 官方 `SegmentedListItem` 默认颜色 |
| 弹窗 | `surfaceContainerHigh` |
| 主要操作 | `primary` + `onPrimary` |
| 已选内容、次级操作 | `secondaryContainer` + `onSecondaryContainer` |
| 错误说明 | `errorContainer` + `onErrorContainer` |
| 页面标题／弹层标题 | headline 系列，增强字重 |
| 分组标题 | title 系列 |
| 说明与正文 | body 系列 |
| 按钮、标签、辅助状态 | label 系列 |

静态配色提供完整的明暗角色配对，不以一组任意浅色十六进制数模拟深色主题。字体保留系统字体与中日韩回退，使用 sp 和原生字体缩放。圆角、间距、最大内容宽度和最小触控区集中在 Theme 与组件层。

依据：[Material 颜色角色](https://m3.material.io/styles/color/the-color-system/)、[Material 字体角色](https://m3.material.io/styles/typography/applying-type)、[MaterialExpressiveTheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MaterialExpressiveTheme.composable)。

## 3. 组件选择

| 需求 | 应用组件 | 规则 |
| --- | --- | --- |
| 独立内容、文件说明、预览、结果反馈 | `TonalCard` | 默认中性；明确的成功、重点或错误状态才使用强调色 |
| 首页快捷方式 | `InteractiveTonalCard` | 卡片统一管理按压、选择、长按及禁用语义，内部操作独立响应 |
| 同一组设置／浏览器／排序选项 | `ConnectedGroup` + `Connected*Item` | 使用官方 `SegmentedListItem`，首尾形状由 index/count 决定；单项也有正确形状 |
| 保存、导入、完成操作 | `ExpressiveButton` | 中号主要胶囊按钮，使用官方 `shapes` 重载实现按压变形 |
| 次级操作 | `ExpressiveTonalButton` | 低于主要操作的视觉权重 |
| 取消、辅助操作 | `ExpressiveTextButton` | 保持 48dp 触控空间 |
| 可选筛选条件 | `ExpressiveFilterChip`、`ChoiceChipSurface` | 只在按压或选中时改变形状；标签移除按钮仍是独立动作 |
| 标题 | `SectionHeader`、`SheetHeader` | 提供一致排版及无障碍 heading 语义 |
| 不确定耗时任务 | `ExpressiveLoading` | 仅任务执行时显示官方 `LoadingIndicator` |
| 已知完成比例 | 原生 determinate progress indicator | 展示实际进度，不伪造百分比 |
| 确认弹窗 | `ExpressiveAlertDialog` | 保留官方对话框焦点、语义和返回键行为 |

`TonalCard` 是应用对官方 Card 的封装名称，并非声称存在同名 Material API。卡片不替代所有列表；同类设置采用连体列表以明确分组。

依据：[Material Lists](https://m3.material.io/components/lists/overview)、[SegmentedListItem API](https://developer.android.com/reference/kotlin/androidx/compose/material3/SegmentedListItem.composable)、[Button API](https://developer.android.com/reference/kotlin/androidx/compose/material3/Button.composable)。

## 4. 交互动效

全局采用官方弹簧运动系统：空间变化使用 `fastSpatialSpec` 等 spatial token；颜色与透明度使用 effects token。颜色不能使用会超调的空间弹簧。

- 按压：按钮使用官方 shape morph；卡片与标签仅短暂收拢圆角。
- 选中：列表采用官方选中状态；卡片和标签协调形状与颜色变化。
- 加载：仅有真实任务时启用形状变化的 loading indicator。
- 弹窗出现：统一轻微缩放进入，内容稳定后不持续运动。
- 图标裁切与桌面安全区：保留精确几何比例，不对裁切数据或安全区做装饰性变形。

所有自定义动画使用 Compose 动画 API，继承系统 MotionDurationScale。关闭系统动画后，动画在后续帧完成；不另建忽略系统设置的计时器或无限动画。

依据：[Material Motion](https://m3.material.io/styles/motion)、[MotionScheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/MotionScheme)、[MotionDurationScale API](https://developer.android.com/reference/kotlin/androidx/compose/ui/MotionDurationScale)。

## 5. 可访问性与验证

使用至少 48dp 的可交互区域，`heightIn` 保证字号放大时内容可以增长。选项整行可点击，RadioButton／Switch 的内部 onClick 设为 null，避免重复焦点与重复动作。装饰图标没有重复朗读描述；有独立操作的图标保留明确描述。

测试重点包括：所有静态配色的明暗文字对比度、200% 字体下的设置与保存操作、卡片内独立动作不会误触打开网站、长按选择语义，以及原有外观／标签／排序／裁切／备份交互回归。现有 `PaletteContrastTest` 检查完整静态配色，新 `ExpressiveComponentsInteractionTest` 检查字号与交互边界。执行结果以本次构建与设备测试报告为准。

依据：[Compose Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)。
