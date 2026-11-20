# 产品审计清单（M6.0.1）

- 状态：只读审计，未改任何代码
- 范围：frontend/{apps,packages}、daos-web/daos-core/daos-runtime、docs/design/ui-style-guide.md
- 用途：M6「产品级重构」①审计与设计系统 ②应用中心与工作台 ③三个核心设计器 ④运行与发布补强 的排期依据

## 1. 前端页面盘点（frontend/apps/console/src）

| 文件 | 行数 | 职责 | 交互形态 |
|---|---|---|---|
| main.tsx | 96 | 控制台入口：登录态门控 + 懒加载 LoginPage/ModelerPage | 无路由库，auth state 手工切换 |
| LoginPage.tsx | 107 | 租户/账号/密码登录 | 单卡片表单 |
| ModelerPage.tsx | 1761 | 控制台主壳：应用列表画廊 + 应用能力切换 + 数据建模器 + 迁移预览/历史/发布 | 侧边导航 + 表格/弹窗；建模器为「表列表 + 字段/索引/关联三个表格 Tab」（:1265-1335），无画布 |
| AppWorkbench.tsx | 388 | 应用工作台首页：hero 卡 + 4 统计卡 + 页面/数据表卡片 + 版本记录 Drawer + 发布 | 卡片列表 + Drawer；无健康检查 |
| FormDesigner.tsx | 1448 | 表单设计器：表单列表、字段面板、字段画布、属性面板、联动规则、子表单 | 三栏（左 240 字段面板 / 中列表式画布 / 右 280 属性卡）；点击添加 + ↑↓ 按钮排序（:814、:922-936），无拖拽、无实时预览 |
| ViewDesigner.tsx | 992 | 视图/报表设计器：列、筛选、排序、分组、聚合配置 | 左列表 + 右配置表格；无预览 |
| PageDesigner.tsx | 565 | 页面设计器：菜单→页面，block 布局（text/metrics/list/form） | 按钮添加 block（:370-379）+ ↑↓ 移动 + 块属性卡；无拖拽画布、无组件库、无预览 |
| AppRuntime.tsx | 1084 | 运行态：菜单导航 + 页面 block 渲染（列表/看板/指标/表单/文字）+ 记录详情/编辑 Drawer + 「运行态/设计预览」Segmented（:834） | 应用外壳 + 表格/看板 + Drawer |
| FormRuntime.tsx | 980 | 表单运行时：消费后端 render tree 渲染控件、联动状态计算、子表单 | 表单；联动引擎只取 `when[0]`（:504-505 附近同款逻辑在 FormDesigner） |
| AdminPage.tsx | 47 | 管理后台壳（用户/组织/字典 Tabs） | Tab + 表格 |
| AdminMembers.tsx / AdminDepartments.tsx / AdminDictionaries.tsx | 245 / 242 / 463 | 成员、部门、数据字典 CRUD 管理 | 表格 + 弹窗表单 |
| api.ts | 451 | fetch 封装（@daos/sdk ApiClient）+ 全部 API 类型 + tokenStore | 自带 RenderNode/RenderTree 类型（:111-132），与 @daos/renderer 重复 |
| appIcons.tsx | 296 | 应用图标集与选择器 | 图标网格选择 |
| pageLayout.ts / pageScaffold.ts | 67 / 73 | 页面 block 模型读写 / 数据表一键生成表单+视图+菜单 | 纯逻辑 |
| app.css | 20 | 全局样式 | — |
| frontend/apps/runtime/src/main.tsx | 27 | 独立运行时应用 | 空壳占位，仅 Empty「等待渲染树」 |

共性：无路由库（react-router 未引入），页面间靠 ModelerPage 内部 `capability` state 与整页 `return <AdminPage/>`（ModelerPage.tsx:864）切换；全 console 内联样式 328 处 `style={{}}`（14 个文件）。

## 2. 共享包盘点（frontend/packages）

