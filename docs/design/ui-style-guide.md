# UI 风格规范（飞书式）

- 状态：已接受
- 关联：ADR-0001、F03-02、F03-03、F03-04
- 适用范围：控制台、设计器、运行时表单与视图

## 目标

视觉与交互向飞书 B 端产品（管理后台、多维表格、审批）靠拢，做到「同一屏内不产生割裂感」。只借鉴布局、配色与交互语言，不复制飞书商标、图标、字体、截图与产品文案。

## 设计令牌

设计令牌的唯一来源是 `frontend/packages/ui/src/index.ts` 的 `tokens`（由 `@daos/app-shell` 的 `ThemeProvider` 灌入 Ant Design 5 的 `ConfigProvider`），禁止在业务组件里写死颜色。下表与 tokens 保持同步（M6.0.2 起统一为蓝主色系）。

| 类别 | 令牌 | 值 |
|---|---|---|
| 主色 | `colorPrimary` | `#2563EB` |
| 主色悬浮 | `colorPrimaryHover` | `#3B82F6` |
| 主色按下 | `colorPrimaryActive` | `#1D4ED8` |
| 主色浅底 | `colorPrimaryBg` | `#EFF6FF` |
| 成功 | `colorSuccess` | `#16A34A` |
| 警告 | `colorWarning` | `#F59E0B` |
| 错误 | `colorError` | `#EF4444` |
| 页面背景 | `colorBgLayout` | `#F5F7FA` |
| 容器背景 | `colorBgContainer` | `#FFFFFF` |
| 浅灰填充 | `colorFillTertiary` | `#F5F7FA` |
| 分割线/边框 | `colorBorder` | `#E5E7EB` |
| 文字主 | `colorTextPrimary` | `#111827` |
| 文字次 | `colorTextSecondary` | `#6B7280` |
| 文字三级 | `colorTextTertiary` | `#9CA3AF` |
| 文字禁用 | `colorTextDisabled` | `#D1D5DB` |
| 反白文字 | `colorTextInverse` | `#FFFFFF` |
| 圆角小 | `radiusSm` | `6` |
| 圆角中（卡片） | `radiusMd` | `8` |
| 圆角大（弹层） | `radiusLg` | `12` |
| 阴影 | `shadowSecondary` | `0 2px 8px rgba(31,35,41,0.08)` |
| 字号 | `fontSizeSm` / `fontSizeMd` / `fontSizeLg` / `fontSizeHeading` | `12` / `14` / `16` / `18` |
| 控件高度 | `controlHeight` | `36` |
| 顶部栏高 | `headerHeight` | `56` |
| 工具栏高 | `toolbarHeight` | `48` |
| 状态区高 | `statusBarHeight` | `32` |
| 侧边导航宽 | `siderWidth` | `240` |

字体使用系统字体栈：`-apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`。不使用飞书品牌字体。

## 布局

| 区域 | 规范 |
|---|---|
| 左侧导航 | 宽 240px，背景 `#FFFFFF`，分组标题 12px 次级色，选中项用浅蓝底 `#EFF6FF` + 主色文字，不用整块深色 |
| 顶部栏 | 高 56px，白底，仅底部 1px 分割线，不放重色块 |
| 工具栏 | 高 48px，白底，承载当前模块的标题与主操作 |
| 内容区 | 背景 `#F5F7FA`，卡片白底、8px 圆角、无边框、轻阴影或不加阴影 |
| 底部状态区 | 高 32px 常驻，承载保存状态、校验结果与版本信息 |
| 卡片内边距 | 16 / 20 / 24，随密度切换，统一取 4 的倍数 |
| 表格 | 表头 `#F5F7FA` 底、12px 次级色文字；行高 48；分割线只用横向 |
| 抽屉 | 右侧滑出，宽 480 / 640，圆角 12，遮罩 `rgba(31,35,41,0.2)` |

## 交互语言

- 操作按钮默认隐藏，行或卡片 hover 时出现，避免页面满屏按钮；
- 优先行内编辑与右侧抽屉，少用整页跳转与居中弹窗；
- 危险操作用文字按钮 + 二次确认，不用红色大按钮；
- 表格批量操作以顶部浮条出现，不常驻；
- 空状态用轻量插画 + 一句引导，不使用大面积色块；
- 加载态优先骨架屏，不用全屏 spinner；空态 / 错误态 / 无权限分别使用 `EmptyState` / `ErrorState` / `ForbiddenState`，统一取自 `@daos/ui` 四态基础组件；
- 所有过渡 150–200ms，`ease-out`，不做夸张动画。

## 图标

统一使用线性图标（Lucide 或自建图标集），线宽 1.5，尺寸 16 / 20 / 24。不复用飞书图标集与品牌图形。

## 组件库决策

保持 Ant Design 5 + ProComponents，通过 Design Token 与布局层实现飞书式外观。

评估过的备选 `Arco Design`（字节系，视觉更接近飞书）：因缺少与 Formily 的现成适配，也没有 ProComponents 级别的表格/表单封装，会显著增加自定义成本，故不采用。

## 设计器专项

| 设计器 | 风格要求 |
|---|---|
| 表单设计器 | 左侧组件面板 240px、中间画布浅灰底 + 白卡、右侧属性面板 300px；画布组件选中用主色 1px 描边 + 四角手柄，不用粗边框 |
| 数据建模器 | 实体卡片白底 8px 圆角，关系连线 1.5px 次级色，选中态主色；画布背景用 12px 点阵网格 |
| BPMN 画布 | 使用 bpmn-js 默认图形 + 主色描边定制，节点保持白底细边框，不使用彩色填充 |

## 验收要求

- 任一页面不得出现 Ant Design 默认蓝色（`#1677ff`）与默认圆角（`2px`）；
- 颜色取值只能来自设计令牌（`@daos/ui` tokens，本文件令牌表与之同步）；
- 新增组件需先在控制台「平台管理 → 组件预览」页登记，确认视觉一致后再进入业务开发；四态组件（骨架屏 / 空态 / 错误态 / 无权限）统一取自 `@daos/ui`，不重复自建。
