# 元数据 Schema v1.1（第一批）

- 状态：草案 v1.1，待契约评审
- 版本：文档 `1.1`；契约字段 `schemaVersion` 仍为 `1`（本次为向后兼容增补，不破坏已有快照）
- 范围：表、字段、表单、权限四类，支撑 M0 归一化 IR 与三路编译快照出口
- 关联：ADR-0002、`docs/architecture/key-flows.md`、`docs/architecture/project-layout.md`
- 不含：视图、菜单、流程（第二批、第三批）
- 文件名沿用 `metadata-schema-v1.md`，仅为保持既有链接稳定

### v1.1 变更记录

| 编号 | 变更 | 原因 |
|---|---|---|
| A1 | 表引入所有权：`appId` → `ownerAppId`，新增 `shareable`、`storageMode`、`externalRefs` | 表必须可归属；跨应用复用走只读引用而非共享同一物理表 |
| A2 | 新增 `ExternalTableRef` 与 `refSnapshotId` | 引用必须锁定版本，否则上游发布导致下游运行时漂移 |
| A3 | 表级 `StorageMode`（`PHYSICAL` / `JSON` / `HYBRID`）与提升列规则；**禁止字段级存储模式** | 物理实现不得泄漏进语义层与表单层 |
| A4 | 新增物理分片维度（租户档位）与配额校验 | 一表一物理表会导致表数爆炸，必须在架构期确定分片边界 |
| A5 | 新增第 27 个逻辑类型 `lookup` | 单跳取值与跨行聚合语义不同，不能合并进 `rollup` |
| A6 | 计算字段执行语义分层：实时 / 连接 / 物化，含陈旧标记与依赖图 | 决定平台是否可用；物化与实时混用会导致超时与脏读 |
| A7 | 权限改为三层执行分离，合并公式修正为「行取并集、字段取最严」 | 行权限必须进 SQL，字段权限必须取最严，否则分页失真或数据泄漏 |
| A8 | 新增查询拒绝规则（隐藏字段筛选、公式进 WHERE、json 路径过滤等一律 400） | 静默降级在事后无法审计，必须显式失败 |
| A9 | 三路编译统一前置归一化 IR | 三个编译器各读一遍原始 JSON 必然产生语义漂移 |
| A10 | 新增发布快照闭包定义与回滚语义 | 发布单元是 App，快照必须自包含 |

设计口径：字段集合按已商用产品的实际能力项对齐（飞书多维表格、明道云、简道云的建模 / 表单 / 权限三块），**能配置的能力必须有承载字段**，不允许「只留两三个字段，剩下全塞 `ext`」；`ext` 只做实验性扩展，不能承载核心语义。核心字段一经写入本文档，即视为契约，Java 模型、TS 类型、渲染树、DDL 编译必须一一对应。

## 1. 通用约定

### 1.1 版本语义

| 概念 | 规则 |
|---|---|
| schemaVersion | 本契约版本号，当前 `1`；不兼容变更才升主版本 |
| draftVersion | 应用级草稿版本，每次保存自增，可覆盖 |
| releaseVersion | 发布版本，只增不改，发布时生成不可变快照 |
| currentRelease | 应用当前生效版本指针，回滚即切换指针 |
| refSnapshotId | 跨应用引用时锁定的上游快照 id，随本应用快照一起固化 |

- 草稿可反复修改，不影响运行中的发布版本；
- 发布 = 校验通过 + 生成快照闭包 + 结构演进成功 + 切换指针；
- 回滚只切指针，不回退已执行的 DDL，被删除的物理列保留并标记 `_deprecated`；
- **运行时只读快照，草稿永不进入运行链路**；快照一经生成即不可变，带 `contentHash`。

#### 1.1.1 版本判定分层

| 变更对象 | 影响版本 | 生效时机 |
|---|---|---|
| 契约本身（本文档定义的结构） | `schemaVersion` | 应用重新发布后生效 |
| 应用元数据内容（表 / 字段 / 表单 / 权限） | `releaseVersion` + `snapshotId` | 发布即生效，旧快照保留 |
| 物理结构（列 / 索引） | 绑定 `releaseVersion` 的迁移记录 | 只前进不回退 |

文档小版本（如 1.0 → 1.1）是编辑修订号，与 `schemaVersion` **不是同一序列**：v1.1 仅新增可选字段，`schemaVersion` 仍为 `1`。

#### 1.1.2 契约变更兼容矩阵

| 变更类型 | 兼容性 | schemaVersion | 是否需迁移 | 读取方要求 |
|---|---|---|---|---|
| 新增可选字段 / 可选配置项 | 兼容 | 不变 | 否 | 忽略未知字段 |
| 新增枚举值（逻辑类型、存储模式等） | 条件兼容 | 不变 | 否 | 未实现的分支显式报错，禁止静默降级 |
| 放宽校验（长度、数量上限变大） | 兼容 | 不变 | 否 | — |
| 收紧校验（上限变小、新增必填） | 数据破坏 | 不变 | 是：先清洗或二次确认 | 发布校验失败即阻断 |
| 修改显示名、描述、UI 提示 | 兼容 | 不变 | 否 | — |
| 逻辑类型同族转换 | 有条件 | 不变 | 是：列类型迁移 | 见 §3.5 同族表 |
| 逻辑类型跨族转换 | 破坏 | 不变 | 是：删旧增新 | 禁止静默转换 |
| 字段 `code` 变更 | 破坏 | 不变 | 是：删旧增新 | 二次确认 |
| 权限维度或合并公式变更 | 破坏 | +1 | 否 | 整应用重发布 + 越权矩阵回归 |
| 删除或重命名契约字段 | 破坏 | +1 | 是：元数据迁移脚本 | 旧快照按旧版本只读解释 |
| 修改既有字段语义 | 破坏 | +1 | 是 | 禁止用新语义解释旧快照 |

兼容变更不得改变已有快照的 `contentHash`；破坏变更必须提供迁移脚本与读取适配层，旧快照永久按生成时的 `schemaVersion` 解释。

#### 1.1.3 `schemaVersion` 升级的强制动作

1. 文档、Java 模型、TS 类型、渲染树、DDL 编译器五处同步，缺一不算完成；
2. 提供 `vN → vN+1` 元数据迁移脚本，可重入、可回滚；
3. runtime 保留旧版本读取适配层，旧快照只读可查，禁止写回新结构；
4. 越权矩阵、结构迁移回归、跨应用引用快照三类测试必须通过；
5. 升级单独成一个 commit，不与功能改动混提。

#### 1.1.4 读取方兼容约定

| 场景 | 行为 |
|---|---|
| 未知的可选字段 | 忽略，不报错 |
| 未知枚举值 / 未知逻辑类型 | 显式失败，禁止按默认值猜测 |
| `schemaVersion` 高于自身支持 | 拒绝加载并提示升级 |
| `schemaVersion` 低于自身支持 | 走适配层读取，只读 |

### 1.2 标识与命名

| 项 | 规则 |
|---|---|
| 元数据主键 | `id`，ULID，26 字符，全局唯一 |
| 表 `code` | 小写字母开头，`[a-z0-9_]{1,40}`，应用内唯一 |
| 字段 `code` | 同类规则，表内唯一；一经发布不可修改 |
| 表单 / 权限 `code` | `[a-z][a-z0-9_]{0,63}`，应用内按类型唯一 |
| 显示名 | 中文不超过 50 字符，可随时修改，不影响物理结构 |
| 说明 description | 不超过 500 字符 |
| 物理表名（应用表） | `app_{appCode}_{tableCode}`，总长 ≤64 |
| 物理表名（系统表） | `sys_{tableCode}`，由平台命名空间持有 |
| 物理表名（中间表） | `rel_{sourceTableCode}_{targetTableCode}`，计入应用配额 |
| 物理列名 | 直接使用字段 `code`；平台列与计算列使用 `_` 前缀 |
| 保留前缀 | `sys_`、`rel_`、`cy_`、`act_`、`_` 不允许被用户使用 |

外部引用**不产生新的物理表**：`ExternalTableRef` 只是对上游物理表的只读投影，查询在编译期改写为上游表名 + 固定字段投影 + 上游行权限的交集条件。

改名规则：显示名变更只写元数据；字段 `code` 变更视为「删旧增新」，必须二次确认并生成迁移。

### 1.3 自动列与系统字段

自动列由平台注入，用户不可删改：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | `varchar(26)` | ULID 主键 |
| `tenant_id` | `varchar(36)` | 租户隔离，联合索引前导 |
| `created_by` / `created_at` | `varchar(36)` / `datetime(3)` | 创建人与时间 |
| `updated_by` / `updated_at` | `varchar(36)` / `datetime(3)` | 更新人与时间 |
| `version` | `int` | 乐观锁 |
| `deleted` | `tinyint(1)` | 软删标记 |
| `_calc_dirty` | `bigint` | 物化计算字段陈旧标记（bitmask），仅含物化字段的表生成 |
| `_calc_computed_at` | `datetime(3)` | 最近一次重算时间，仅含物化字段的表生成 |
| `_ext` | `json` / `jsonb` | 非提升字段存储，仅 `JSON` / `HYBRID` 表生成 |

