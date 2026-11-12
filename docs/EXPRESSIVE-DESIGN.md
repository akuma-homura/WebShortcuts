# Material 3 Expressive 设计契约

本文件规定 WebShortcuts 的基础主题、表面色阶、组件场景边界与表达性动效规则。业务页面仅组合 `ui/components/ExpressiveComponents.kt` 中的集中封装组件，不自行定义圆角动画、边框、按钮变形或分段色值。

## 1. 官方实现与版本

采用 `androidx.compose.material3:material3:1.5.0-rc01` 官方 Compose 实现，启用 `MaterialExpressiveTheme`、`MotionScheme.expressive()`、`MaterialShapes`、`SegmentedListItem`、`ContainedLoadingIndicator`、`LinearWavyProgressIndicator`、`IconButtonDefaults.shapes()` 与 `MediumExtendedFloatingActionButton`。实验性注解集中收敛在组件层内部，不向业务页面扩散。

## 2. 统一基础主题与表面层级（`Theme.kt`）

`WebShortcutsTheme` 统一提供 `ColorScheme`、`AppTypography`、`AppShapes`、`MotionScheme.expressive()` 以及语义化表面层级 `AppTheme.surfaces`（`AppSurfaces`），完整兼容跟随系统／浅色／深色、Android 12+ 壁纸动态色彩与五套 HCT 精选色板：

| 表面 / 角色 | 浅色模式映射 | 深色模式映射 | 适用场景 |
| --- | --- | --- | --- |
| `AppTheme.surfaces.canvas` | `surfaceContainer`（温润有色阶画布） | `surface`（深邃墨黑画布） | 首页、全屏编辑器、手动整理页与 `ExpressiveModalSheet` 底层背景 |
| `AppTheme.surfaces.card` | `surfaceContainerLowest`（纯净白卡） | `surfaceContainerHigh`（丝绒抬升容器） | `TonalCard`、`InteractiveTonalCard`、未选中连体项、胶囊搜索框 |
| `AppTheme.surfaces.inset` | `surfaceContainerLow`（内嵌微凹底面） | `surfaceContainerLow`（深陷内衬底面） | 卡片内部次级说明区、`ExpressiveTextField` 常态填充、前导图标底座 |
| `AppTheme.surfaces.elevated` | `surfaceContainerLowest` | `surfaceContainerHighest` | `ExpressiveAlertDialog`、裁切工作台模态窗 |
| `AppTheme.surfaces.scrolledBar` | `surfaceContainerHigh` | `surfaceContainer` | 滚动吸顶后的 `TopAppBar` |

字体保留系统字体与 CJK 回退，强化 `display` / `headline` / `titleLarge` 的 `Bold` 字重与微负字距（`-0.15sp` 至 `-0.5sp`），正文与辅助信息保持清晰易读，全量使用 `sp` 支持 200% 大字体无损缩放。

## 3. 组件场景规则（`ExpressiveComponents.kt`）

| 场景需求 | 封装组件 | 设计规则 |
| --- | --- | --- |
| 独立内容舞台、图标预览、文件说明、结果摘要、精确滑块 | `TonalCard` / `ExpressiveNoticeCard` | 默认使用 `surfaces.card`；支持 `inset` 内嵌层与 `accentTint` 图标动态微染色，严禁多层边框卡片套娃 |
| 首页网站卡片（列表 / 双列网格） | `InteractiveTonalCard` | 统一管理按压微缩与圆角收拢、多选高亮、长按语义与内部独立菜单点击 |
| 同组设置、打开浏览器、标签管理、排序与备份导出 | `ConnectedGroup` + `Connected*Item` | 封装官方 `SegmentedListItem`，首中尾圆角按 `index/count` 自动成型，前导图标配圆角柔和底座 |
| 互斥筛选与模式切换（来源、图标模式、预览遮罩、比例、合并/替换、升降序） | `ExpressiveConnectedChoices` | 常态为连接式分段胶囊，选中舒展为完整胶囊；150% 以上大字体自动切换为 `FlowRow` |
| 多选与可移除标签筛选 | `ExpressiveFilterChip`、`ChoiceChipSurface` | 保留独立移除热区（≥ 48dp）与按压/选中形状响应 |
| 主操作 / 次级操作 / 辅助操作 / 工具栏图标 | `ExpressiveButton`、`ExpressiveTonalButton`、`ExpressiveTextButton`、`ExpressiveIconButton`、`ExpressiveTonalIconButton`、`ExpressiveExtendedFab` | 全部接入官方 `shapes` 按压变形与弹簧显隐 |
| 搜索与表单输入 | `ExpressiveSearchBar`、`ExpressiveTextField` | 统一胶囊搜索框焦点边框过渡与圆角内衬输入框 |
| 弹窗、底部面板与主题徽章 | `ExpressiveModalSheet`、`SheetHeader`、`ExpressiveHeroEmblem`、`ExpressiveAlertDialog` | 底部面板统一 `canvas` 底色与胶囊拖拽条；徽章采用官方 `MaterialShapes.Cookie9Sided` |
| 真实任务加载与进度反馈 | `ExpressiveLoading`、`ExpressiveProgressBar` | 封装 `ContainedLoadingIndicator` 与 `LinearWavyProgressIndicator`，仅在任务执行期间启用 |

## 4. 表达性动效边界

空间几何变化统一采用 `MaterialTheme.motionScheme.fastSpatialSpec()`；色彩与不透明度变化统一采用 `fastEffectsSpec()`，避免颜色超调闪烁。

- **按压**：胶囊按钮与图标按钮使用官方 shape morph；`InteractiveTonalCard` 圆角收拢至 `16.dp` 并伴随 `0.986f` 触感微缩；分段胶囊收拢至 `10.dp`。
- **选中**：连体列表保留官方选中过渡；分段胶囊从 `8.dp` 连接缝舒展为 `24.dp` 独立胶囊；多选卡片圆角扩展至 `30.dp` 并点亮主色描边；色板圆盘选中时变形为圆角方章并弹出勾选徽标。
- **加载与进度**：仅在读取、导入、抓取图标或提交备份时展示多边形形变加载器或波浪进度条，静止态零循环动画。
- **弹层出现**：模态窗使用 `expressiveEntrance()` 轻微缩放与淡入进场，稳定后保持静止。
- **系统无障碍**：全部动画基于 Compose 原生动画上下文，自动遵守系统 `MotionDurationScale`（含关闭动画模式），所有交互热区不低于 `48dp`。