| 包 | 行数 | 现状 |
|---|---|---|
| @daos/ui | 37 | **有效**：tokens 唯一来源（主色 #2563EB），设计 Token 刚统一 |
| @daos/metadata | 594 | **有效**：元数据类型全集（SchemaApp/Table/Form/View/Menu、20+ 字段配置、LinkageRule、FormEvent、权限类型） |
| @daos/sdk | 92 | **有效**：createApiClient + ApiError，被 console 复用 |
| @daos/app-shell | 150 | **有效**：ThemeProvider（token 灌入 antd）+ AppShell；但 AppShell 仅空壳 runtime 应用使用，console 自己拼 Layout/Header |
| @daos/renderer | 26 | **类型壳**：仅 RenderContext/RenderNode/RenderTree 类型，无渲染实现；仅 runtime 占位应用引用 |
| @daos/designer | 14 | **类型壳 + 死依赖**：仅 DesignerDocument 等类型；console package.json 声明依赖但源码 0 处 import |
| @daos/shared | 23 | **死包**：isUlid/assertNever/formatDateTime，无任何包依赖或引用 |

### @daos/ui tokens 与 docs/design/ui-style-guide.md 一致性

ui-style-guide.md 的令牌表（飞书色系）**大面积与新 token 冲突**，需在 M6.0.1 同步改文档：

| 项 | ui-style-guide.md | @daos/ui tokens | 判定 |
|---|---|---|---|
| colorPrimary | #3370FF | #2563EB | 冲突 |
| colorPrimaryHover / Active | #4E83FD / #245BDB | #3B82F6 / #1D4ED8 | 冲突 |
| colorSuccess / Warning / Error | #34C724 / #FF8800 / #F54A45 | #16A34A / #F59E0B / #EF4444 | 冲突 |
| 文字主/次/三级/禁用 | #1F2329 / #646A73 / #8F959E / #BBBFC4 | #111827 / #6B7280 / #9CA3AF / #D1D5DB | 冲突 |
| 分割线 | #DEE0E3 | #E5E7EB（app-shell 把 colorBorderSecondary 映射到 colorBorder） | 冲突 |
| 控件高度 | 32 | 36 | 冲突 |
| 顶部栏高 | 48 | 56（tokens.headerHeight） | 冲突 |
| 弹层圆角 | 10 | 12（tokens.radiusLg → Modal/Drawer） | 冲突 |
| 圆角 6 / 卡片 8 / 字号 12·14·18 / 阴影 | — | 一致 | 一致 |

另有：ui-style-guide.md 验收要求「新增组件先在控制台『组件预览』页登记」——该页面在 console 中**不存在**（全仓 grep 无「组件预览」）；文档要求「加载态优先骨架屏」——全仓 0 处 Skeleton，运行端均用 Spin。

## 3. 后端 API 盘点

### daos-web Controller（13 个）

| 路径前缀 | Controller | 能力 |
|---|---|---|
| /api/auth | AuthController | 登录/登出/当前会话 |
| /api/apps | AppController | 应用列表/创建/删除 |
| /api/apps/{appId}/schema | SchemaEvolutionController | 草稿读写、迁移预览/执行/回滚、迁移历史 |
| /api/apps/{appId}/releases | ReleaseController | 版本列表、回滚到指定版本 |
| /api/apps/{appId}/forms | FormController | 表单列表、`/{formCode}/render` 渲染树编译 |
| /api/apps/{appId}/forms/{formCode}/records | FormRecordController | 表单记录 CRUD |
| /api/apps/{appId}/tables/{tableCode}/records | DataRecordController | 表记录 CRUD + relation-options |
| /api/apps/{appId}/views | ViewController | 视图列表/详情/records/summary/aggregate |
| /api/apps/{appId}/menus | MenuController | 运行端菜单列表 |
| /api/meta | MetadataCatalogController | 字段类型模板 |
| /api/admin/members · /api/admin/departments | AdminMember/DepartmentController | 成员、部门管理 |
| /api/apps/{appId}/dictionaries | DictionaryAdminController | 数据字典管理 |
| /api/apps/{appId}/files · /api/files/download | FileController | 文件上传/下载/签名 URL |

