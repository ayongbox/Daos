# 关键链路

- 状态：已接受
- 关联：`docs/architecture/overview.md`、`docs/architecture/module-map.md`
- 用途：定义四条主链路的调用顺序、权限注入点与失败回滚方式，作为接口冻结的依据

## 一、建模发布

触发：设计器保存并发布一个应用版本。

```text
console → web/app → core/pipeline → runtime/data → 数据库
```

| 步 | 动作 | 模块 | 失败处理 |
|---|---|---|---|
| 1 | 提交草稿：表、字段、表单、视图、菜单、权限点 | console → web/app | 校验失败直接返回，草稿保留 |
| 2 | 校验引用完整性：字段类型来自字典、权限点已注册、表单字段存在于表 | core/metadata | 同上 |
| 3 | 校验发布权限：当前用户对该应用具备发布动作 | core/auth | 403 |
| 4 | DDL 编译：按方言生成差异迁移计划（建表、增删改列、索引与同字段 id 改名） | core/pipeline/ddl | 不落库 |
| 5 | 结构演进执行：单连接事务内顺序执行迁移 SQL | runtime/data | 任一语句失败整体回滚 |
| 6 | 发布校验与快照生成：归一化、内容哈希 | core/pipeline | 执行迁移回滚 SQL 并标记发布失败 |
| 7 | 激活新版本指针，写入迁移台账 | runtime/data | 保持旧版本指针，台账记 FAILED |
| 8 | 失效元数据缓存，投递「应用已发布」事件 | runtime/integration | 重试至成功，事件幂等 |

约束：

- 迁移 SQL 与快照激活不在同一数据库事务内：迁移成功、发布失败时执行迁移计划提供的回滚 SQL 补偿；
- 每个应用串行执行演进；只允许回滚最近一次生效的迁移，首次发布不提供自动回滚；
- `id`、`tenant_id`、`version` 为保留列，禁止删除、改名或变更类型；删除表与主键变更暂不支持；
- 渲染树快照必须携带权限快照，同一元数据不同权限产出不同快照；
- 发布失败不得留下半成品结构，回滚 SQL 由迁移计划负责。

## 二、数据查询

触发：列表页、报表、导出。

```text
console/runtime → web → runtime/data → core/pipeline/query → 数据库
```

| 步 | 动作 | 模块 |
|---|---|---|
| 1 | 接收查询请求：视图 id、分页、排序、筛选 | web |
| 2 | 权限裁决：资源 + 动作 + 条件，得到数据范围 | core/auth（实现于 runtime/identity） |
| 3 | 查询编译：元数据 + 请求 → AST | core/pipeline/query |
| 4 | 独立重写阶段注入租户条件、数据范围、行级条件 | core/pipeline/query |
| 5 | 方言输出参数化 SQL，执行 | runtime/data |
| 6 | 字段级权限裁剪结果 | runtime/data |
| 7 | 返回分页结果，记录慢查询与审计 | web、runtime/identity |

约束：

- 租户与数据范围只在第 4 步注入，第 3 步保持纯函数；
- 第 2 步是唯一裁决点，任何模块不得自行放行；
- 越权请求必须返回空集或 403，不允许「先查后过滤」。

## 三、表单渲染

触发：打开表单、打开记录详情。

```text
console/runtime → web/designer → core/pipeline/render → console/runtime
```

| 步 | 动作 | 模块 |
|---|---|---|
| 1 | 请求渲染树：表单 id、记录 id、场景（新建/编辑/查看） | web |
| 2 | 权限裁决，得到字段级权限快照 | core/auth |
| 3 | 渲染树编译：元数据 + 权限快照 → 渲染树 | core/pipeline/render |
| 4 | 取记录数据，按字段权限裁剪 | runtime/data |
| 5 | 返回渲染树与数据 | web |
| 6 | 前端 `renderer` 按渲染树渲染 | packages/renderer |
| 7 | 提交时服务端重新校验全部规则 | web → runtime/data |

约束：