系统字段在**字段模型中以只读字段出现**（`system = true`），用于列表展示、筛选、表单只读展示，不允许用户编辑或删除。系统字段清单固定为：`created_by`、`created_at`、`updated_by`、`updated_at`、`creator_dept`（创建人部门，可关闭）。`_calc_*` 为内部列，不进入字段模型，仅由计算引擎读写。

### 1.4 元数据自身审计字段

四类 schema 均包含以下字段，本文档后文不再重复列出：

| 字段 | 类型 | 说明 |
|---|---|---|
| `createdBy` | `varchar(36)` | 创建人 |
| `createdAt` | `datetime` | 创建时间 |
| `updatedBy` | `varchar(36)` | 最近修改人 |
| `updatedAt` | `datetime` | 最近修改时间 |
| `deleted` | `boolean` | 逻辑删除，默认 `false` |
| `schemaVersion` | `int` | 契约版本，固定 `1` |

### 1.5 状态枚举

| 枚举 | 取值 | 说明 |
|---|---|---|
| `MetadataStatus` | `DRAFT` / `PUBLISHED` / `DISABLED` / `ARCHIVED` | 草稿、已发布、停用（保留数据不可写入）、归档（只读） |

`ext` 约定：每类 schema 保留 `ext json`，仅存放未纳入本契约的实验性配置；读取方必须容忍未知字段，写入方不得用 `ext` 承载核心语义。注意区分：`ext` 是**元数据**扩展，`_ext` 是**数据表**的稀疏列存储（见 1.8），二者无关。

### 1.6 公共枚举

| 枚举 | 取值 |
|---|---|
| `TableKind` | `MAIN`（主表）、`SUB`（明细/子表）、`AUX`（辅助表，不直接出现在菜单） |
| `RelationType` | `ONE_TO_MANY`、`MANY_TO_ONE`、`MANY_TO_MANY` |
| `OnDeleteAction` | `RESTRICT`、`CASCADE`、`SET_NULL` |
| `ValueType` | `STRING`、`NUMBER`、`BOOLEAN`、`DATE`、`DATETIME`、`JSON`、`ANY` |
| `SortDirection` | `ASC`、`DESC` |
| `LogicOperator` | `AND`、`OR` |
| `EffectType` | `ALLOW`、`DENY` |
| `SubjectType` | `ROLE`、`USER`、`DEPARTMENT`、`ORG`、`PUBLIC` |
| `ResourceType` | `APP`、`TABLE`、`FORM`、`VIEW`、`FLOW`、`MENU`、`FILE`、`FIELD` |
| `DataScopeType` | `ALL`、`SELF`、`DEPT`、`DEPT_TREE`、`CUSTOM` |
| `StorageMode` | `PHYSICAL`、`JSON`、`HYBRID` |
| `TableOwnership` | `APPLICATION`（应用自有）、`PLATFORM`（平台表，物理名前缀 `sys_`）；外部引用由 `externalRef` 非空表达 |
| `CalcExecMode` | `REALTIME`、`MATERIALIZED`、`STALE_TOLERANT` |
| `TenantTier` | `SHARED`、`DEDICATED` |

### 1.7 归一化 IR（编译唯一入口）

三路编译器不得各自读取原始元数据 JSON，必须消费同一份归一化 IR。原始元数据是"用户编辑态"，包含草稿、冗余、可选字段；IR 是"可执行态"，只保留已校验、已补全默认值、已解析引用的结果。

```text
SchemaBundle (raw, draft)
      │  normalize + validate
      ▼
Normalized IR ──┬──> DdlPlan
                ├──> QueryPlan 模板
                └──> RenderTree
```

IR 必须满足：

| 约束 | 说明 |
|---|---|
| 纯数据 | 不含 Spring、Jackson 注解、MyBatis 类型；可直接序列化与 diff |
| 引用全解析 | 字段 id、表 id、关联路径全部解析为强引用，禁止运行期再查元数据 |
| 默认值已补全 | 所有可选字段填实，读取方不需要 `null` 分支 |
| 依赖图已建 | 计算字段依赖、表关联、权限资源全部成图，环检测在 IR 构建期完成 |
| 可哈希 | IR 规范序列化后计算 `irHash`，用于快照比对与幂等发布 |

IR 是三路编译契约的唯一来源；任何一路需要的额外信息，必须先在 IR 中显式建模，不允许编译器私自回查原始 JSON。

### 1.8 存储模式（表级，禁止字段级）

| 模式 | 适用条件 | 写入 | 查询 |
|---|---|---|---|
| `PHYSICAL` | 默认，绝大多数表 | 每字段独立列 | 全部字段可筛选、排序、索引、关联 |
| `JSON` | 字段数 >50 且稀疏率 >50%，或字段集合按租户/用户高度差异化 | 全部进 `_ext` | 仅允许整体读取，不允许路径过滤 |
| `HYBRID` | 字段多但少数高频 | 稳定字段落列，其余进 `_ext` | 仅提升列可筛选、排序、索引 |

硬规则：

1. **存储模式只在表上声明，字段不得声明存储模式**。字段一旦能声明存储，前端表单、权限、渲染都会被迫感知物理布局。
2. `JSON` / `HYBRID` 表若某字段需要被筛选、排序、建索引、做关联或参与计算，**必须提升为真实列或 generated column**，提升清单写入 IR 的 `promotedColumns`。
3. 未提升字段禁止任何形式的 `json path` 过滤（`->>`、`JSON_EXTRACT` 等），查询编译期直接拒绝。
4. 提升列变更属于结构变更，需要发布与新 DDL；降级（列回到 json）必须先把数据迁回 `_ext` 并保留废弃列。
5. `HYBRID` 单表提升列 ≤30，且必须覆盖 `nameFieldId`、`ownerFieldId`、`deptFieldId`、所有关联外键。

### 1.9 物理分片与配额

分片维度**不是应用，而是租户档位**。应用是 DDL 与权限的自然边界，但它不是好的物理边界：按应用分库会让小应用产生大量小库，运维和连接成本反而更高。

| 概念 | 规则 |
|---|---|
| 分片维度 | `tenant_id` 档位（`TenantTier`）；同一租户的所有应用落在同一库 |
| `SHARED` | 多个中小租户共享一个数据库，靠 `tenant_id` + `appCode` 前缀隔离 |
| `DEDICATED` | 大租户独占数据库，可独立备份、独立扩缩容 |
| 迁移粒度 | 以租户为单位整体迁库，不允许单应用迁移 |
| 单库阈值 | 业务表 >2000 张触发迁库评估；>2500 张禁止新建应用 |
| 配额校验时机 | **应用创建/表创建时**校验，不是发布时 |

`DatabasePlacement`（平台内部结构，不进入用户可见元数据）：

```text
DatabasePlacement
├── tenantId
├── tier          SHARED / DEDICATED
├── databaseKey   物理库标识
└── appCodes      该库承载的应用编码集合
```

配额按租户累计：业务表数、索引数、中间表数、计算字段数全部计入。配额在创建阶段拒绝，避免用户建模完成后才在发布失败。

## 2. 表 SchemaTable

### 2.1 主字段

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `string` | 是 | ULID | 主键 |
| `ownerAppId` | `string` | 是 | — | **所有权应用**，不可为空、不可转移；字段/表单/权限仍用 `appId` 表示所属应用 |
| `ownership` | `TableOwnership` | 是 | `OWNED` | `OWNED`（应用自有）/ `EXTERNAL`（只读引用投影）/ `SYSTEM`（平台系统表） |
| `code` | `string` | 是 | — | 物理表名来源，在 owner 应用内唯一，发布后不可改 |
| `displayName` | `string` | 是 | — | 显示名 |
| `description` | `string` | 否 | `null` | 说明 |
| `icon` | `string` | 否 | `null` | 图标标识（前端图标库 key） |
| `color` | `string` | 否 | `null` | 主题色，来自设计令牌，不允许自由色值 |
| `groupCode` | `string` | 否 | `null` | 分组编码，用于左侧表导航分组 |
| `sortOrder` | `int` | 否 | `0` | 排序 |
| `kind` | `TableKind` | 是 | `MAIN` | 表类型 |
| `status` | `MetadataStatus` | 是 | `DRAFT` | 状态 |
| `system` | `boolean` | 是 | `false` | 平台内置表，不可删除；`SYSTEM` 表不属于任何普通应用 |
| `shareable` | `boolean` | 是 | `false` | 是否允许被其他应用引用为 External Table；`SYSTEM` 表恒为 `true` |
| `storageMode` | `StorageMode` | 是 | `PHYSICAL` | 表级存储模式，见 1.8 |
| `promotedColumns` | `string[]` | 否 | `[]` | `HYBRID` 下提升为物理列的字段 code，`PHYSICAL` 时必须为空 |
| `externalRef` | `ExternalTableRef` | 否 | `null` | 仅 `ownership = EXTERNAL` 时必填；引用在 Catalog 中就是本应用的一张只读表，见 2.5 |
| `nameFieldId` | `string` | 否 | 首个文本字段 | 记录标题字段，列表与关联展示用 |
| `subtitleFieldIds` | `string[]` | 否 | `[]` | 副标题字段，最多 3 个 |
| `defaultSortFieldId` | `string` | 否 | `created_at` | 默认排序字段 |
| `defaultSortDirection` | `SortDirection` | 否 | `DESC` | 默认排序方向 |
| `options` | `TableOptions` | 是 | 见 2.2 | 表级开关 |
| `fields` | `SchemaField[]` | 是 | `[]` | 字段集合，最多 200 |
| `indexes` | `IndexDef[]` | 否 | `[]` | 业务索引，最多 20 |
| `relations` | `RelationDef[]` | 否 | `[]` | 关联定义，最多 30 |
| `ext` | `object` | 否 | `{}` | 扩展 |
| 审计字段 | — | — | — | 见 1.4 |

