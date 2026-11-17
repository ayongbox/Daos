# 工程目录与模块边界

- 状态：已接受
- 关联：ADR-0001、ADR-0002
- 适用范围：后端 Maven 多模块与前端 pnpm workspace

## 设计原则

目录结构承担架构约束，不能只靠自觉。每条目录划分都必须能回答「它阻止了哪种错误」。

## 后端

```text
Daos/
├── pom.xml                        parent，统一版本与插件
├── daos-core/                 契约与编译
│   └── src/main/java/io/github/ayongbox/daos/core/
│       ├── metadata/              八类元数据模型
│       ├── pipeline/              ddl / query / render 三路编译器
│       ├── auth/                  权限裁决契约（仅接口）
│       ├── tenant/                租户上下文契约
│       └── spi/                   扩展点定义
├── daos-runtime/              数据服务、查询执行、流程封装
├── daos-web/                  控制台 REST API、装配与启动
├── daos-license/              授权校验与版本能力
├── daos-ext-*/                商业扩展，闭源
├── daos-test/                 集成测试、越权矩阵、金标准用例
├── frontend/                      前端，不进 Maven reactor
└── docs/                          adr / architecture / design
```

### 模块职责

| 模块 | 负责 | 禁止 |
|---|---|---|
| `daos-core` | 元数据模型、三路编译、权限与租户契约、SPI | 依赖 Spring Web、依赖任何 starter 实现、访问数据库 |
| `daos-runtime` | 数据服务、查询编译执行、结构演进、流程封装 | 直接对外暴露 REST、定义新的元数据语义 |
| `daos-web` | REST 接口、参数校验、装配、启动 | 包含业务规则、直接写 SQL |
| `daos-license` | License 验签、Feature 判定、降级 | 依赖具体业务模块 |
| `daos-ext-*` | 商业能力实现 | 依赖 `runtime` 实现、修改社区表结构 |
| `daos-test` | 跨模块集成测试、越权反例、迁移回归 | 被任何生产模块依赖 |

### 依赖方向

```text
web → runtime → core
license → core
ext-* → core（只允许 SPI）
test → 全部（仅测试范围）
```

- 依赖只能单向下行，禁止反向与循环；
- `ext-*` 不得依赖 `runtime`，否则商业实现可绕过数据服务直连数据库；
- `core` 不得依赖任何 Spring Web 与 starter 实现，保证编译管线是纯函数、可快照比对；
- 由 ArchUnit 测试强制，违反即构建失败。

### 包内结构

每个业务模块内部统一四层：

```text
<module>/src/main/java/io/github/ayongbox/daos/<module>/
├── api/           对外接口与 DTO（仅 web 模块有）
├── domain/        领域模型与领域服务
├── infra/         仓储、外部适配、Baseon starter 集成
└── support/       模块内工具与配置
```

规则：`domain` 不依赖 `infra`；跨模块调用只走对方 `domain` 的公开接口，不直接引用 `infra`。

## 前端

```text
frontend/
├── pnpm-workspace.yaml
├── apps/
│   ├── console/                   控制台：应用管理、三个设计器、权限配置
│   └── runtime/                   运行时：表单渲染、列表、审批页
├── packages/
│   ├── metadata/                  元数据 TS 类型，由 schema v1 生成
│   ├── renderer/                  渲染树 → React 组件
│   ├── designer/                  画布与设计器组件（表单 / 建模 / BPMN）
│   ├── ui/                        飞书式 token 与基础组件封装
│   ├── sdk/                       API 客户端，按模块拆分
│   └── shared/                    工具、常量、hooks
└── tooling/                       eslint / tsconfig / vite 共享配置
```

### 包职责

| 包 | 负责 | 禁止 |
|---|---|---|
| `apps/console` | 控制台路由与页面组装 | 直接渲染业务字段、绕过 `renderer` |
| `apps/runtime` | 运行时页面，可独立部署 | 引入 `designer` 或控制台状态 |
| `packages/metadata` | 元数据与渲染树类型定义 | 手写与后端不一致的类型 |
| `packages/renderer` | 消费渲染树输出界面 | 读取设计器状态、感知编辑模式 |
| `packages/designer` | 三个设计器的画布与面板 | 直接调用数据接口写库 |
| `packages/ui` | 设计令牌与基础组件 | 定义业务语义组件 |
| `packages/sdk` | 接口调用、错误处理、鉴权头 | 包含 UI 逻辑 |

### 关键约束

1. `runtime` 与 `console` 是两个独立 app，不是路由拆分：运行时需独立部署，设计器代码不进入生产运行时产物。
2. `renderer` 与 `designer` 物理分离：`renderer` 只导出 `renderTree(tree, ctx)`，不导出底层组件，从结构上保证「设计器只产元数据、运行时只读渲染树」。
3. `metadata` 包由后端 schema v1 生成，schema 变更时前端类型编译直接失败，不依赖文档同步。
4. 颜色、圆角、阴影只能来自 `packages/ui`，业务包禁止写死色值；`#1677ff` 与 `2px` 圆角在 CI 中作为禁用项扫描。

## 构建

| 项 | 方式 |
|---|---|
| 后端 | Maven 多模块，`mvn verify` 全量校验，含 ArchUnit |
| 前端 | pnpm workspace，`pnpm -r build`，按包缓存 |
| 前后端 | CI 分两条流水线，schema 变更触发前端类型重新生成 |
| 本地开发 | Docker Compose 起 MySQL 8.0 与 Redis，前端 `apps/console` 代理到 `daos-web` |

## 验收

- ArchUnit 用例覆盖依赖方向与包结构规则；
- 前端构建产物中，`apps/runtime` 不得包含 `packages/designer` 代码；
- 新增模块必须先更新本文档的职责表，再建目录。
