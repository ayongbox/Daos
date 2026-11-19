import { createApiClient } from '@daos/sdk';
import type {
  FieldConfig,
  FieldLogicalType,
  FormComponent,
  LinkageRule,
  SchemaApp
} from '@daos/metadata';

const TOKEN_KEY = 'daos.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY)
};

/** 会话失效事件：SDK 只负责发现 401，具体跳转由控制台决定。 */
export const AUTH_EXPIRED_EVENT = 'daos:auth-expired';

const client = createApiClient('', () => tokenStore.get(), {
  onUnauthorized: () => window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
});

export interface AppSummary {
  id: string;
  code: string;
  displayName: string;
  description?: string | null;
  icon?: string | null;
  status: string;
  activeReleaseVersion: number;
  changedSinceRelease: boolean;
  tableCount: number;
  updatedAt?: string | null;
}

export interface LoginResponse {
  token: string;
  tenantId: string;
  subjectId: string;
  expiresAt: string;
}

/** 刷新页面后用于校验本地令牌是否仍然有效。 */
export interface SessionInfo {
  sessionId: string;
  tenantId: string;
  subjectId: string;
  issuedAt: string;
  expiresAt: string;
}

export interface FieldTypeTemplate {
  logicalType: FieldLogicalType;
  defaultConfig: FieldConfig;
}

export interface MigrationStep {
  operation: string;
  tableCode: string;
  objectName: string;
  forwardSql: string;
  rollbackSql: string;
}

export interface MigrationPlan {
  appId: string;
  fromSnapshotId?: string | null;
  fromVersion: number;
  toSnapshotId: string;
  toVersion: number;
  steps: MigrationStep[];
}

export interface MigrationRecord {
  id: string;
  appId: string;
  fromVersion: number;
  toVersion: number;
  status: string;
  stepCount: number;
  operator?: string;
  createdAt: string;
  updatedAt: string;
  error?: string | null;
}

/** 表单列表项，来自应用当前生效快照。 */
export interface FormSummary {
  formId: string;
  formCode: string;
  displayName: string;
  tableId: string;
  tableCode: string;
  formType: string;
}

export interface RenderNode {
  id: string;
  component: FormComponent;
  fieldCode?: string | null;
  label?: string | null;
  span: number;
  readonly: boolean;
  required: boolean;
  hidden: boolean;
  props: Record<string, unknown>;
  children: RenderNode[];
}

export interface RenderTree {
  formId: string;
  scene: 'CREATE' | 'EDIT' | 'VIEW';
  schemaVersion: number;
  nodes: RenderNode[];
  props: {
    linkages?: LinkageRule[];
  };
}

export interface FormRenderResult {
  formId: string;
  formCode: string;
  displayName: string;
  tableId: string;
  tableCode: string;
  scene: string;
  tree: RenderTree;
}

export interface RecordPage {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Record<string, unknown>[];
}

export interface RelationOption {
  value: string;
  label: string;
}

/** 主表值 + 按子表单表单项 id 分组的子表行。 */
export interface FormRecordPayload {
  values: Record<string, unknown>;
  subforms: Record<string, Record<string, unknown>[]>;
}

export interface FormRecordDetail {
  record: Record<string, unknown>;
  subforms: Record<string, Record<string, unknown>[]>;
}

export interface ViewSummary {
  viewId: string;
  viewCode: string;
  displayName: string;
  tableId: string;
  tableCode: string;
  viewType: string;
}

export interface ViewColumnSpec {
  fieldId: string;
  fieldCode: string;
  label: string;
  visible: boolean;
  width?: number | null;
  sortOrder: number;
}

export interface ViewFilterSpec {
  fieldId: string;
  fieldCode: string;
  operator: string;
  value?: unknown;
}

export interface ViewSortSpec {
  fieldId: string | null;
  fieldCode: string | null;
  aggregation: string | null;
  direction: 'ASC' | 'DESC';
}

export interface ViewGroupSpec {
  fieldId: string;
  fieldCode: string;
  label: string;
}

export interface ViewAggregationSpec {
  function: 'count' | 'count_distinct' | 'sum' | 'avg' | 'min' | 'max';
  fieldId?: string | null;
  fieldCode?: string | null;
  displayName: string;
}

export interface ViewDetail {
  viewId: string;
  viewCode: string;
  displayName: string;
  tableId: string;
  tableCode: string;
  viewType: string;
  pageSize: number;
  columns: ViewColumnSpec[];
  filters: ViewFilterSpec[];
  sorts: ViewSortSpec[];
  groupBy: ViewGroupSpec[];
  aggregations: ViewAggregationSpec[];
}

export interface ViewPage {
  total: number;
  current: number;
  size: number;
  records: Record<string, unknown>[];
}

export interface ViewAggregatePage {
  total: number;
  current: number;
  size: number;
  rows: Record<string, unknown>[];
}

export interface MenuSummary {
  menuId: string;
  menuCode: string;
  displayName: string;
  icon?: string | null;
  menuType: 'VIEW' | 'FORM' | 'TABLE';
  targetId: string;
  targetCode?: string | null;
}