### daos-core（129 文件）/ daos-runtime（77 文件）核心服务

- daos-core：元数据模型（SchemaApp 等）、IR（NormalizedSchema/MigrationPlan）、编译管线 SPI（DdlCompiler/MigrationCompiler/SqlDialect）、Ids、FieldConfigTemplates、ExtensionRegistry。
- daos-runtime 关键服务：
  - `engine/PublishService`：草稿→归一化→内容哈希→快照→切换生效指针，重复发布去重。
  - `engine/SchemaEvolutionService`：迁移预览、在线执行 DDL、发布、回滚与迁移历史；失败自动执行回滚 SQL；单机实例锁。
  - `engine/render/DefaultRenderCompiler` + `FormController /render`：表单→渲染树（前端 FormRuntime 的消费来源）。
  - `engine/SchemaNormalizer / SchemaContentHasher / ddl/DefaultMigrationCompiler·DefaultDdlCompiler·TableDdlFactory / dialect(H2·MySQL·Postgres)`。
  - `exec/DataQueryService·DataWriteService·DataAccessFacade`、`subform/SubformService`、`rollup/RollupService`、`view/ViewService`、`menu/MenuService`、`auth/AuthenticationService`、`identity/IdentityAdminService`、`permission/FieldMasker·PermissionRewriter`、`file/FileService`。
  - 存储双实现：InMemory* 与 Jdbc*（MetadataStore/SnapshotStore/MigrationStore 等 SPI）。

## 4. 重复与技术债

| # | 问题 | 证据 |
|---|---|---|
| 1 | `newId()`/`now()` 三处复制 | ViewDesigner.tsx:70、86；FormDesigner.tsx:43；ModelerPage.tsx:77、93。@daos/shared 有 isUlid 却无 newId |
| 2 | RenderNode/RenderTree 双份定义且字段不一致 | api.ts:111-132（含 label/span/readonly/required/hidden + props.linkages）vs @daos/renderer/src/index.ts:9-26（抽象版）；M6「统一 Renderer」前必须合并 |
| 3 | 内联样式泛滥 | 全 console 328 处 `style={{}}`（FormDesigner 69、ModelerPage 66、AppRuntime 38、ViewDesigner 38 等 14 个文件）；布局样式未沉淀到 @daos/ui |
| 4 | 手拼 Layout/Header 重复 | ModelerPage.tsx:868-923 与 AdminPage.tsx:12-31 几乎同款 Header 内联样式；@daos/app-shell 的 AppShell 无人用 |
| 5 | ready:false 占位导航 | ModelerPage.tsx:70-72：流程设计/权限与成员/应用设置三项置灰 |
| 6 | 空壳包/死依赖 | @daos/shared（0 消费者）；@daos/designer（console/package.json:14 声明依赖、0 import）；@daos/renderer 仅类型 |
| 7 | 空壳应用 | frontend/apps/runtime/src/main.tsx 仅 Empty 占位 |
| 8 | 联动 UI 与模型脱节 | schema-v1.ts:396-406 支持 `when[]` + AND/OR 多条件，但 FormDesigner.tsx:504-505、:1185 只编辑 `when[0]`，文案自认「一个条件一个动作」（:1170） |
| 9 | 风格文档过期 | ui-style-guide.md 令牌表整表与 @daos/ui 冲突（见 §2），且 #1677ff 禁用项等验收口径未落到检查脚本（frontend/tooling/scripts/check-forbidden-tokens.mjs 除外，未覆盖色值差异） |
| 10 | TODO/FIXME | 全仓 0 处；缺口全部靠 ready:false 或注释表达，无显式债单 |

## 5. 对照路线图现状与缺口表

### ① 审计与设计系统

| 功能点 | 状态 | 证据 |
|---|---|---|
| 设计 Token 统一（#2563EB） | 已有 | packages/ui/src/index.ts + app-shell ThemeProvider 灌入 antd |
| Token 文档一致 | 部分 | ui-style-guide.md 色值整表冲突（§2），需随 M6.0.1 更新 |
| 「组件预览」登记页 | 缺失 | console 无该页面（grep 无「组件预览」） |