### 2.2 TableOptions 表级开关

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `softDelete` | `boolean` | `true` | 启用逻辑删除；关闭时删除为物理删除，仅系统表允许关闭 |
| `auditEnabled` | `boolean` | `true` | 记录字段级变更审计 |
| `tenantScoped` | `boolean` | `true` | 是否注入 `tenant_id`；系统表允许关闭 |
| `allowImport` | `boolean` | `true` | 允许批量导入 |
| `allowExport` | `boolean` | `true` | 允许导出 |
| `allowBulkEdit` | `boolean` | `true` | 允许列表批量编辑 |
| `allowQuickCreate` | `boolean` | `true` | 关联字段允许快速新建 |
| `allowAttachment` | `boolean` | `true` | 允许附件字段 |
| `recordTitleExpression` | `string` | `null` | 标题拼接表达式，优先于 `nameFieldId` |
| `defaultPageSize` | `int` | `20` | 默认分页大小，可选 10/20/50/100 |
| `duplicateCheckFieldIds` | `string[]` | `[]` | 新建时的查重字段组合 |
| `ownerFieldId` | `string` | `null` | 负责人字段，供 `SELF` 数据范围使用 |
| `deptFieldId` | `string` | `null` | 部门字段，供 `DEPT` / `DEPT_TREE` 数据范围使用 |
| `enableComments` | `boolean` | `false` | 记录评论 |
| `enableAttachmentsPanel` | `boolean` | `true` | 记录详情附件面板 |
| `enableChangeHistory` | `boolean` | `true` | 记录变更历史面板 |
| `colorRuleEnabled` | `boolean` | `false` | 列表行/标签条件着色 |

### 2.3 IndexDef

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | ULID |
| `name` | `string` | 是 | 索引名，`idx_{tableCode}_{n}` |
| `columns` | `string[]` | 是 | 字段 code 列表，最多 5 列 |
| `unique` | `boolean` | 是 | 是否唯一 |
| `type` | `IndexType` | 是 | `BTREE` / `HASH` / `FULLTEXT` / `GIN` / `GIST` |
| `where` | `string` | 否 | 部分索引条件，仅 PostgreSQL 生效，MySQL 忽略并告警 |
| `description` | `string` | 否 | 说明 |

### 2.4 RelationDef

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | ULID |
| `code` | `string` | 是 | 关联编码，应用内唯一 |
| `displayName` | `string` | 是 | 显示名 |
| `type` | `RelationType` | 是 | 关联类型 |
| `sourceTableId` | `string` | 是 | 源表 |
| `targetTableId` | `string` | 是 | 目标表 |
| `sourceFieldId` | `string` | 否 | 外键字段；多对多可空 |
| `targetFieldId` | `string` | 否 | 目标展示字段 |
| `joinTableCode` | `string` | 否 | 多对多中间表，默认 `rel_{source}_{target}` |
| `onDelete` | `OnDeleteAction` | 是 | 默认 `RESTRICT` |
| `onUpdate` | `OnDeleteAction` | 否 | 默认 `RESTRICT` |
| `bidirectional` | `boolean` | 是 | 是否生成反向关联 |
| `reverseFieldId` | `string` | 否 | 反向字段 id，双向时自动创建 |
| `filterExpression` | `string` | 否 | 可关联记录过滤表达式 |
| `enabled` | `boolean` | 是 | 默认 `true` |

约束：

- 一张表最多 200 个字段、20 个索引、30 个关联；
- 关联只允许引用已发布的表，或已锁定 `refSnapshotId` 的 External Table；
- 多对多关系由平台隐式建中间表，用户不可见，中间表同样带自动列；
- 删除被引用字段时，必须先解除关联，否则发布校验失败。

### 2.5 ExternalTableRef 跨应用引用

外部引用**不是共享同一张物理表**，而是对上游表的只读投影：有自己的编码、字段投影、行过滤，以及锁定的上游快照。上游发布新版本不会自动影响引用方。

`ExternalTableRef` 内嵌在 `SchemaTable` 中，`ownership = EXTERNAL` 时必填。引用在 Catalog 中就是**本应用的一张表**（`id` / `code` / `displayName` 齐备，权限、视图、菜单统一以表寻址），但 `fields` 必须为空，可见字段来自 `fieldProjection`。这样避免"引用"与"表"两套寻址导致权限与渲染分叉。

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `sourceAppId` | `string` | 是 | — | 上游应用 |
| `sourceTableId` | `string` | 是 | — | 上游表 |
| `allowedActions` | `string[]` | 是 | `["read"]` | 只允许 `read`、`export`；出现写动作直接拒绝 |
| `fieldProjection` | `string[]` | 是 | — | 可见字段 code 集合，必须是上游已发布字段 |
| `filterExpression` | `string` | 否 | `null` | 额外行过滤，最终与上游行权限**取交集** |
| `refSnapshotId` | `string` | 是 | — | 锁定的上游快照，发布时固化 |
| `upgradePolicy` | `string` | 是 | `MANUAL` | `MANUAL`（显式升级）/ `FOLLOW_RELEASE`（跟随上游，需上游授权） |

硬约束：

1. **只读**。任何写路径（create/update/delete/bulk_update/import）命中 `ownership = EXTERNAL` 一律 403。
2. **权限不穿透**。引用方只能缩小可见范围，不能放大：`effective = 上游行权限 ∩ 引用 filterExpression ∩ 引用方行权限`。
3. **版本不漂移**。`refSnapshotId` 不由上游自动改写；`MANUAL` 策略下升级必须走一次本应用的发布。
4. **环检测**。引用图禁止成环（A→B→A），也禁止上游反向引用下游；IR 构建期检测。
5. **不复制数据**。引用不产生物理表，不产生数据副本；跨库引用（不同 `databaseKey`）由查询层做联邦查询，跨库引用超过 5 个时发布校验告警。
6. **上游删除保护**。被引用的表/字段在上游不可删除，需先解除所有引用（上游发布校验检查引用反向索引）。

### 2.6 表的三种身份

| 身份 | 归属 | 可写 | 可被引用 | 进快照 |
|---|---|---|---|---|
| `OWNED` | 唯一 `ownerAppId` | 是 | 仅当 `shareable = true` | 是，全量闭包 |
| `EXTERNAL` | 引用方应用（投影） | 否 | 否 | 是，仅存 `refSnapshotId` 与投影定义 |
| `SYSTEM` | 平台命名空间 | 否（用户侧） | 恒可 | 否，随平台版本 |

这张表是全篇最重要的约束：**Table 不是一等公民，App 才是；Table 是 App 的内部结构，跨应用复用只能走只读引用。** 其余设计（权限挂载点、快照闭包、物理命名、DDL 影响面）都是这一条的推论。

## 3. 字段 SchemaField

### 3.1 主字段

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `string` | 是 | ULID | 主键 |
| `appId` | `string` | 是 | — | 所属应用 |
| `tableId` | `string` | 是 | — | 所属表 |
| `code` | `string` | 是 | — | 物理列名，发布后不可改 |
| `displayName` | `string` | 是 | — | 显示名 |
| `description` | `string` | 否 | `null` | 说明 |
| `logicalType` | `FieldLogicalType` | 是 | — | 逻辑类型，见 3.4 |
| `config` | `FieldConfig` | 是 | 类型默认值 | 类型专属配置，见 3.5 |
| `constraints` | `FieldConstraints` | 是 | 见 3.2 | 结构约束 |
| `ui` | `FieldUiHint` | 否 | 见 3.3 | 展示提示 |
| `status` | `MetadataStatus` | 是 | `DRAFT` | 状态 |
| `system` | `boolean` | 是 | `false` | 系统字段 |
| `nameField` | `boolean` | 是 | `false` | 是否记录标题字段 |
| `sortOrder` | `int` | 是 | `0` | 字段顺序 |
| `groupCode` | `string` | 否 | `null` | 字段分组，用于表单/列表分组展示 |
| `ext` | `object` | 否 | `{}` | 扩展 |
| 审计字段 | — | — | — | 见 1.4 |