export const api = {
  login: (tenantId: string, username: string, password: string) =>
    client.post<LoginResponse>('/api/auth/login', { tenantId, username, password }),
  me: () => client.get<SessionInfo>('/api/auth/me'),
  logout: () => client.post<void>('/api/auth/logout'),
  listApps: () => client.get<AppSummary[]>('/api/apps'),
  createApp: (body: { displayName: string; description?: string; icon?: string }) =>
    client.post<AppSummary>('/api/apps', body),
  deleteApp: (appId: string) => client.delete<void>(`/api/apps/${appId}`),
  fieldTypes: () => client.get<FieldTypeTemplate[]>('/api/meta/field-types'),
  draft: (appId: string) => client.get<SchemaApp>(`/api/apps/${appId}/schema/draft`),
  saveDraft: (appId: string, app: SchemaApp) =>
    client.put<void>(`/api/apps/${appId}/schema/draft`, app),
  preview: (appId: string) =>
    client.get<MigrationPlan>(`/api/apps/${appId}/schema/migrations/preview`),
  apply: (appId: string) =>
    client.post<MigrationRecord>(`/api/apps/${appId}/schema/migrations`),
  history: (appId: string) =>
    client.get<MigrationRecord[]>(`/api/apps/${appId}/schema/migrations`),
  rollback: (appId: string, migrationId: string) =>
    client.post<MigrationRecord>(
      `/api/apps/${appId}/schema/migrations/${migrationId}/rollback`
    ),
  forms: (appId: string) => client.get<FormSummary[]>(`/api/apps/${appId}/forms`),
  renderForm: (appId: string, formCode: string, scene = 'CREATE') =>
    client.get<FormRenderResult>(
      `/api/apps/${appId}/forms/${encodeURIComponent(formCode)}/render?scene=${scene}`
    ),
  listRecords: (appId: string, tableCode: string, current = 1, size = 20) =>
    client.get<RecordPage>(
      `/api/apps/${appId}/tables/${encodeURIComponent(tableCode)}/records?current=${current}&size=${size}`
    ),
  createRecord: (appId: string, tableCode: string, values: Record<string, unknown>) =>
    client.post<{ id: string }>(
      `/api/apps/${appId}/tables/${encodeURIComponent(tableCode)}/records`,
      values
    ),
  createFormRecord: (appId: string, formCode: string, body: FormRecordPayload) =>
    client.post<{ id: string }>(
      `/api/apps/${appId}/forms/${encodeURIComponent(formCode)}/records`,
      body
    ),
  formRecord: (appId: string, formCode: string, recordId: string) =>
    client.get<FormRecordDetail>(
      `/api/apps/${appId}/forms/${encodeURIComponent(formCode)}/records/${encodeURIComponent(recordId)}`
    ),
  updateFormRecord: (
    appId: string,
    formCode: string,
    recordId: string,
    version: number,
    body: FormRecordPayload
  ) =>
    client.put<{ affected: number }>(
      `/api/apps/${appId}/forms/${encodeURIComponent(formCode)}/records/${encodeURIComponent(recordId)}?version=${version}`,
      body
    ),
  deleteFormRecord: (appId: string, formCode: string, recordId: string, version: number) =>
    client.delete<{ affected: number }>(
      `/api/apps/${appId}/forms/${encodeURIComponent(formCode)}/records/${encodeURIComponent(recordId)}?version=${version}`
    ),
  relationOptions: (
    appId: string,
    tableCode: string,
    fieldCode: string,
    keyword = '',
    limit = 20
  ) =>
    client.get<RelationOption[]>(
      `/api/apps/${appId}/tables/${encodeURIComponent(tableCode)}/records/relation-options` +
        `?field=${encodeURIComponent(fieldCode)}&keyword=${encodeURIComponent(keyword)}&limit=${limit}`
    ),
  views: (appId: string) => client.get<ViewSummary[]>(`/api/apps/${appId}/views`),
  view: (appId: string, viewCode: string) =>
    client.get<ViewDetail>(`/api/apps/${appId}/views/${encodeURIComponent(viewCode)}`),
  viewRecords: (appId: string, viewCode: string, current = 1, size = 0) =>
    client.get<ViewPage>(
      `/api/apps/${appId}/views/${encodeURIComponent(viewCode)}/records?current=${current}&size=${size}`
    ),
  viewAggregate: (appId: string, viewCode: string, current = 1, size = 0) =>
    client.get<ViewAggregatePage>(
      `/api/apps/${appId}/views/${encodeURIComponent(viewCode)}/aggregate?current=${current}&size=${size}`
    ),
  viewSummary: (appId: string, viewCode: string) =>
    client.get<Record<string, unknown>>(
      `/api/apps/${appId}/views/${encodeURIComponent(viewCode)}/summary`
    ),
  menus: (appId: string) => client.get<MenuSummary[]>(`/api/apps/${appId}/menus`)
};