- 权限快照是编译入参，不在前端计算；
- 提交校验不得复用前端校验结果；
- 渲染树中不得出现框架特有的组件名或样式属性。

## 四、流程审批

触发：表单提交后按绑定关系发起流程，或手动发起。

```text
web → runtime/data（写入）→ runtime/flow（Flowable）→ 任务
```

| 步 | 动作 | 模块 |
|---|---|---|
| 1 | 提交表单，数据服务写入业务数据 | runtime/data |
| 2 | 按表单绑定关系启动流程实例，映射流程变量 | runtime/flow |
| 3 | 任务创建，按候选人或候选角色分配 | runtime/flow |
| 4 | 任务列表查询：仅返回当前用户有权处理的任务 | runtime/flow + core/auth |
| 5 | 打开任务：复用链路三渲染表单，叠加任务级字段权限 | web/designer |
| 6 | 提交流程操作：同意、驳回、转办、加签、撤回 | runtime/flow |
| 7 | 流程推进产生的业务数据变更，仍经数据服务与权限裁决 | runtime/data |
| 8 | 记录操作审计与流程实例快照 | runtime/identity |

约束：

- 流程变量只存标识与轻量值，业务数据以记录 id 关联，不复制整行；
- 撤回、转办、加签必须校验操作者身份与当前任务状态；
- 流程中的业务数据写入与普通写入走同一权限裁决，不设旁路。

## 公共规则

1. 主体与租户只来自登录会话（`SessionAuthInterceptor`），禁止信任 `X-User-Id` 之类可伪造请求头；
2. 所有写操作幂等键由请求携带，重复提交返回同一结果；
3. 跨模块调用只走公开接口，禁止直接访问对方 `infra`；
4. 链路内出现的缓存失效、事件投递均为最终一致，失败进重试与死信。

## 五、与 Baseon 的契约边界

Daos 以 BOM + starter 的方式消费 Baseon，并在 Web 契约上与其保持一致：

| 项 | 约定 |
|---|---|
| 响应体 | Controller 统一返回 `R<T>`，分页用 `PageQuery` / `PageResult` |
| 异常 | Daos 领域异常由 `ApiExceptionHandler` 映射为 `R` 错误码；其余异常交给 Baseon `GlobalExceptionHandler`，不并存两套格式 |
| 登录上下文 | 会话拦截器写入 Baseon `LoginUser`（`username` + `subjectId` + `tenantKey`），供日志、审计与 `SecurityUtil` 读取 |
| 权限校验 | 数据接口通过 Baseon `PermissionChecker` 的应用维度方法裁决动作权限，由 `DaosPermissionChecker` 适配到 `PermissionContext` |
| 链路 id | 审计事件通过 `TraceIdSupplier` 读取 `baseon-starter-trace` 的 traceId |
| 用户上下文请求头 | `baseon.web.user-context.enabled=false`，身份只来自会话 |

刻意保留的边界：

- **权限裁决唯一归属 Daos 引擎**：不启用 `baseon-starter-tenant` / `data-scope` 对动态表的自动 SQL 改写，避免与 `PermissionRewriter` 双过滤；
- **动态表直连 JDBC**：低代码表在运行期建立，没有 MyBatis-Plus 实体，DDL 与通用查询走方言渲染；
- **权限是应用级语义**：数据范围、行级条件、字段脱敏由 `PermissionContext` 承载，不映射为全局权限串。

契约对齐结果：

1. **字符串主体与租户**：Baseon `LoginUser` 增加 `subjectId` / `tenantKey`，`SecurityUtil` 增加同名读取方法（未设置时回退为数值 id 的字符串形式）；会话拦截器直接写入 ULID 与字符串租户，不再只写用户名。
2. **应用维度权限**：Baseon `PermissionChecker` 增加两参默认方法 `hasPermission(appId, permission)`，既有实现无需改动；`@RequiresPermission` 增加 `appId` 属性（支持 `#参数` SpEL）。
3. **解析时序**：权限上下文不再要求控制器先解析。`DaosPermissionChecker` 在切面/接口调用时从会话按应用惰性解析，并缓存到 `PermissionContextHolder`（按 `appId` 隔离）；数据接口以 `table:read` / `table:create` / `table:update` / `table:delete` 调用同一 checker 完成动作级裁决。