### 3.2 FieldConstraints 结构约束

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `required` | `boolean` | `false` | 必填；服务端与渲染树同时生效 |
| `unique` | `boolean` | `false` | 唯一；编译为唯一索引（含 `tenant_id`） |
| `indexed` | `boolean` | `false` | 普通索引 |
| `searchable` | `boolean` | `false` | 可被关键字检索 |
| `sortable` | `boolean` | `false` | 可作为排序字段 |
| `filterable` | `boolean` | `true` | 可作为筛选条件 |
| `immutable` | `boolean` | `false` | 创建后不可修改 |
| `caseSensitive` | `boolean` | `false` | 文本比较是否区分大小写（MySQL 排序规则层面处理） |
| `trimWhitespace` | `boolean` | `true` | 文本写入前去首尾空格 |
| `defaultValue` | `object` | `null` | 静态默认值，类型与 logicalType 匹配 |
| `defaultValueExpression` | `string` | `null` | 动态默认值表达式（如 `currentUser()`、`today()`），与 `defaultValue` 互斥 |
| `deprecated` | `boolean` | `false` | 已废弃；物理列保留、元数据不参与渲染 |
| `deprecatedAt` | `datetime` | `null` | 废弃时间 |

### 3.3 FieldUiHint 展示提示

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `placeholder` | `string` | `null` | 占位提示 |
| `helpText` | `string` | `null` | 字段下方帮助文本 |
| `tooltip` | `string` | `null` | 标签旁问号提示 |
| `prefix` | `string` | `null` | 输入前缀（货币符号等） |
| `suffix` | `string` | `null` | 输入后缀（单位等） |
| `width` | `int` | `null` | 列表列宽，单位 px |
| `align` | `string` | `left` | 列表对齐：`left` / `center` / `right` |
| `hiddenInList` | `boolean` | `false` | 列表默认隐藏 |
| `hiddenInDetail` | `boolean` | `false` | 详情默认隐藏 |
| `readonlyInForm` | `boolean` | `false` | 表单默认只读 |

### 3.4 逻辑类型与物理映射

v1.1 共 27 个逻辑类型：20 个基础类型 + 双值人员/部门（`member_multi`、`department_multi`）+ `rating`、`progress`、`location`、`barcode` + **`lookup`**。

`lookup` 与 `rollup` 的边界必须严格区分，不能合并：

| 类型 | 语义 | 输入 | 输出 | 典型例子 |
|---|---|---|---|---|
| `relation` | 引用目标记录（存 id） | — | 目标记录 id | 合同指向客户 |
| `lookup` | 取单跳关联的**单个字段值** | 1 个关联字段 + 1 个目标字段 | 标量 | 取客户的"行业" |
| `rollup` | 对一对多关联做**跨行聚合** | 1 个关联字段 + 聚合函数 + 源字段 | 标量 | 客户下所有合同的金额合计 |

| logicalType | 用户命名 | MySQL | PostgreSQL | 默认长度/精度 |
|---|---|---|---|---|
| `text_short` | 单行文本 | `varchar(255)` | `varchar(255)` | 255 |
| `text_long` | 多行文本 | `text` | `text` | — |
| `rich_text` | 富文本 | `text` | `text` | — |
| `email` | 邮箱 | `varchar(255)` | `varchar(255)` | 255 |
| `phone` | 电话 | `varchar(32)` | `varchar(32)` | 32 |
| `url` | 网址 | `varchar(512)` | `varchar(512)` | 512 |
| `number` | 数字 | `bigint` | `bigint` | — |
| `decimal` | 金额 / 小数 | `decimal(20,6)` | `numeric(20,6)` | 20,6 |
| `date` | 日期 | `date` | `date` | — |
| `datetime` | 日期时间 | `datetime(3)` | `timestamp(3)` | 毫秒 |
| `select` | 单选 | `varchar(64)` | `varchar(64)` | 64，存选项 code |
| `multi_select` | 多选 | `json` | `jsonb` | 选项 code 数组 |
| `boolean` | 布尔 | `tinyint(1)` | `boolean` | — |
| `member` | 成员 | `varchar(36)` | `varchar(36)` | 单值用户 id |
| `member_multi` | 成员（多选） | `json` | `jsonb` | 用户 id 数组 |
| `department` | 部门 | `varchar(36)` | `varchar(36)` | 单值部门 id |
| `department_multi` | 部门（多选） | `json` | `jsonb` | 部门 id 数组 |
| `attachment` | 附件 / 图片 | `json` | `jsonb` | 文件引用数组 |
| `relation` | 关联记录 | `varchar(36)`（多值 `json`） | 同左 | 目标记录 id |
| `lookup` | 引用取值 | 物化时随来源类型；实时时不落列 | 同左 | 单跳取单字段值 |
| `rollup` | 引用汇总 | 物化列，随来源类型 | 同左 | 跨行聚合，只读 |
| `formula` | 公式 | 物化时随结果类型；实时时不落列 | 同左 | 同行运算，只读 |
| `auto_number` | 自动编号 | `varchar(64)` | `varchar(64)` | 规则生成 |
| `rating` | 评分 | `tinyint` | `smallint` | 1–5 星 |
| `progress` | 进度 | `tinyint` | `smallint` | 0–100 |
| `location` | 地理位置 | `json` | `jsonb` | 名称 / 地址 / 经纬度 |
| `barcode` | 条码 / 二维码 | `varchar(512)` | `varchar(512)` | 扫描值 |

物理映射只存在于编译器，不写入元数据。

### 3.5 类型专属配置 config

| logicalType | config 字段 |
|---|---|
| `text_short` | `maxLength`(255)、`minLength`(0)、`pattern`、`patternMessage`、`showCount` |
| `text_long` | `maxLength`(5000)、`minLength`(0)、`rows`(3)、`showCount` |
| `rich_text` | `maxLength`(65535)、`toolbar`(字符串数组，如 `bold`/`italic`/`list`/`image`/`table`)、`allowImage`、`allowVideo`、`allowAttachment` |
| `email` | `maxLength`(255)、`allowedDomains`(字符串数组，空表示不限) |
| `phone` | `region`(`CN` / `INTL`)、`allowExtension`(是否允许分机号) |
| `url` | `maxLength`(512)、`allowedSchemes`(`http`/`https`)、`openInNewTab` |
| `number` | `min`、`max`、`step`(1)、`precision`(0)、`unit`、`thousandsSeparator`(true) |
| `decimal` | `precision`(20)、`scale`(6)、`min`、`max`、`currency`(ISO 4217，可空)、`format`(`plain`/`currency`/`percent`)、`roundingMode`(`HALF_UP` 等)、`thousandsSeparator` |
| `date` | `format`(`yyyy-MM-dd`)、`min`、`max`、`granularity`(`day`/`week`/`month`/`quarter`/`year`)、`defaultToday` |
| `datetime` | `format`(`yyyy-MM-dd HH:mm:ss`)、`min`、`max`、`timezone`(默认租户时区)、`granularity`(`second`/`minute`/`hour`/`day`)、`defaultNow` |
| `select` | `source`(`DICT`/`INLINE`)、`dictCode`、`options`、`displayStyle`(`dropdown`/`radio`/`button`)、`allowOther`、`colorMode`(`option`/`rule`) |
| `multi_select` | `source`、`dictCode`、`options`、`displayStyle`(`tag`/`checkbox`)、`maxSelected`、`allowCreate`、`colorMode` |
| `boolean` | `trueLabel`(是)、`falseLabel`(否)、`displayStyle`(`switch`/`checkbox`/`button`)、`defaultValue` |
| `member` | `multi`(false)、`scopeDeptIds`、`includeSubDept`(true)、`roleCodes`、`allowExternal`(false)、`displayStyle`(`select`/`avatar`) |
| `member_multi` | 同 `member`，`multi` 固定 `true`，另有 `maxSelected` |
| `department` | `multi`(false)、`scopeDeptIds`、`includeSubDept`(true)、`displayStyle`(`tree`/`select`) |
| `department_multi` | 同 `department`，`multi` 固定 `true`，另有 `maxSelected` |
| `attachment` | `maxCount`(9)、`maxSizeMb`(20)、`accept`(MIME/扩展名数组)、`storage`(`default`/`oss`/`minio`)、`imageOnly`(false)、`compressImage`(true)、`privateAccess`(true)、`allowCamera`(false) |
| `relation` | `targetTableId`（`EXTERNAL` 时填 `externalRefId`）、`displayFieldId`、`multiple`(false)、`maxSelected`、`filterExpression`、`onDelete`(`RESTRICT`)、`quickCreate`(true)、`showAs`(`select`/`table`/`card`)、`bidirectionalFieldId` |
| `lookup` | `relationFieldId`、`sourceFieldId`、`execMode`(`REALTIME`/`MATERIALIZED`)、`displayFormat`、`emptyValue`、`refreshOn`(`SOURCE_CHANGE`/`SELF_CHANGE`/`SCHEDULED`)、`stalenessTtlSeconds`(0) |
| `rollup` | `relationFieldId`、`sourceFieldId`、`aggregate`(`sum`/`count`/`avg`/`max`/`min`/`distinct_count`)、`filterExpression`、`precision`、`emptyAsZero`(true)、`execMode`(**固定 `MATERIALIZED`**)、`recalcMode`(`ON_SOURCE_CHANGE`/`SCHEDULED`/`MANUAL`)、`stalenessTtlSeconds`(0) |
| `formula` | `expression`、`resultType`(`number`/`text`/`date`/`boolean`)、`precision`、`execMode`(`REALTIME`/`MATERIALIZED`)、`volatile`(false)、`stalenessTtlSeconds`(0) |
| `auto_number` | `prefix`、`datePattern`(`yyyyMMdd`)、`serialLength`(4)、`resetCycle`(`never`/`day`/`month`/`year`)、`startValue`(1)、`suffix` |
| `rating` | `max`(5)、`allowHalf`(false)、`icon`(`star`/`heart`/`thumb`) |
| `progress` | `min`(0)、`max`(100)、`displayStyle`(`bar`/`circle`)、`showPercent`(true) |
| `location` | `provider`(`amap`/`google`/`manual`)、`allowManual`(true)、`showMap`(true)、`defaultZoom`(15) |
| `barcode` | `format`(`qr`/`code128`/`ean13`)、`scannerEnabled`(true)、`unique`(false) |

