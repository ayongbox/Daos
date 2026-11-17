# 模块地图

- 状态：已接受
- 关联：`docs/architecture/project-layout.md`、`.features/features.md`
- 用途：把 40 项功能落到具体模块与子域，作为排期与代码归属的唯一依据

## 后端

| 模块 | 子域 | 覆盖功能 |
|---|---|---|
| `daos-core` | `metadata` | F00-01 元数据模型、F00-03 字典与枚举、F00-04 资源与动作定义 |
| | `pipeline` | F00-02 三路编译管线 |
| | `auth` | F01-01 租户上下文契约、F01-06 权限裁决契约、F01-09 数据范围契约 |
| | `spi` | F00-06 扩展装配契约 |
| `daos-runtime` | `identity` | F01-02 组织与成员、F01-03 身份源适配、F01-04 平台级角色、F01-05 应用级角色、F01-07 审计日志、F01-08 配置中心 |
| | `data` | F02-01 数据服务、F02-02 查询编译执行、F02-03 结构演进、F02-06 附件与文件存储 |
| | `flow` | F03-06 BPMN 引擎封装、F03-08 流程人机交互 |
| | `integration` | F02-04 事件总线与 Outbox、F02-05 调度器、F02-07 开放 API 与凭证 |
| `daos-web` | `app` | 应用、菜单、发布编排（F03-01 的服务端部分） |
| | `designer` | F03-01 建模器 API、F03-02 表单设计器 API、F03-04 视图与报表 API、F03-05 BPMN 设计器 API |
| | `ops` | F05-01 可观测性接入、F05-03 部署初始化、F05-04 导出与备份入口、F05-05 升级与回滚入口、F05-07 安全基线 |
| `daos-license` | — | F05-02 商业授权与降级、F05-06 商业扩展包交付 |
| `daos-ext-*` | — | F04-01 脚本沙箱、F04-02 组件注册、F04-03 集成与通知（商业实现） |
| `daos-test` | — | F00-05 验证套件：越权矩阵、迁移回归、流程回归、金标准用例 |

## 前端

| 包 | 覆盖功能 |
|---|---|
| `packages/metadata` | 元数据与渲染树类型，由 schema v1 生成 |
| `packages/renderer` | F03-03 表单运行时、F03-07 高级字段渲染 |
| `packages/designer` | F03-01 建模画布、F03-02 表单画布、F03-05 流程画布 |
| `apps/console` | F03-04 视图与报表配置、F01-02~F01-07 管理界面、F05 运维界面 |
| `apps/runtime` | F03-03 表单运行页、F03-08 审批处理页 |
| `packages/ui` | UI 规范落地，不承载功能编号 |

## 边界说明

1. `core` 只放契约与纯函数编译，任何实现都不得下沉到 `core`；`auth` 子域只有接口，实现位于 `runtime/identity`。
2. `runtime/identity` 与 `runtime/data` 同模块不同子域，跨子域调用只走公开接口，不直接引用对方 `infra`。
3. `web/designer` 只做元数据 CRUD 与发布编排，不包含编译逻辑；编译一律调用 `core.pipeline`。
4. 流程引擎的 `ACT_*` 访问只允许出现在 `runtime/flow`，其他模块通过 `runtime/flow` 的接口使用。
5. 附件存储只允许通过 `core.spi` 的 `ObjectStorageService`，具体实现在 `runtime/data`。

## 覆盖检查

| 层级 | 功能数 | P0 | P1 | 归属 |
|---|---|---|---|---|
| L0 契约 | 6 | 6 | 0 | core、test |
| L1 地基 | 9 | 8 | 1 | core、runtime/identity、web/ops |
| L2 数据与引擎 | 7 | 4 | 3 | runtime/data、runtime/integration |
| L3 交互闭环 | 8 | 8 | 0 | web/designer、runtime/flow、frontend |
| L4 扩展 | 3 | 0 | 3 | ext-* |
| L5 交付 | 7 | 6 | 1 | web/ops、license |
| 合计 | 40 | 32 | 8 | 与 features.md 一致 |

## 排期含义

| 阶段 | 主要模块 |
|---|---|
| M0 契约冻结 | `core` 全部子域、`daos-test` 骨架 |
| M1 可登录可授权 | `runtime/identity`、`core/auth` |
| M2 可建表可录数 | `runtime/data`、`core/pipeline/ddl` |
| M3 可搭应用 | `web/designer`、`packages/designer`、`packages/renderer` |
| M4 可跑流程 | `runtime/flow`、`web/designer`（流程绑定） |
| M5 可交付 | `web/ops`、`license` |
| M6 可升级 | `license`、`web/ops` |
| M7 可扩展 | `runtime/integration`、`ext-*` |