刻意不做的接线：Daos 使用自建会话登录，不引入 `baseon-starter-auth` 的 JWT 过滤器，因此不用 `@RequiresPermission` 注解，而是以编程方式调用其 `PermissionChecker` 契约；注解路径留给使用 starter-auth 的应用，两参方法与切面逻辑已被 Baseon 单测覆盖。`daos-runtime` 也不声明该 starter 依赖：其自动装配在缺少 `baseon.auth.secret` 时直接拒绝启动，与自建会话重复。

## 六、附件与文件存储

上传、下载与签名链路：

| 步 | 动作 | 位置 | 失败处理 |
|---|---|---|---|
| 1 | 校验 `file:upload`，按声明大小与类型快速拒绝 | web | 返回 400，不落盘 |
| 2 | 流式读取到临时文件，同时统计真实大小并计算 SHA-256 | runtime/file | 超限即中止并删除临时文件 |
| 3 | 按 `租户/应用/年/月/ULID` 生成存储键，写入对象存储 | runtime/file | 异常向上抛出，不写元数据 |
| 4 | 保存文件元数据（归属、大小、哈希、上传人） | runtime/file | 元数据即真相，字节缺失按不存在处理 |
| 5 | 会话下载校验 `file:download` 与租户/应用归属 | web | 跨归属一律 400，不泄露存在性 |
| 6 | 签名下载校验 HMAC、有效期与绑定关系 | web | 篡改或过期返回 400 |

接口：

- `POST /api/apps/{appId}/files`（multipart，`file:upload`）
- `GET /api/apps/{appId}/files/{fileId}`、`POST .../signed-url`、`GET .../content`（`file:download`）
- `DELETE /api/apps/{appId}/files/{fileId}`（`file:delete`）
- `GET /api/files/download?token=...`（公开路径，只认签名令牌）

约束：

- 附件字段值存 `FileReference` 数组（`id`、`fileName`、`contentType`、`size`），不存存储键；
- 默认本地存储（`daos.file.storage=local`），对象存储通过 Baseon `ObjectStorageService` 切换：设置 `storage=minio` 并引入 `baseon-starter-oss-minio`；
- 默认单文件上限 20 MB、允许类型清单可配置；签名链接默认 5 分钟有效；
- 未配置 `daos.file.download-secret` 时进程内生成临时密钥，重启后旧链接失效，多实例必须显式配置。

## 七、建模器与设计态权限

| 步 | 动作 | 位置 | 说明 |
|---|---|---|---|
| 1 | 创建应用 | `AppService` | 同时创建 `owner` 应用角色与 `APP` 资源权限，并绑定创建人 |
| 2 | 读取草稿 | `SessionPermissionService.resolve` | 有生效快照用快照权限；尚未发布时回退草稿权限 |
| 3 | 编辑表/字段/索引/关联 | 控制台 `ModelerPage` | 只改草稿，前端不做物理结构变更 |
| 4 | 保存草稿 | `PUT /api/apps/{appId}/schema/draft` | 要求 `app:design` |
| 5 | 预览/发布 | `GET/POST .../schema/migrations` | 预览要求 `app:design`，执行要求 `app:publish`，统一走 F02-03 结构演进 |

设计器数据源：

- 应用列表：`GET /api/apps`，摘要含表数量与状态；
- 逻辑类型与默认配置：`GET /api/meta/field-types`，默认配置由 `FieldConfigTemplates` 唯一维护，前端不再重复实现；
- 草稿读取：`GET /api/apps/{appId}/schema/draft`。

边界：草稿权限只在「没有生效快照」时生效；应用一旦发布，权限变更必须重新发布。应用列表目前是平台级视图，租户档位（`DatabasePlacement`）接入后再按租户过滤。