公共规则：

- `config` 与 `logicalType` 必须匹配，发布校验强制；
- 类型变更只允许同族转换（`text_short` ↔ `text_long`、`number` ↔ `decimal`、`select` ↔ `multi_select`、`member` ↔ `member_multi`、`department` ↔ `department_multi`），跨族变更视为删旧增新；
- 计算字段（`formula` / `lookup` / `rollup`）之间、以及计算字段与普通字段之间**禁止互转**，只能删旧增新；`lookup` 与 `rollup` 的语义边界见 3.4，不允许用 `rollup` 表达单值取值。

### 3.6 选项 OptionItem（`select` / `multi_select` 内联选项）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | ULID |
| `code` | `string` | 是 | 选项值编码，字段内唯一，发布后不可改 |
| `label` | `string` | 是 | 显示名 |
| `color` | `string` | 否 | 令牌色，枚举值来自设计系统 |
| `icon` | `string` | 否 | 图标 key |
| `sortOrder` | `int` | 是 | 排序 |
| `enabled` | `boolean` | 是 | 停用后不再可选，历史数据保留 |
| `defaultSelected` | `boolean` | 是 | 是否默认选中 |
| `description` | `string` | 否 | 选项说明 |

### 3.7 变更行为矩阵

| 操作 | 行为 |
|---|---|
| 新增字段 | 生成 `ADD COLUMN`，可空，默认值由 config 决定 |
| 改显示名 / 说明 / UI 提示 | 仅元数据 |
| 改 maxLength 变大 | 直接执行 |
| 改 maxLength 变小 | 提示可能截断，需二次确认 |
| 改 logicalType（同族） | 生成转换迁移 |
| 改 logicalType（跨族） | 禁止，需删旧增新 |
| 删除字段 | 物理列改名加 `_deprecated` 后缀，保留数据，元数据标记 `deprecated` |
| 改 code | 视为删除 + 新增，需二次确认 |
| 改唯一 / 索引约束 | 已存在冲突数据时发布失败，需先清洗 |

### 3.8 字段校验

| 项 | 规则 |
|---|---|
| 字段数 | 单表 ≤200，其中计算字段（`formula` / `lookup` / `rollup`）≤50 |
| 索引 | 单表 ≤20；唯一约束字段必须建索引 |
| 关联 | 单表关联字段 ≤30，禁止自关联套娃超过 2 层 |
| 公式 | 表达式只能引用**同表**字段；跨表取值必须经 `lookup` / `rollup`，不允许公式内直接跨表 |
| lookup | 只允许**单跳**（`relation` → 目标表一个字段）；禁止 lookup 一个 lookup；禁止 lookup 多值关联字段 |
| rollup | 聚合源不得是另一个 `rollup`（禁止二级聚合）；`count` 允许无 `sourceFieldId` |
| 计算依赖 | 依赖图必须无环，最大深度 5；环检测在 IR 构建期完成，草稿保存阶段即报错 |
| 命名 | `code` 不得与自动列重名，不得以 `_` 开头 |

### 3.9 计算字段执行语义

计算字段是平台可用性的分水岭。三类字段的物理执行方式必须按语义固定，不允许用户自由选择，否则会出现"实时算超时、物化算脏读"两头失控。

| 类别 | 执行方式 | 落列 | 生效时机 | 一致性 | v1 是否开放 |
|---|---|---|---|---|---|
| `formula` + `REALTIME` | 查询期表达式求值，进 Projection | 否 | 读取时 | 强一致 | 是（仅同行运算） |
| `formula` + `MATERIALIZED` | 写入期计算并落列 | 是 | 写时 | 强一致 | 是（`volatile = false`） |
| `lookup` + `REALTIME` | 查询期单跳 join | 否 | 读取时 | 强一致 | 是（目标表 ≤100 万行） |
| `lookup` + `MATERIALIZED` | 上游变更时重算并落列 | 是 | 异步 | 最终一致 | 是 |
| `rollup` | **只有物化**，无实时选项 | 是 | 异步 | 最终一致 | 需事件总线就绪 |

硬规则：

1. **`rollup` 禁止实时**。一页 20 条 × N 个 rollup × 全表扫描会直接超时；实时 rollup 在任何规模下都不可接受。
2. **`formula` 禁止进入 `WHERE` / `ORDER BY` / `GROUP BY`**，除非该字段为 `MATERIALIZED` 且已建索引。实时公式出现在过滤条件中由查询编译期拒绝。
3. **易变函数**（`now()`、`random()`、`uuid()`、当前用户上下文）只允许出现在 `REALTIME` 公式中；`MATERIALIZED` 公式引用易变函数时发布校验失败，除非显式标记 `volatile = true` 并接受"值不随时间变化"的语义。
4. **物化陈旧必须有标记**。所有 `MATERIALIZED` 字段带 `_calc_dirty`（bitmask）与 `_calc_computed_at`，重算未完成时读取方拿到的值标记为陈旧，前端显示灰点，导出与统计需显式确认。
5. **重算走队列**，不允许在写事务内同步重算下游链路。上游变更发布 `FieldChanged` 事件，队列按依赖拓扑序重算，失败进入死信并记录待修数据。
6. **依赖图是 IR 的一部分**：`calcDeps` 记录每个计算字段依赖的字段/表/关联，方向为「被依赖 → 依赖」，发布快照中固化。
7. **物化字段不可作为唯一约束或外键目标**，其值可能滞后。
8. **计算字段继承来源字段掩码**。`lookup` / `rollup` 的可见性跟随 `sourceFieldId`：来源字段对当前主体 `hidden` 时，计算值返回 `null`；来源字段被 `maskRules` 脱敏时，计算值同样脱敏。物化列中存的是**原始值**，掩码只在序列化层应用，因此导出、统计、OpenAPI 必须走同一条掩码链路，禁止直接读物化列。否则计算字段会成为绕过字段权限的旁路。

## 4. 表单 SchemaForm

### 4.1 主字段

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `string` | 是 | ULID | 主键 |
| `appId` | `string` | 是 | — | 所属应用 |
| `tableId` | `string` | 是 | — | 主表 |
| `code` | `string` | 是 | — | 表单编码，应用内唯一 |
| `displayName` | `string` | 是 | — | 显示名 |
| `description` | `string` | 否 | `null` | 说明 |
| `formType` | `string` | 是 | `RECORD` | `RECORD`（记录表单）/ `SEARCH`（查询条件）/ `APPROVAL`（审批表单） |
| `scenes` | `map<FormScene, FormLayout>` | 是 | 三场景 | `CREATE` / `EDIT` / `VIEW` |
| `fields` | `FormField[]` | 是 | `[]` | 字段树，支持分组、标签页、栅格、子表单 |
| `rules` | `ValidationRule[]` | 否 | `[]` | 校验规则 |
| `linkages` | `LinkageRule[]` | 否 | `[]` | 联动规则 |
| `events` | `FormEvent[]` | 否 | `[]` | 事件钩子 |
| `status` | `MetadataStatus` | 是 | `DRAFT` | 状态 |
| `version` | `int` | 是 | `1` | 表单版本 |
| `ext` | `object` | 否 | `{}` | 扩展 |
| 审计字段 | — | — | — | 见 1.4 |