### ② 应用中心与工作台

| 功能点 | 状态 | 证据 |
|---|---|---|
| 应用中心卡片 | 部分 | ModelerPage.tsx:1030-1092 画廊卡片已有（图标/发布态/表数）；无搜索/筛选/分组 |
| 创建向导 | 部分 | 仅单步「新建应用」Modal（ModelerPage.tsx:1359、api.ts:330），非多步向导 |
| 应用控制台/工作台 | 部分 | AppWorkbench.tsx 已有统计卡/页面卡/数据表卡/版本记录/发布；缺健康检查与快捷配置入口聚合 |
| 健康检查 5 项 | 缺失 | 全仓无「健康检查」代码（grep 仅命中图标 label appIcons.tsx:152） |

### ③ 三个核心设计器

| 功能点 | 状态 | 证据 |
|---|---|---|
| 建模器三模式 | 部分 | 仅「字段/索引/关联」三表格 Tab（ModelerPage.tsx:1265-1335），非三种编辑模式 |
| 关系可视化 | 缺失 | 关联为表格行（ModelerPage.tsx:832 起），无连线图/ER 画布（ui-style-guide.md 已有画布规范） |
| 表单拖拽画布 | 缺失 | 全仓 0 处 onDragStart/onDrop；FormDesigner 点击添加（:814）+ ↑↓ 排序（:922-936） |
| 表单分组/条件联动 | 部分 | 联动规则已有且模型支持多条件（schema-v1.ts:396-406），UI 仅单条件（FormDesigner.tsx:504-505）；画布只能加数据表字段（:781），group/tab/grid 布局组件（schema-v1.ts:85）无入口 |
| 页面画布组件库 | 部分 | PageDesigner 4 类 block 按钮（:370-379）；无拖拽、无组件库面板、无属性联动 |
| 统一属性面板 | 部分 | FormDesigner 280px 属性卡（:952）、PageDesigner 块属性已有；@daos/designer 空壳，无统一 PropertyPanel |
| 视图实时预览 | 缺失 | ViewDesigner 纯配置表格；预览只能进 AppRuntime「设计预览」（AppRuntime.tsx:834） |
| 表单/页面实时预览 | 缺失 | FormDesigner/PageDesigner 画布即配置列表，非所见即所得 |

### ④ 运行与发布补强

| 功能点 | 状态 | 证据 |
|---|---|---|
| 统一页面 Schema | 部分 | pageLayout.ts PageBlock（text/metrics/list/form）+ readBlocks 兼容旧菜单；仅 4 类 block，无组件化 Schema |
| 统一 Renderer | 部分 | 后端 DefaultRenderCompiler + FormRuntime 消费 render tree 已跑通；但 @daos/renderer 仅类型、api.ts 重复定义（§4-2）、runtime 应用空壳 |
| 发布前检查 | 部分 | 迁移预览（SchemaEvolutionController /migrations/preview、ModelerPage「预览迁移」:1122）+ SchemaNormalizer 校验已有；无表单/页面/权限维度的发布前检查清单（grep 无「发布前检查/Preflight」） |
| 四态处理 | 部分 | AppRuntime/FormRuntime 有 Spin/Empty/message.error 分散处理；无统一四态组件、0 处 Skeleton（与 ui-style-guide.md「骨架屏」要求相悖） |

### 缺口优先级建议（供 M6 排期）

1. 同步 ui-style-guide.md 与 @daos/ui tokens，落「组件预览」页（①收口）。
2. 合并 RenderNode/RenderTree 双定义，激活 @daos/renderer（④统一 Renderer 前置）。
3. 三个设计器补拖拽画布 + 统一 PropertyPanel（③主体工程，FormDesigner/PageDesigner 现为按钮式列表）。
4. 发布前检查 + 统一四态组件（④）。
5. 应用中心向导 + 健康检查 5 项（②）。
6. 清理死包/死依赖（@daos/shared、@daos/designer）与 newId/now 三处复制。