### 4.2 FormLayout 场景布局

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `scene` | `FormScene` | — | 场景 |
| `columns` | `int` | `24` | 栅格列数，固定 24 |
| `labelPosition` | `string` | `top` | `top` / `left` / `right` |
| `labelWidth` | `int` | `120` | 标签宽度（left/right 生效） |
| `labelAlign` | `string` | `right` | `left` / `right` |
| `layoutMode` | `string` | `horizontal` | `horizontal` / `vertical` / `inline` |
| `size` | `string` | `middle` | `small` / `middle` / `large` |
| `gutter` | `int` | `16` | 栅格间距 |
| `dense` | `boolean` | `false` | 紧凑模式 |
| `width` | `string` | `100%` | 表单宽度 |
| `maxWidth` | `int` | `960` | 最大宽度 |
| `showSubmit` | `boolean` | `true` | 显示提交按钮 |
| `showReset` | `boolean` | `false` | 显示重置按钮 |
| `submitText` | `string` | `提交` | 提交按钮文案 |
| `resetText` | `string` | `重置` | 重置按钮文案 |
| `redirectAfterSubmit` | `string` | `null` | 提交后跳转：`stay` / `back` / `detail` / `url:{...}` |
| `subformSummary` | `boolean` | `true` | 子表单显示汇总行 |

### 4.3 FormField 表单项

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `string` | 是 | ULID | 表单项 id（非字段 id，支持同字段多场景复用） |
| `fieldId` | `string` | 否 | `null` | 对应 SchemaField；容器节点为空 |
| `component` | `FormComponent` | 是 | 自动推断 | 组件类型，见 4.4 |
| `label` | `string` | 否 | 字段显示名 | 覆盖标签 |
| `span` | `int` | 是 | `24` | 栅格占位 1–24 |
| `offset` | `int` | 否 | `0` | 左侧偏移 |
| `sortOrder` | `int` | 是 | `0` | 排序 |
| `placeholder` | `string` | 否 | `null` | 占位提示 |
| `helpText` | `string` | 否 | `null` | 帮助文本 |
| `tooltip` | `string` | 否 | `null` | 标签提示 |
| `readonly` | `boolean` | 是 | `false` | 只读 |
| `hidden` | `boolean` | 是 | `false` | 隐藏（不渲染） |
| `disabled` | `boolean` | 是 | `false` | 禁用（渲染但不可交互） |
| `requiredOverride` | `boolean` | 否 | `null` | 覆盖字段必填；`null` 表示沿用字段约束 |
| `defaultValue` | `object` | 否 | `null` | 表单级默认值，覆盖字段默认值 |
| `props` | `map<string, object>` | 否 | `{}` | 组件透传属性（不承载核心语义） |
| `style` | `map<string, string>` | 否 | `{}` | 样式覆盖，仅允许设计令牌变量 |
| `children` | `FormField[]` | 否 | `[]` | 子节点（`group` / `tab` / `grid` / `subform`） |
| `subformTableId` | `string` | 否 | `null` | 子表单引用的子表 |
| `subformFields` | `FormField[]` | 否 | `[]` | 子表单列定义 |
| `minRows` | `int` | 否 | `0` | 子表单最少行 |
| `maxRows` | `int` | 否 | `200` | 子表单最多行 |
| `allowAdd` | `boolean` | 是 | `true` | 允许新增行 |
| `allowDelete` | `boolean` | 是 | `true` | 允许删除行 |
| `allowSort` | `boolean` | 是 | `true` | 允许拖拽排序 |
| `showRowNumber` | `boolean` | 是 | `true` | 显示行号 |
| `showSummary` | `boolean` | 是 | `false` | 显示汇总行 |
| `summaryFields` | `map<string, string>` | 否 | `{}` | 汇总配置：字段 id → `sum`/`avg`/`count` |
| `tabKey` | `string` | 否 | `null` | 标签页 key（`tab` 容器使用） |
| `tabLabel` | `string` | 否 | `null` | 标签页标题 |
| `collapsible` | `boolean` | 否 | `true` | 分组可折叠 |
| `defaultCollapsed` | `boolean` | 否 | `false` | 默认折叠 |
| `columnCount` | `int` | 否 | `2` | 栅格容器每行列数 |

### 4.4 组件清单 FormComponent

| 分类 | 组件 |
|---|---|
| 基础输入 | `input`、`textarea`、`richtext`、`number`、`currency`、`percent`、`password` |
| 选择 | `select`、`multi_select`、`radio`、`checkbox`、`switch`、`cascader`、`tree_select` |
| 日期时间 | `date`、`datetime`、`date_range`、`time`、`month` |
| 组织人员 | `member`、`member_multi`、`department`、`department_multi`、`role_select` |
| 文件 | `attachment`、`image`、`image_group`、`signature` |
| 数据关联 | `relation`、`lookup`、`subform`、`rollup`、`formula`、`auto_number` |
| 高级 | `rating`、`progress`、`location`、`barcode`、`scanner` |
| 布局容器 | `group`、`tab`、`grid`、`divider`、`static_text`、`alert`、`description_list` |
| 系统 | `creator`、`create_time`、`modifier`、`modify_time`、`record_id` |

渲染约束：组件只做「展示与交互」，字段语义、校验、权限一律来自元数据；`renderer` 不认组件外的业务语义。

### 4.5 校验规则 ValidationRule

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | ULID |
| `name` | `string` | 否 | 规则名 |
| `targetFieldIds` | `string[]` | 是 | 作用字段，支持多字段 |
| `type` | `ValidationType` | 是 | 见下表 |
| `params` | `map<string, object>` | 否 | 类型参数 |
| `message` | `string` | 否 | 自定义提示 |
| `level` | `ValidationLevel` | 是 | `ERROR` / `WARNING` |
| `trigger` | `ValidationTrigger` | 是 | `BLUR` / `CHANGE` / `SUBMIT` / `MANUAL` |
| `scenes` | `FormScene[]` | 否 | 生效场景，空表示全部 |
| `enabled` | `boolean` | 是 | 默认 `true` |

`ValidationType` 与参数：

| 类型 | 参数 |
|---|---|
| `required` | 无 |
| `unique` | `scope`（`tenant` / `app` / `global`）、`ignoreEmpty` |
| `regex` | `pattern`、`flags` |
| `length` | `min`、`max` |
| `range` | `min`、`max`（数值或日期） |
| `compare` | `operator`（`gt`/`gte`/`lt`/`lte`/`eq`/`ne`）、`otherFieldId` 或 `value` |
| `expression` | `expression`（跨字段表达式） |
| `fileSize` | `maxSizeMb` |
| `fileCount` | `min`、`max` |
| `dateRange` | `minDate`、`maxDate`、`allowPast`、`allowFuture` |
| `phone` | `region` |
| `idCard` | `region`（`CN`） |
| `custom` | `handler`（注册的校验器编码） |

规则同时用于前端提示与服务端重校验，服务端是唯一裁决方。

### 4.6 联动规则 LinkageRule

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | ULID |
| `name` | `string` | 否 | 规则名 |
| `priority` | `int` | 是 | 数值越小越先执行，默认 100 |
| `enabled` | `boolean` | 是 | 默认 `true` |
| `scenes` | `FormScene[]` | 否 | 生效场景 |
| `logic` | `LogicOperator` | 是 | 条件间逻辑，默认 `AND` |
| `when` | `LinkageCondition[]` | 是 | 触发条件 |
| `then` | `LinkageAction[]` | 是 | 满足时动作 |
| `otherwise` | `LinkageAction[]` | 否 | 不满足时动作 |

`LinkageCondition`：`fieldId`、`operator`、`value`（静态值）、`valueFieldId`（引用字段）、`valueType`。

条件运算符全清单：

| 分组 | 运算符 |
|---|---|
| 相等 | `eq`、`ne` |
| 文本 | `contains`、`not_contains`、`starts_with`、`ends_with`、`empty`、`not_empty` |
| 数值/日期 | `gt`、`gte`、`lt`、`lte`、`between` |
| 集合 | `in`、`not_in`、`any_of`、`all_of`、`none_of` |
| 人员/部门 | `is_self`、`in_dept`、`in_dept_tree` |
| 附件 | `has_file`、`no_file` |

`LinkageAction`：`fieldId` / `targetFieldIds`、`action`、`value`、`expression`。

动作全清单：

| 动作 | 说明 |
|---|---|
| `show` / `hide` | 显示 / 隐藏（隐藏不参与提交） |
| `require` / `optional` | 必填 / 取消必填 |
| `readonly` / `editable` | 只读 / 可编辑 |
| `enable` / `disable` | 启用 / 禁用 |
| `set_value` | 赋静态值 |
| `clear_value` | 清空 |
| `set_expression` | 表达式赋值 |
| `set_options` | 替换选项（`select` / `multi_select`） |
| `filter_options` | 过滤选项 |
| `set_required_message` | 修改必填提示 |
| `trigger_validate` | 触发校验 |
| `trigger_fetch` | 触发远端数据拉取 |

联动只改变界面表现，不改变服务端权限；服务端仍按权限快照裁决，且所有 `set_value` 结果在提交时重新校验。

### 4.7 表单事件 FormEvent

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `string` | 是 | ULID |
| `event` | `FormEventType` | 是 | `BEFORE_RENDER` / `AFTER_LOAD` / `ON_FIELD_CHANGE` / `ON_ROW_ADD` / `ON_ROW_DELETE` / `BEFORE_SUBMIT` / `AFTER_SUBMIT` |
| `handlerType` | `HandlerType` | 是 | `SCRIPT` / `FORMULA` / `WEBHOOK` / `PLUGIN` |
| `expression` | `string` | 否 | `FORMULA` 使用 |
| `script` | `string` | 否 | `SCRIPT` 使用，受沙箱限制（商业扩展） |
| `url` | `string` | 否 | `WEBHOOK` 使用，仅允许白名单域名 |
| `method` | `string` | 否 | `GET` / `POST` |
| `headers` | `map<string, string>` | 否 | 请求头 |
| `async` | `boolean` | 是 | 是否异步，默认 `false` |
| `timeoutMs` | `int` | 否 | 默认 3000 |
| `retry` | `int` | 否 | 重试次数，默认 0 |
| `onError` | `ErrorStrategy` | 是 | `CONTINUE` / `ABORT`，默认 `ABORT` |
| `condition` | `string` | 否 | 执行条件表达式 |
| `enabled` | `boolean` | 是 | 默认 `true` |

### 4.8 场景差异

| 场景 | 字段集合 | 可写 | 说明 |
|---|---|---|---|
| `CREATE` | 全部参与创建的字段 | 是 | 支持默认值与联动赋值 |
| `EDIT` | 同 CREATE | 视字段权限 | 只读字段不提交 |
| `VIEW` | 全部字段 | 否 | 隐藏字段依据字段权限裁剪 |

三场景布局独立，字段集合一致；不一致由发布校验拒绝。

## 5. 权限 SchemaPermission

### 5.1 主字段

| 字段 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `id` | `string` | 是 | ULID | 主键 |
| `appId` | `string` | 是 | — | 所属应用 |
| `code` | `string` | 是 | — | 权限编码，应用内唯一 |
| `name` | `string` | 是 | — | 权限名称 |
| `description` | `string` | 否 | `null` | 说明 |
| `subject` | `PermissionSubject` | 是 | — | 主体，见 5.2 |
| `resource` | `ResourceRef` | 是 | — | 资源，见 5.3 |
| `actions` | `ActionType[]` | 是 | `[]` | 动作集合 |
| `effect` | `EffectType` | 是 | `ALLOW` | 允许 / 拒绝 |
| `condition` | `RowCondition` | 否 | `null` | 行级条件 |
| `dataScope` | `DataScope` | 否 | `null` | 数据范围 |
| `fieldMask` | `FieldMask` | 否 | `null` | 字段级权限 |
| `priority` | `int` | 是 | `100` | 冲突时数值大者优先 |
| `enabled` | `boolean` | 是 | `true` | 是否启用 |
| `effectiveFrom` | `datetime` | 否 | `null` | 生效时间 |
| `effectiveTo` | `datetime` | 否 | `null` | 失效时间 |
| `inherited` | `boolean` | 是 | `false` | 是否允许被下级资源继承 |
| `ext` | `object` | 否 | `{}` | 扩展 |
| 审计字段 | — | — | — | 见 1.4 |

### 5.2 PermissionSubject

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `type` | `SubjectType` | 是 | 主体类型 |
| `id` | `string` | 否 | 角色 / 用户 / 部门 id；`PUBLIC` 为空 |
| `includeDescendants` | `boolean` | 是 | 部门是否含下级，默认 `true` |

### 5.3 ResourceRef 与动作矩阵

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | `ResourceType` | 资源类型 |
| `appCode` | `string` | 应用编码 |
| `objectCode` | `string` | 对象编码：表 / 表单 / 视图 / 流程 / 菜单 / 文件 |
| `fieldCodes` | `string[]` | `FIELD` 类型时的字段集合 |

资源编码格式：`{type}:{appCode}:{objectCode}`，动作与资源解耦，鉴权引擎按「资源 + 动作」判定。

`EXTERNAL` 表在**引用方应用内**挂载权限，`appCode` 取引用方应用、`objectCode` 取该引用表的 `code`。引用方只能缩小可见范围：`effective = 上游行权限 ∩ 引用 filterExpression ∩ 引用方行权限`，任何试图放大的规则在 IR 构建期拒绝。

| 资源类型 | 动作 |
|---|---|
| `APP` | `view`、`design`、`publish`、`export`、`delete`、`manage_member` |
| `TABLE` | `create`、`read`、`update`、`delete`、`import`、`export`、`bulk_update`、`share` |
| `FORM` | `submit`、`view` |
| `VIEW` | `view`、`export`、`share` |
| `FLOW` | `start`、`approve`、`reject`、`withdraw`、`transfer`、`add_sign`、`view` |
| `MENU` | `view` |
| `FILE` | `upload`、`download`、`delete` |
| `FIELD` | `read`、`write`、`export` |

### 5.4 RowCondition 行级条件

```text
RowCondition
├── logic       AND / OR
├── items       ConditionItem[]
└── groups      RowCondition[]      // 嵌套分组，最多 3 层
```

`ConditionItem`：`fieldCode`、`operator`、`valueType`、`value`、`valueFieldCode`、`valueExpression`。

运算符与 4.6 保持一致，额外支持 `current_user`、`current_dept`、`current_dept_tree`、`current_tenant` 四个上下文变量。

### 5.5 DataScope

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `type` | `DataScopeType` | `ALL` | 范围类型 |
| `ownerFieldId` | `string` | `null` | 负责人字段；`SELF` 必需 |
| `deptFieldId` | `string` | `null` | 部门字段；`DEPT` / `DEPT_TREE` 必需 |
| `userIds` | `string[]` | `[]` | 自定义成员 |
| `deptIds` | `string[]` | `[]` | 自定义部门 |
| `roleIds` | `string[]` | `[]` | 自定义角色 |
| `includeSubDept` | `boolean` | `true` | 自定义部门是否含下级 |
| `withShared` | `boolean` | `false` | 是否包含共享给我的记录 |

数据范围在查询编译的独立重写阶段转为行级条件，与 `condition` 取交集。

### 5.6 FieldMask 字段级权限

| 字段 | 类型 | 说明 |
|---|---|---|
| `hiddenFieldIds` | `string[]` | 不渲染、查询不返回 |
| `readonlyFieldIds` | `string[]` | 渲染只读，服务端拒绝写入 |
| `requiredFieldIds` | `string[]` | 强制必填 |
| `maskRules` | `MaskRule[]` | 脱敏规则 |

`MaskRule`：`fieldId`、`type`（`phone` / `email` / `id_card` / `name` / `custom`）、`pattern`、`replacement`。

- 未列出的字段默认继承表级动作权限；
- `hidden` 优先级高于 `readonly`，同时出现按 `hidden` 处理。

### 5.7 动作权限裁决优先级

```text
显式拒绝 > 显式允许 > 角色继承 > 默认拒绝
```

同优先级冲突时：`priority` 大者胜；仍相同则 `DENY` 胜。平台超管与应用管理员不参与普通规则计算，走独立旁路并记录审计。

### 5.8 三层执行分离与合并公式

权限不是一个"大过滤器"，而是三层独立决策。任何一层拒绝即整体拒绝，且三层不可互相替代——把行权限放到应用层过滤，会让分页数、总数、聚合值全部失真；把字段权限放到 SQL 层裁剪，会破坏列投影与缓存。

| 层 | 决策内容 | 执行点 | 输入 | 输出 |
|---|---|---|---|---|
| 动作层 | 能否调这个接口/动作 | Command / Controller 入口 | 资源 + 动作 + 主体 | 允许 / 403 |
| 行层 | 能看见哪些记录 | **QueryCompiler 注入 WHERE** | 权限规则集合 + 上下文 | 追加的谓词 |
| 字段层 | 能读/写哪些列 | 反序列化（写） + 序列化（读） | 权限规则集合 + 字段 | 掩码 |

**动作层**：按 5.7 裁决；`effect = DENY` 命中直接 403，不允许"降级为只读"。

**行层**（SQL 注入，集合语义）：

```text
allowSet = ⋃ { rowScope(r) | r 命中 且 effect = ALLOW }
denySet  = ⋃ { rowScope(r) | r 命中 且 effect = DENY }

effectivePredicate = tenant_scope
                   AND (allowSet)          -- 交集：必须落在某个允许范围内
                   AND NOT (denySet)       -- 拒绝优先，排除
                   AND rule.condition      -- 规则自身的行条件
```

- `allowSet` 为空且存在 ALLOW 规则时，结果为空集，**不是全集**；
- 多个 ALLOW 规则之间是并集（用户可能通过角色、部门、共享多条路径可见同一行）；
- `denySet` 永远整体取反后与结果取交集；
- `DataScope` 编译为 `rowScope` 的一种，`withShared = true` 时额外并入共享集合；
- 软删、租户、逻辑删除条件由同一阶段注入，顺序固定：`租户 → 软删 → 行权限 → 用户条件`。

**字段层**（序列化，最严语义）：

```text
mask(field) = mostRestrictive { maskRule(r, field) | r 命中 }

严重度：hidden > readonly > required > 无约束
```

- **字段掩码取最严，不是取并集**。取并集会允许一条宽规则覆盖一条严规则，直接造成数据泄漏；
- 读：`hidden` 字段不出现在响应与渲染树，`maskRules` 按规则脱敏；
- 写：`hidden` / `readonly` 字段出现在提交体中一律 400，不静默丢弃（静默丢弃会让用户误以为已保存）；
- 未命中的字段继承表级动作权限，不默认开放。

### 5.9 查询拒绝规则

以下情况**必须显式失败并返回明确错误码**，禁止静默降级、静默裁剪或静默忽略：

| 场景 | 响应 | 原因 |
|---|---|---|
| 筛选 / 排序命中 `hidden` 字段 | `400 FIELD_HIDDEN` | 静默移除会让用户以为筛选生效 |
| 筛选 / 排序命中 `filterable = false` / `sortable = false` 字段 | `400 FIELD_NOT_FILTERABLE` | 契约必须显式 |
| 实时 `formula` / 任意 `rollup` 进入 `WHERE` / `ORDER BY` | `400 CALC_NOT_QUERYABLE` | 无索引导致全表扫描 |
| 对 `JSON` / `HYBRID` 未提升字段做路径过滤 | `400 JSON_PATH_FILTER_DENIED` | 查询规划器不可控 |
| 对 `EXTERNAL` 表发起写操作 | `403 EXTERNAL_READONLY` | 引用为只读投影 |
| 提交体包含 `hidden` / `readonly` 字段 | `400 FIELD_READONLY` | 防止越权写 |
| 请求字段超出 `fieldProjection` | `400 FIELD_OUT_OF_PROJECTION` | 引用投影不可绕过 |
| 跨应用引用的上游快照与 `refSnapshotId` 不一致 | `409 SNAPSHOT_DRIFT` | 运行时只读快照 |

## 6. 归一化 IR 与编译产物

编译链路固定，不允许绕过 IR：

```text
SchemaBundle (raw, draft)
      │ normalize + validate
      ▼
Normalized IR ──┬──> DdlPlan
                ├──> QueryPlan
                └──> RenderTree
```

| 层 | 内容 | 组件 | 粒度 | 确定性 |
|---|---|---|---|---|
| `SchemaBundle` | 用户编辑态，含草稿与未解析引用 | 元数据服务 | 应用 | — |
| `Normalized IR` | 已校验、已补全、已解析、已建图的可执行模型 | `core.metadata.ir` | 应用级 | `irHash` 稳定 |
| `DdlPlan` | 结构演进脚本 + 回滚脚本 | `core.pipeline.ddl` | 应用全量 | 逐字节一致 |
| `QueryPlan` | 参数化查询模板 + 权限谓词占位 | `core.pipeline.query` | 表 + 视图 | 逐字节一致 |
| `RenderTree` | 渲染树 + 权限掩码结果 | `core.pipeline.render` | 表单 + 权限快照 | 逐字节一致 |

约束：

- **三路编译器只读 IR**，禁止回查原始 JSON、数据库元数据或运行期配置；
- IR 构建阶段完成：默认值补全、引用解析、依赖图构建、环检测、权限规则展开、存储模式与提升列求解；
- DDL / Query / Render 编译器均为纯函数，同一 `irHash` 输出逐字节一致；
- 渲染树入参固定包含权限快照，同一表单在不同权限下产出不同树；
- CI 快照比对同时覆盖 IR 与三路产物，任一路变化必须能解释来源。

## 7. 发布快照闭包与回滚

发布单元是 **App**。快照必须自包含，"半引用"会让回滚与灰度失去意义。

快照闭包内容：

| 项 | 说明 |
|---|---|
| App 定义 | 应用元数据、配置、成员引用 |
| `OWNED` 表全量 | 表、字段、索引、关联、`storageMode`、`promotedColumns` |
| `EXTERNAL` 引用 | `ExternalTableRef` + `refSnapshotId`；**只存引用，不复制上游定义** |
| 表单 / 视图 / 权限 | 全量规则与掩码 |
| 计算依赖图 | `calcDeps`，含物化字段清单与重算策略 |
| 编译产物 | `irHash`、`DdlPlan`、`QueryPlan` 模板、`RenderTree` 种子 |
| 结构版本 | `ddlVersion`、已应用迁移 id 列表 |

发布流程（顺序固定）：

```text
校验草稿 → 构建 IR → 生成不可变快照(contentHash) → DDL 预演 → DDL 执行 → 切换 currentRelease
```

回滚语义：

- **只切 `currentRelease` 指针**，不回退已执行的 DDL；
- 删除的物理列改名为 `_deprecated_{n}` 保留数据，旧快照仍可读；
- 被回滚掉的字段在新快照中标记废弃，不再参与渲染与写入；
- 回滚不做数据反向迁移，数据修复是独立运维动作；
- 跨应用引用不回滚上游，下游的 `refSnapshotId` 保持不变。

**上游发布不影响下游**：下游锁定 `refSnapshotId`，升级必须显式触发本应用发布。这是引用模型成立的前提。

## 8. 边界与上限

| 项 | 上限 | 校验时机 |
|---|---|---|
| 单应用表数 | 500 | 建表 |
| 单表字段数 | 200 | 保存草稿 |
| 单表索引 | 20 | 保存草稿 |
| 单表关联 | 30 | 保存草稿 |
| 单表计算字段 | 50 | 保存草稿 |
| 计算依赖深度 | 5 | IR 构建 |
| 单表单字段数 | 200 | 保存草稿 |
| 单表单联动规则 | 200 | 保存草稿 |
| 单表单校验规则 | 200 | 保存草稿 |
| 单应用权限规则 | 2000 | 保存草稿 |
| 单应用外部引用 | 50 | 保存草稿 |
| 单条外部引用投影字段 | 200 | 保存草稿 |
| 行级条件嵌套 | 3 层 | 保存草稿 |
| 公式表达式长度 | 2000 字符 | 保存草稿 |
| `HYBRID` 提升列 | 30 | 保存草稿 |
| 单库业务表 | 2000 告警 / 2500 禁止新建 | 建应用 / 建表 |
| 重算队列积压 | 10 万条告警 | 运行期 |

超限在草稿保存或建表阶段直接拒绝，不进入发布流程。运行期阈值触发告警与限流，不静默降级。

## 9. 第一批验收

| 项 | 标准 |
|---|---|
| 覆盖 | 四类 schema 字段定义完整，无「待定」项；**27 个逻辑类型**的 config 全部有承载字段 |
| 归属 | 每张 `OWNED` 表必须且只能有一个 `ownerAppId`；`EXTERNAL` 表不产生物理表 |
| 引用 | `ExternalTableRef` 写操作返回 403；上游发布后下游 `refSnapshotId` 不变；引用成环被拒 |
| 存储 | `JSON` / `HYBRID` 未提升字段路径过滤返回 400；`PHYSICAL` 表 `promotedColumns` 必须为空 |
| 编译 | 同一份元数据在 MySQL 8.0 与 PostgreSQL 15 下 DDL 等价；同一 `irHash` 三路产物逐字节一致 |
| 一致 | 元数据 → TS 类型由生成器产出，与 Java 模型字段一一对应 |
| 越权 | 字段级 `hidden` 在渲染树与查询结果两处同时生效；`hidden` 字段筛选返回 400 |
| 权限 | 行权限进 SQL；`allowSet` 为空时结果为空集；字段掩码取最严；三层任一拒绝即整体拒绝 |
| 计算 | `rollup` 无实时路径；物化字段带陈旧标记；实时 `formula` 进 `WHERE` 返回 400 |
| 版本 | 草稿修改不影响当前发布版本；回滚只切指针；快照 `contentHash` 稳定 |
| 变更 | 删字段保留物理列并标记废弃，缩长度需二次确认 |
| 联动 | 联动产生的赋值在服务端重校验，越权赋值被拒绝 |
