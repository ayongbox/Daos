import { useEffect, useRef, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Layout,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import { FIELD_TYPE_LABELS } from '@daos/metadata';
import type {
  FieldConfig,
  FieldLogicalType,
  FormulaConfig,
  IndexDef,
  RelationConfig,
  RelationDef,
  RelationType,
  RollupConfig,
  SchemaApp,
  SchemaField,
  SchemaTable
} from '@daos/metadata';
import {
  api,
  type AppSummary,
  type FieldTypeTemplate,
  type MigrationPlan,
  type MigrationRecord
} from './api';
import { AppIcon, AppIconPicker } from './appIcons';
import { AppRuntime } from './AppRuntime';
import { FormDesigner } from './FormDesigner';
import { FormRuntime } from './FormRuntime';
import { ViewDesigner } from './ViewDesigner';

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,31}$/;
const { Header, Sider, Content } = Layout;

/** 应用内能力导航。未开发的能力只做占位，不提供点击行为。 */
const APP_NAV_ITEMS = [
  { key: 'model', label: '数据建模', ready: true },
  { key: 'form', label: '表单设计', ready: true },
  { key: 'run', label: '表单运行', ready: true },
  { key: 'view', label: '视图与报表', ready: true },
  { key: 'runtime', label: '应用运行', ready: true },
  { key: 'flow', label: '流程设计', ready: false },
  { key: 'member', label: '权限与成员', ready: false },
  { key: 'setting', label: '应用设置', ready: false }
] as const;

type Capability = 'model' | 'form' | 'run' | 'view' | 'runtime';

function newId(): string {
  const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
  let time = Date.now();
  let out = '';
  for (let i = 0; i < 10; i += 1) {
    out = alphabet.charAt(time % 32) + out;
    time = Math.floor(time / 32);
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  for (const byte of bytes) {
    out += alphabet.charAt(byte % 32);
  }
  return out;
}

function now(): string {
  return new Date().toISOString();
}

function newTable(appId: string, code: string, displayName: string, sortOrder: number): SchemaTable {
  const timestamp = now();
  return {
    id: newId(),
    appId,
    ownership: 'APPLICATION',
    shareable: false,
    storageMode: 'PHYSICAL',
    promotedColumns: [],
    externalRef: null,
    code,
    displayName,
    description: null,
    icon: null,
    color: null,
    groupCode: null,
    sortOrder,
    kind: 'MAIN',
    status: 'DRAFT',
    system: false,
    nameFieldId: null,
    subtitleFieldIds: [],
    defaultSortFieldId: null,
    defaultSortDirection: 'ASC',
    options: {
      softDelete: true,
      auditEnabled: true,
      tenantScoped: true,
      allowImport: true,
      allowExport: true,
      allowBulkEdit: true,
      allowQuickCreate: true,
      allowAttachment: true,
      recordTitleExpression: null,
      defaultPageSize: 20,
      duplicateCheckFieldIds: [],
      ownerFieldId: null,
      deptFieldId: null,
      enableComments: false,
      enableAttachmentsPanel: true,
      enableChangeHistory: true,
      colorRuleEnabled: false
    },
    fields: [],
    indexes: [],
    relations: [],
    ext: {},
    createdBy: 'console',
    createdAt: timestamp,
    updatedBy: 'console',
    updatedAt: timestamp,
    deleted: false,
    schemaVersion: 1
  };
}

function newField(
  appId: string,
  table: SchemaTable,
  code: string,
  displayName: string,
  logicalType: FieldLogicalType,
  config: FieldConfig
): SchemaField {
  const timestamp = now();
  return {
    id: newId(),
    appId,
    tableId: table.id,
    code,
    displayName,
    description: null,
    logicalType,
    config,
    constraints: {
      required: false,
      unique: false,
      indexed: false,
      searchable: true,
      sortable: true,
      filterable: true,
      immutable: false,
      caseSensitive: false,
      trimWhitespace: true,
      defaultValue: null,
      defaultValueExpression: null,
      deprecated: false,
      deprecatedAt: null
    },
    ui: {
      placeholder: null,
      helpText: null,
      tooltip: null,
      prefix: null,
      suffix: null,
      width: null,
      align: 'left',
      hiddenInList: false,
      hiddenInDetail: false,
      readonlyInForm: false
    },
    status: 'DRAFT',
    system: false,
    nameField: table.fields.length === 0,
    sortOrder: table.fields.length,
    groupCode: null,
    ext: {},
    createdBy: 'console',
    createdAt: timestamp,
    updatedBy: 'console',
    updatedAt: timestamp,
    deleted: false,
    schemaVersion: 1
  };
}

interface FieldFormValues {
  code: string;
  displayName: string;
  logicalType: FieldLogicalType;
  required: boolean;
  unique: boolean;
  placeholder?: string;
  helpText?: string;
  expression?: string;
  resultType?: FormulaConfig['resultType'];
  relationFieldId?: string;
  sourceFieldId?: string;
  aggregate?: RollupConfig['aggregate'];
  precision?: number;
  emptyAsZero?: boolean;
}

interface IndexFormValues {
  name: string;
  columns: string[];
  unique: boolean;
}

interface RelationFormValues {
  code: string;
  displayName: string;
  type: RelationType;
  targetTableId: string;
  sourceFieldId?: string;
  targetFieldId?: string;
}


export function ModelerPage({ onLogout }: { onLogout: () => void }) {
  const { message, modal } = AntApp.useApp();
  const [apps, setApps] = useState<AppSummary[]>([]);
  const [appId, setAppId] = useState<string | null>(null);
  const [capability, setCapability] = useState<Capability>('model');
  const [draft, setDraft] = useState<SchemaApp | null>(null);
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);
  const [fieldTypes, setFieldTypes] = useState<FieldTypeTemplate[]>([]);
  const [dirty, setDirty] = useState(false);
  const [loading, setLoading] = useState(false);

  const [appModal, setAppModal] = useState(false);
  const [tableModal, setTableModal] = useState<{ tableId?: string } | null>(null);
  const [fieldModal, setFieldModal] = useState<{ tableId: string; fieldId?: string } | null>(null);
  const [indexModal, setIndexModal] = useState(false);
  const [relationModal, setRelationModal] = useState(false);
  const [preview, setPreview] = useState<MigrationPlan | null>(null);
  const [history, setHistory] = useState<MigrationRecord[] | null>(null);

  const [appForm] = Form.useForm<{ displayName: string; description?: string; icon?: string }>();
  const [tableForm] = Form.useForm<{ code: string; displayName: string }>();
  const [fieldForm] = Form.useForm<FieldFormValues>();
  const selectedLogicalType = Form.useWatch('logicalType', fieldForm);
  const selectedRelationFieldId = Form.useWatch('relationFieldId', fieldForm);
  const selectedAggregate = Form.useWatch('aggregate', fieldForm);
  const [indexForm] = Form.useForm<IndexFormValues>();
  const [relationForm] = Form.useForm<RelationFormValues>();

  const selectedTable = draft?.tables.find((table) => table.id === selectedTableId) ?? null;
  const rollupRelations = (draft?.tables ?? []).flatMap((table) =>
    table.id === selectedTable?.id
      ? []
      : table.fields
          .filter(
            (field) =>
              !field.deleted &&
              field.logicalType === 'relation' &&
              (field.config as RelationConfig).targetTableId === selectedTable?.id
          )
          .map((field) => ({ table, field }))
  );
  const selectedRollupRelation = rollupRelations.find(
    ({ field }) => field.id === selectedRelationFieldId
  );
  const rollupSourceFields = selectedRollupRelation?.table.fields.filter((field) => !field.deleted) ?? [];
  const editingField =
    fieldModal?.fieldId && selectedTable
      ? selectedTable.fields.find((field) => field.id === fieldModal.fieldId) ?? null
      : null;

  /** 重新拉取应用列表，用于发布或返回列表时刷新发布状态。 */
  function refreshApps() {
    api.listApps().then(setApps).catch(showError);
  }

  useEffect(() => {
    refreshApps();
    api.fieldTypes().then(setFieldTypes).catch(showError);
  }, []);

  // 已删除的应用不再拉取草稿，避免删除瞬间的竞态把 403 提示弹到用户面前。
  const deletedApps = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (!appId) {
      setDraft(null);
      return;
    }
    setLoading(true);
    api
      .draft(appId)
      .then((value) => {
        setDraft({ ...value, views: value.views ?? [], menus: value.menus ?? [] });
        setDirty(false);
        setSelectedTableId(value.tables[0]?.id ?? null);
      })
      .catch((cause) => {
        if (cause instanceof ApiError && cause.status === 403 && deletedApps.current.has(appId)) {
          return;
        }
        showError(cause);
      })
      .finally(() => setLoading(false));
  }, [appId]);

  function showError(cause: unknown) {
    // 401 由全局会话失效事件统一处理并回到登录页，不再叠加提示
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  /** 切换应用或返回应用列表；未保存时先确认，避免草稿丢失。 */
  function switchApp(nextAppId: string | null) {
    if (nextAppId === appId) return;
    if (dirty && !window.confirm('当前草稿未保存，确认离开？')) return;
    if (nextAppId === null) refreshApps();
    setAppId(nextAppId);
  }

  function openNewField() {
    if (!selectedTable) return;
    setFieldModal({ tableId: selectedTable.id });
    fieldForm.resetFields();
    fieldForm.setFieldsValue({ required: false, unique: false });
  }

  function openFieldEditor(field: SchemaField) {
    if (!selectedTable) return;
    const formula = field.logicalType === 'formula' ? (field.config as FormulaConfig) : null;
    const rollup = field.logicalType === 'rollup' ? (field.config as RollupConfig) : null;
    setFieldModal({ tableId: selectedTable.id, fieldId: field.id });
    fieldForm.setFieldsValue({
      code: field.code,
      displayName: field.displayName,
      logicalType: field.logicalType,
      required: field.constraints.required,
      unique: field.constraints.unique,
      placeholder: field.ui.placeholder ?? undefined,
      helpText: field.ui.helpText ?? undefined,
      expression: formula?.expression ?? undefined,
      resultType: formula?.resultType ?? undefined,
      relationFieldId: rollup?.relationFieldId ?? undefined,
      sourceFieldId: rollup?.sourceFieldId ?? undefined,
      aggregate: rollup ? (rollup.aggregate ?? 'SUM') : undefined,
      precision: formula?.precision ?? (rollup ? (rollup.precision ?? 2) : undefined),
      emptyAsZero: rollup ? (rollup.emptyAsZero ?? true) : undefined
    });
  }

  function closeFieldDrawer() {
    setFieldModal(null);
    fieldForm.resetFields();
  }

  function patchTable(tableId: string, update: (table: SchemaTable) => SchemaTable) {
    setDraft((previous) =>
      previous
        ? {
            ...previous,
            tables: previous.tables.map((table) => (table.id === tableId ? update(table) : table))
          }
        : previous
    );
    setDirty(true);
  }

  async function reloadDraft(targetAppId: string) {
    const value = await api.draft(targetAppId);
    setDraft({ ...value, views: value.views ?? [], menus: value.menus ?? [] });
    setDirty(false);
    if (!value.tables.some((table) => table.id === selectedTableId)) {
      setSelectedTableId(value.tables[0]?.id ?? null);
    }
  }

  async function saveDraft() {
    if (!draft) return;
    const timestamp = now();
    const payload: SchemaApp = {
      ...draft,
      updatedAt: timestamp,
      updatedBy: 'console',
      tables: draft.tables.map((table) => ({ ...table, updatedAt: timestamp, updatedBy: 'console' }))
    };
    setLoading(true);
    try {
      await api.saveDraft(draft.id, payload);
      setDraft(payload);
      setDirty(false);
      message.success('草稿已保存');
    } catch (cause) {
      showError(cause);
    } finally {
      setLoading(false);
    }
  }

  async function createApp(values: { displayName: string; description?: string; icon?: string }) {
    try {
      const created = await api.createApp(values);
      setApps((previous) => [...previous, created]);
      setAppId(created.id);
      setAppModal(false);
      appForm.resetFields();
      message.success('应用已创建');
    } catch (cause) {
      showError(cause);
    }
  }

  async function deleteApp(id: string) {
    try {
      await api.deleteApp(id);
      deletedApps.current.add(id);
      setApps((previous) => previous.filter((item) => item.id !== id));
      setAppId(null);
      setDraft(null);
      message.success('应用已删除');
    } catch (cause) {
      showError(cause);
    }
  }

  function openNewTable() {
    tableForm.resetFields();
    setTableModal({});
  }

  function openTableEditor(table: SchemaTable) {
    tableForm.setFieldsValue({ code: table.code, displayName: table.displayName });
    setTableModal({ tableId: table.id });
  }

  function submitTable(values: { code: string; displayName: string }) {
    if (!draft) return;
    const editingId = tableModal?.tableId;
    if (editingId) {
      patchTable(editingId, (table) => ({
        ...table,
        displayName: values.displayName,
        updatedAt: now(),
        updatedBy: 'console'
      }));
    } else {
      const table = newTable(draft.id, values.code, values.displayName, draft.tables.length);
      setDraft({ ...draft, tables: [...draft.tables, table] });
      setSelectedTableId(table.id);
      setDirty(true);
    }
    setTableModal(null);
    tableForm.resetFields();
  }

  function deleteTable(tableId: string) {
    if (!draft) return;
    const table = draft.tables.find((item) => item.id === tableId);
    if (!table) return;
    if (draft.forms.some((form) => form.tableId === tableId)) {
      message.warning('该表已被表单使用，请先在表单设计中删除相关表单');
      return;
    }
    modal.confirm({
      title: `删除数据表「${table.displayName}」？`,
      content: '字段、索引与关联配置将一并移除，保存草稿后生效',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        const remaining = draft.tables.filter((item) => item.id !== tableId);
        const removedViewIds = new Set(
          (draft.views ?? []).filter((view) => view.tableId === tableId).map((view) => view.id)
        );
        setDraft({
          ...draft,
          tables: remaining.map((item) => ({
            ...item,
            relations: item.relations.filter((relation) => relation.targetTableId !== tableId)
          })),
          views: (draft.views ?? []).filter((view) => !removedViewIds.has(view.id)),
          menus: (draft.menus ?? []).filter(
            (menu) =>
              !(
                (menu.menuType === 'TABLE' && menu.targetId === tableId) ||
                (menu.menuType === 'VIEW' && removedViewIds.has(menu.targetId))
              )
          )
        });
        setSelectedTableId((current) => (current === tableId ? (remaining[0]?.id ?? null) : current));
        setDirty(true);
      }
    });
  }

  function submitField(values: FieldFormValues) {
    if (!draft || !selectedTable || !fieldModal) return;
    const template = fieldTypes.find((item) => item.logicalType === values.logicalType);
    if (!template) {
      message.error('字段类型未加载');
      return;
    }
    const config: FieldConfig =
      values.logicalType === 'formula'
        ? {
            expression: values.expression?.trim() ?? '',
            resultType: values.resultType ?? 'decimal',
            precision: values.precision ?? 2,
            cacheMode: 'REALTIME'
          }
        : values.logicalType === 'rollup'
          ? {
              relationFieldId: values.relationFieldId ?? '',
              sourceFieldId: values.sourceFieldId ?? null,
              aggregate: values.aggregate ?? 'SUM',
              filterExpression: null,
              precision: values.precision ?? 2,
              emptyAsZero: values.emptyAsZero ?? true
            }
          : template.defaultConfig;
    if (fieldModal.fieldId) {
      patchTable(selectedTable.id, (table) => ({
        ...table,
        fields: table.fields.map((field) =>
          field.id === fieldModal.fieldId
            ? {
                ...field,
                code: values.code,
                displayName: values.displayName,
                logicalType: values.logicalType,
                config,
                constraints: {
                  ...field.constraints,
                  required: values.required,
                  unique: values.unique
                },
                ui: {
                  ...field.ui,
                  placeholder: values.placeholder?.trim() || null,
                  helpText: values.helpText?.trim() || null
                },
                updatedAt: now(),
                updatedBy: 'console'
              }
            : field
        )
      }));
    } else {
      const field = newField(
        draft.id,
        selectedTable,
        values.code,
        values.displayName,
        values.logicalType,
        config
      );
      field.constraints.required = values.required;
      field.constraints.unique = values.unique;
      field.ui.placeholder = values.placeholder?.trim() || null;
      field.ui.helpText = values.helpText?.trim() || null;
      patchTable(selectedTable.id, (table) => ({
        ...table,
        nameFieldId: table.nameFieldId ?? field.id,
        fields: [...table.fields, field]
      }));
    }
    closeFieldDrawer();
  }

  function deleteField(table: SchemaTable, field: SchemaField) {
    modal.confirm({
      title: `删除字段 ${field.displayName}？`,
      content: '发布后会生成 DROP COLUMN；已删除列的数据无法通过回滚恢复。',
      okType: 'danger',
      onOk: () =>
        setDraft((previous) => {
          if (!previous) return previous;
          return {
            ...previous,
            tables: previous.tables.map((current) =>
              current.id === table.id
                ? {
                    ...current,
                    fields: current.fields.filter((item) => item.id !== field.id),
                    nameFieldId: current.nameFieldId === field.id ? null : current.nameFieldId
                  }
                : current
            ),
            views: (previous.views ?? []).map((view) =>
              view.tableId === table.id
                ? {
                    ...view,
                    columns: view.columns.filter((column) => column.fieldId !== field.id),
                    filters: view.filters.filter((filter) => filter.fieldId !== field.id),
                    sorts: view.sorts.filter((sort) => sort.fieldId !== field.id)
                  }
                : view
            )
          };
        })
    });
  }

  function submitIndex(values: IndexFormValues) {
    if (!selectedTable) return;
    const index: IndexDef = {
      id: newId(),
      name: values.name,
      columns: values.columns,
      unique: values.unique,
      type: 'BTREE',
      where: null,
      description: null
    };
    patchTable(selectedTable.id, (table) => ({ ...table, indexes: [...table.indexes, index] }));
    setIndexModal(false);
    indexForm.resetFields();
  }

  function submitRelation(values: RelationFormValues) {
    if (!selectedTable) return;
    const relation: RelationDef = {
      id: newId(),
      code: values.code,
      displayName: values.displayName,
      type: values.type,
      sourceTableId: selectedTable.id,
      targetTableId: values.targetTableId,
      sourceFieldId: values.sourceFieldId ?? null,
      targetFieldId: values.targetFieldId ?? null,
      joinTableCode: null,
      onDelete: 'RESTRICT',
      onUpdate: 'RESTRICT',
      bidirectional: false,
      reverseFieldId: null,
      filterExpression: null,
      enabled: true
    };
    patchTable(selectedTable.id, (table) => ({
      ...table,
      relations: [...table.relations, relation]
    }));
    setRelationModal(false);
    relationForm.resetFields();
  }

  async function openPreview() {
    if (!draft) return;
    try {
      setPreview(await api.preview(draft.id));
    } catch (cause) {
      showError(cause);
    }
  }

  async function publish() {
    if (!draft) return;
    modal.confirm({
      title: '发布并执行结构演进？',
      content: '将按预览差异执行 DDL 并激活新版本；失败会自动回滚 SQL。',
      onOk: async () => {
        try {
          await api.apply(draft.id);
          await reloadDraft(draft.id);
          refreshApps();
          message.success('发布成功');
        } catch (cause) {
          showError(cause);
        }
      }
    });
  }

  async function openHistory() {
    if (!draft) return;
    try {
      setHistory(await api.history(draft.id));
    } catch (cause) {
      showError(cause);
    }
  }

  function rollback(record: MigrationRecord) {
    if (!draft) return;
    modal.confirm({
      title: `回滚到 v${record.fromVersion}？`,
      content: '只能回滚最近一次成功迁移；被删除列的数据不会恢复。',
      okType: 'danger',
      onOk: async () => {
        try {
          await api.rollback(draft.id, record.id);
          await reloadDraft(draft.id);
          setHistory(await api.history(draft.id));
          message.success('已回滚');
        } catch (cause) {
          showError(cause);
        }
      }
    });
  }

  const fieldColumns: ColumnsType<SchemaField> = [
    { title: '编码', dataIndex: 'code', width: 160 },
    { title: '名称', dataIndex: 'displayName', width: 160 },
    {
      title: '类型',
      dataIndex: 'logicalType',
      width: 120,
      render: (value: FieldLogicalType) => FIELD_TYPE_LABELS[value] ?? value
    },
    {
      title: '必填',
      dataIndex: ['constraints', 'required'],
      width: 70,
      render: (value: boolean) => (value ? '是' : '否')
    },
    {
      title: '操作',
      width: 150,
      render: (_, field) => (
        <Space className="daos-row-actions">
          <Button size="small" type="link" onClick={() => openFieldEditor(field)}>
            编辑
          </Button>
          <Button size="small" type="link" danger onClick={() => selectedTable && deleteField(selectedTable, field)}>
            删除
          </Button>
        </Space>
      )
    }
  ];

  const indexColumns: ColumnsType<IndexDef> = [
    { title: '名称', dataIndex: 'name' },
    { title: '字段', dataIndex: 'columns', render: (value: string[]) => value.join(', ') },
    { title: '唯一', dataIndex: 'unique', render: (value: boolean) => (value ? '是' : '否') },
    {
      title: '操作',
      width: 100,
      render: (_, index) =>
        selectedTable ? (
          <Button
            className="daos-row-actions"
            size="small"
            type="link"
            danger
            onClick={() =>
              patchTable(selectedTable.id, (table) => ({
                ...table,
                indexes: table.indexes.filter((item) => item.id !== index.id)
              }))
            }
          >
            删除
          </Button>
        ) : null
    }
  ];

  const relationColumns: ColumnsType<RelationDef> = [
    { title: '编码', dataIndex: 'code' },
    { title: '名称', dataIndex: 'displayName' },
    { title: '类型', dataIndex: 'type' },
    {
      title: '目标表',
      dataIndex: 'targetTableId',
      render: (value: string) => draft?.tables.find((table) => table.id === value)?.displayName ?? value
    },
    {
      title: '操作',
      width: 100,
      render: (_, relation) =>
        selectedTable ? (
          <Button
            className="daos-row-actions"
            size="small"
            type="link"
            danger
            onClick={() =>
              patchTable(selectedTable.id, (table) => ({
                ...table,
                relations: table.relations.filter((item) => item.id !== relation.id)
              }))
            }
          >
            删除
          </Button>
        ) : null
    }
  ];

  const appOptions = [
    { value: 'gallery', label: '应用列表' },
    ...apps.map((app) => ({ value: app.id, label: app.displayName }))
  ];

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '0 16px',
          background: tokens.colorBgContainer,
          borderBottom: `1px solid ${tokens.colorBorder}`
        }}
      >
        <div
          style={{
            width: 28,
            height: 28,
            borderRadius: tokens.radiusSm,
            background: tokens.colorPrimary,
            color: '#FFFFFF',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 15,
            fontWeight: 600
          }}
        >
          道
        </div>
        <Typography.Text strong style={{ fontSize: tokens.fontSizeLg }}>
          道生
        </Typography.Text>
        <Divider type="vertical" style={{ margin: 0 }} />
        {apps.length > 0 ? (
          <Select
            variant="borderless"
            value={appId ?? 'gallery'}
            options={appOptions}
            style={{ width: 180 }}
            onChange={(value) => switchApp(value === 'gallery' ? null : value)}
          />
        ) : (
          <Typography.Text type="secondary">控制台</Typography.Text>
        )}
        <div style={{ flex: 1 }} />
        {dirty ? (
          <Tag color="orange" style={{ marginInlineEnd: 0 }}>
            未保存
          </Tag>
        ) : null}
        <Button type="text" onClick={onLogout}>
          退出
        </Button>
      </Header>

      <Layout>
        <Sider
          theme="light"
          width={tokens.siderWidth}
          style={{ borderRight: `1px solid ${tokens.colorBorder}`, overflow: 'auto' }}
        >
          <div style={{ padding: '16px 20px 8px', color: tokens.colorTextTertiary, fontSize: tokens.fontSizeSm }}>
            应用
          </div>
          <div
            onClick={() => switchApp(null)}
            style={{
              display: 'flex',
              alignItems: 'center',
              height: 36,
              margin: '2px 8px',
              padding: '0 10px',
              borderRadius: tokens.radiusSm,
              cursor: appId ? 'pointer' : 'default',
              background: appId ? undefined : tokens.colorPrimaryBg,
              color: appId ? tokens.colorTextPrimary : tokens.colorPrimary,
              fontSize: tokens.fontSizeMd
            }}
          >
            应用列表
          </div>
          <Divider style={{ margin: '12px 0' }} />
          <div style={{ padding: '0 20px 8px', color: tokens.colorTextTertiary, fontSize: tokens.fontSizeSm }}>
            应用能力
          </div>
          {APP_NAV_ITEMS.map((item) => {
            const enabled = appId !== null && item.ready;
            const active = appId !== null && item.key === capability;
            return (
              <div
                key={item.key}
                onClick={() => {
                  if (enabled) setCapability(item.key as Capability);
                }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  height: 36,
                  margin: '2px 8px',
                  padding: '0 10px',
                  borderRadius: tokens.radiusSm,
                  cursor: enabled ? 'pointer' : 'default',
                  background: active ? tokens.colorPrimaryBg : undefined,
                  color: active
                    ? tokens.colorPrimary
                    : enabled
                      ? tokens.colorTextPrimary
                      : tokens.colorTextDisabled,
                  fontSize: tokens.fontSizeMd
                }}
              >
                <span>{item.label}</span>
                {item.ready ? null : (
                  <span style={{ fontSize: 11, color: tokens.colorTextTertiary }}>规划中</span>
                )}
              </div>
            );
          })}
        </Sider>

        <Content style={{ padding: 16, overflow: 'auto', minWidth: 0 }}>
          {!appId ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
                <div>
                  <Typography.Title level={4} style={{ margin: 0, fontSize: tokens.fontSizeHeading }}>
                    我的应用
                  </Typography.Title>
                  <Typography.Text type="secondary">
                    共 {apps.length} 个应用，选择应用进入数据建模
                  </Typography.Text>
                </div>
                <Button type="primary" onClick={() => setAppModal(true)}>
                  新建应用
                </Button>
              </div>
              {apps.length === 0 ? (
                <Card>
                  <Empty description="还没有应用，先创建一个" />
                </Card>
              ) : (
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))',
                    gap: 16
                  }}
                >
                  {apps.map((app) => (
                    <Card
                      key={app.id}
                      hoverable
                      style={{ height: '100%' }}
                      onClick={() => switchApp(app.id)}
                    >
                      <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                        <AppIcon icon={app.icon} name={app.displayName} />
                        <div style={{ flex: 1, minWidth: 0 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <Typography.Text
                              strong
                              ellipsis
                              style={{ flex: 1, minWidth: 0, fontSize: 15 }}
                            >
                              {app.displayName}
                            </Typography.Text>
                            <Popconfirm
                              title="删除这个应用？"
                              description="应用与表配置将不可恢复"
                              okText="删除"
                              cancelText="取消"
                              okButtonProps={{ danger: true }}
                              onConfirm={() => deleteApp(app.id)}
                            >
                              <Button
                                type="text"
                                size="small"
                                danger
                                style={{ flexShrink: 0 }}
                                onClick={(event) => event.stopPropagation()}
                              >
                                删除
                              </Button>
                            </Popconfirm>
                          </div>
                          <Space size={4} style={{ marginTop: 6 }}>
                            {app.activeReleaseVersion > 0 ? (
                              <Tag color="green" style={{ marginInlineEnd: 0 }}>
                                已发布 v{app.activeReleaseVersion}
                              </Tag>
                            ) : (
                              <Tag style={{ marginInlineEnd: 0 }}>未发布</Tag>
                            )}
                            {app.changedSinceRelease ? (
                              <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                                未发布改动
                              </Tag>
                            ) : null}
                          </Space>
                          <div
                            style={{
                              color: tokens.colorTextSecondary,
                              fontSize: tokens.fontSizeSm,
                              marginTop: 6,
                              height: 20,
                              lineHeight: '20px',
                              whiteSpace: 'nowrap',
                              overflow: 'hidden',
                              textOverflow: 'ellipsis'
                            }}
                          >
                            {app.description?.trim() || '暂无描述'}
                          </div>
                        </div>
                      </div>
                      <Divider style={{ margin: '12px 0' }} />
                      <div style={{ color: tokens.colorTextTertiary, fontSize: tokens.fontSizeSm }}>
                        {app.tableCount} 张表
                      </div>
                    </Card>
                  ))}
                </div>
              )}
            </Space>
          ) : !draft ? (
            <Card loading />
          ) : (
            <Space direction="vertical" style={{ width: '100%' }} size={16}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  flexWrap: 'wrap',
                  gap: 8,
                  padding: '10px 16px',
                  background: tokens.colorBgContainer,
                  borderRadius: tokens.radiusMd
                }}
              >
                <Space size={8}>
                  <AppIcon icon={draft.icon} name={draft.displayName} size={28} />
                  <Typography.Text strong style={{ fontSize: 15 }}>
                    {draft.displayName}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
                    {draft.tables.length} 张表
                  </Typography.Text>
                </Space>
                {capability === 'run' || capability === 'runtime' ? null : (
                  <Space size={8} wrap>
                    <Button type="text" onClick={openPreview}>
                      预览迁移
                    </Button>
                    <Button type="text" onClick={openHistory}>
                      迁移历史
                    </Button>
                    <Button onClick={saveDraft} disabled={!dirty} loading={loading}>
                      保存草稿
                    </Button>
                    <Tooltip title={dirty ? '请先保存草稿' : undefined}>
                      <Button type="primary" onClick={publish} disabled={dirty}>
                        发布
                      </Button>
                    </Tooltip>
                  </Space>
                )}
              </div>

              {capability === 'form' ? (
                <FormDesigner
                  draft={draft}
                  onChange={(forms) => {
                    setDraft((previous) => (previous ? { ...previous, forms } : previous));
                    setDirty(true);
                  }}
                  onTablesChange={(tables) => {
                    setDraft((previous) => (previous ? { ...previous, tables } : previous));
                    setDirty(true);
                  }}
                />
              ) : capability === 'run' ? (
                <FormRuntime appId={appId} />
              ) : capability === 'view' ? (
                <ViewDesigner
                  app={draft}
                  onChange={(next) => {
                    setDraft(next);
                    setDirty(true);
                  }}
                />
              ) : capability === 'runtime' ? (
                <AppRuntime appId={appId} />
              ) : (
                <div
                  style={{
                    display: 'flex',
                    gap: 16,
                    alignItems: 'flex-start',
                    flexWrap: 'wrap',
                    minWidth: 0
                  }}
                >
                  <Card
                    size="small"
                    style={{ width: 220, flex: '0 0 220px' }}
                    title="数据表"
                    extra={
                      <Button size="small" type="text" onClick={openNewTable}>
                        新建
                      </Button>
                    }
                  >
                    <List
                      size="small"
                      dataSource={draft.tables}
                      locale={{ emptyText: '暂无数据表' }}
                      renderItem={(table) => (
                        <List.Item
                          style={{
                            cursor: 'pointer',
                            borderRadius: tokens.radiusSm,
                            paddingInline: 12,
                            background: table.id === selectedTableId ? tokens.colorPrimaryBg : undefined
                          }}
                          onClick={() => setSelectedTableId(table.id)}
                        >
                          <List.Item.Meta
                            title={table.displayName}
                            description={`${table.code} · ${table.fields.length} 字段`}
                          />
                          <Dropdown
                            trigger={['click']}
                            menu={{
                              items: [
                                { key: 'rename', label: '重命名' },
                                { key: 'delete', label: '删除', danger: true }
                              ],
                              onClick: ({ key, domEvent }) => {
                                domEvent.stopPropagation();
                                if (key === 'rename') openTableEditor(table);
                                if (key === 'delete') deleteTable(table.id);
                              }
                            }}
                          >
                            <Button type="text" size="small" onClick={(event) => event.stopPropagation()}>
                              ···
                            </Button>
                          </Dropdown>
                        </List.Item>
                      )}
                    />
                  </Card>
                  {!selectedTable ? (
                    <Card style={{ flex: '1 1 420px', minWidth: 0 }}>
                      <Empty description="选择或新建一张数据表" />
                    </Card>
                  ) : (
                    <Card
                      size="small"
                      style={{ flex: '1 1 420px', minWidth: 0 }}
                      title={
                        <Space>
                          <span>{selectedTable.displayName}</span>
                          <Tag>{selectedTable.code}</Tag>
                        </Space>
                      }
                    >
                      <Tabs
                        items={[
                          {
                            key: 'fields',
                            label: `字段 ${selectedTable.fields.length}`,
                            children: (
                              <Space direction="vertical" style={{ width: '100%' }}>
                                <Button size="small" onClick={openNewField}>
                                  添加字段
                                </Button>
                                <Table
                                  rowKey="id"
                                  size="small"
                                  pagination={false}
                                  scroll={{ x: 780 }}
                                  columns={fieldColumns}
                                  dataSource={selectedTable.fields}
                                  onRow={(field) => ({
                                    style: { cursor: 'pointer' },
                                    onClick: (event) => {
                                      if ((event.target as HTMLElement).closest('button')) return;
                                      openFieldEditor(field);
                                    }
                                  })}
                                />
                              </Space>
                            )
                          },
                          {
                            key: 'indexes',
                            label: `索引 ${selectedTable.indexes.length}`,
                            children: (
                              <Space direction="vertical" style={{ width: '100%' }}>
                                <Button
                                  size="small"
                                  disabled={selectedTable.fields.length === 0}
                                  onClick={() => {
                                    setIndexModal(true);
                                    indexForm.resetFields();
                                    indexForm.setFieldsValue({ unique: false, columns: [] });
                                  }}
                                >
                                  添加索引
                                </Button>
                                <Table
                                  rowKey="id"
                                  size="small"
                                  pagination={false}
                                  scroll={{ x: 640 }}
                                  columns={indexColumns}
                                  dataSource={selectedTable.indexes}
                                />
                              </Space>
                            )
                          },
                          {
                            key: 'relations',
                            label: `关联 ${selectedTable.relations.length}`,
                            children: (
                              <Space direction="vertical" style={{ width: '100%' }}>
                                <Button
                                  size="small"
                                  disabled={draft.tables.length < 2}
                                  onClick={() => {
                                    setRelationModal(true);
                                    relationForm.resetFields();
                                    relationForm.setFieldsValue({ type: 'MANY_TO_ONE' });
                                  }}
                                >
                                  添加关联
                                </Button>
                                <Table
                                  rowKey="id"
                                  size="small"
                                  pagination={false}
                                  scroll={{ x: 720 }}
                                  columns={relationColumns}
                                  dataSource={selectedTable.relations}
                                />
                              </Space>
                            )
                          }
                        ]}
                      />
                    </Card>
                  )}
                </div>
              )}
            </Space>
          )}
        </Content>
      </Layout>

      <Modal
        title="新建应用"
        open={appModal}
        onCancel={() => setAppModal(false)}
        onOk={() => appForm.submit()}
        okText="创建"
        cancelText="取消"
        width={480}
        destroyOnClose
      >
        <Form
          form={appForm}
          layout="vertical"
          onFinish={createApp}
          initialValues={{ icon: 'AppstoreOutlined' }}
        >
          <Form.Item name="icon" label="应用图标">
            <AppIconPicker />
          </Form.Item>
          <Form.Item name="displayName" label="应用名称" rules={[{ required: true, message: '请输入应用名称' }]}>
            <Input placeholder="客户管理" maxLength={40} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} maxLength={200} showCount placeholder="一句话说明这个应用用来做什么" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={tableModal?.tableId ? '重命名数据表' : '新建数据表'}
        open={tableModal !== null}
        onCancel={() => setTableModal(null)}
        onOk={() => tableForm.submit()}
        destroyOnClose
      >
        <Form form={tableForm} layout="vertical" onFinish={submitTable}>
          <Form.Item
            name="code"
            label="表编码"
            extra={tableModal?.tableId ? '表编码是物理标识，创建后不可修改' : undefined}
            rules={[
              { required: true, message: '请输入表编码' },
              { pattern: CODE_PATTERN, message: '小写字母开头，可含数字与下划线，最长 32 位' }
            ]}
          >
            <Input placeholder="crm_customer" disabled={Boolean(tableModal?.tableId)} />
          </Form.Item>
          <Form.Item name="displayName" label="表名称" rules={[{ required: true, message: '请输入表名称' }]}>
            <Input placeholder="客户" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={
          <Space>
            <span>{editingField ? '字段配置' : '添加字段'}</span>
            {editingField ? <Tag>{editingField.code}</Tag> : null}
          </Space>
        }
        width="min(480px, calc(100vw - 48px))"
        open={fieldModal !== null}
        onClose={closeFieldDrawer}
        destroyOnClose
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={closeFieldDrawer}>取消</Button>
            <Button type="primary" onClick={() => fieldForm.submit()}>
              保存
            </Button>
          </div>
        }
      >
        {editingField ? (
          <div style={{ marginBottom: 16, fontSize: 12, color: tokens.colorTextSecondary }}>
            字段 ID <Typography.Text copyable code>{editingField.id}</Typography.Text>
          </div>
        ) : null}
        <Form form={fieldForm} layout="vertical" onFinish={submitField} requiredMark={false}>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            基础信息
          </Typography.Text>
          <Form.Item
            name="displayName"
            label="字段名称"
            style={{ marginTop: 8 }}
            rules={[{ required: true, message: '请输入字段名称' }]}
          >
            <Input placeholder="客户名称" />
          </Form.Item>
          <Form.Item
            name="code"
            label="字段编码"
            rules={[
              { required: true, message: '请输入字段编码' },
              { pattern: CODE_PATTERN, message: '小写字母开头，可含数字与下划线，最长 32 位' }
            ]}
          >
            <Input placeholder="customer_name" />
          </Form.Item>
          <Form.Item
            name="logicalType"
            label="字段类型"
            extra="切换类型会重置该类型的配置项"
            rules={[{ required: true, message: '请选择类型' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              options={fieldTypes.map((item) => ({
                value: item.logicalType,
                label: FIELD_TYPE_LABELS[item.logicalType] ?? item.logicalType
              }))}
              onChange={(logicalType) => {
                if (logicalType === 'formula') {
                  fieldForm.setFieldsValue({ resultType: 'decimal', precision: 2 });
                } else if (logicalType === 'rollup') {
                  fieldForm.setFieldsValue({
                    relationFieldId: undefined,
                    sourceFieldId: undefined,
                    aggregate: 'SUM',
                    precision: 2,
                    emptyAsZero: true
                  });
                }
              }}
            />
          </Form.Item>

          {selectedLogicalType === 'formula' ? (
            <>
              <Divider style={{ margin: '8px 0 16px' }} />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                公式配置
              </Typography.Text>
              <Form.Item
                name="expression"
                label="公式表达式"
                extra="使用 {字段编码} 引用同表字段，支持 +、-、*、/、括号和 ROUND"
                style={{ marginTop: 8 }}
                rules={[{ required: true, message: '请输入公式表达式' }]}
              >
                <Input.TextArea rows={3} placeholder="{quantity} * {unit_price}" />
              </Form.Item>
              <Form.Item
                name="resultType"
                label="结果类型"
                rules={[{ required: true, message: '请选择结果类型' }]}
              >
                <Select
                  options={[
                    { value: 'number', label: '整数' },
                    { value: 'decimal', label: '小数' },
                    { value: 'text', label: '文本' },
                    { value: 'date', label: '日期' },
                    { value: 'boolean', label: '是 / 否' }
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="precision"
                label="小数位数"
                rules={[{ required: true, message: '请设置小数位数' }]}
              >
                <InputNumber min={0} max={20} precision={0} style={{ width: '100%' }} />
              </Form.Item>
            </>
          ) : null}

          {selectedLogicalType === 'rollup' ? (
            <>
              <Divider style={{ margin: '8px 0 16px' }} />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                引用汇总配置
              </Typography.Text>
              <Form.Item
                name="relationFieldId"
                label="关联字段"
                extra="仅显示其他表中关联到当前表的字段"
                style={{ marginTop: 8 }}
                rules={[{ required: true, message: '请选择关联字段' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="请选择子表关联字段"
                  options={rollupRelations.map(({ table, field }) => ({
                    value: field.id,
                    label: `${table.displayName} / ${field.displayName}`
                  }))}
                  onChange={() => fieldForm.setFieldsValue({ sourceFieldId: undefined })}
                />
              </Form.Item>
              <Form.Item
                name="sourceFieldId"
                label="来源字段"
                extra={selectedAggregate === 'COUNT' ? 'COUNT 时可不选择来源字段' : undefined}
                dependencies={['aggregate']}
                rules={[{ required: selectedAggregate !== 'COUNT', message: '请选择来源字段' }]}
              >
                <Select
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  disabled={!selectedRollupRelation}
                  options={rollupSourceFields.map((field) => ({
                    value: field.id,
                    label: `${field.displayName} (${field.code})`
                  }))}
                />
              </Form.Item>
              <Form.Item
                name="aggregate"
                label="聚合方式"
                rules={[{ required: true, message: '请选择聚合方式' }]}
              >
                <Select
                  options={[
                    { value: 'SUM', label: 'SUM（求和）' },
                    { value: 'AVG', label: 'AVG（平均值）' },
                    { value: 'COUNT', label: 'COUNT（计数）' },
                    { value: 'MAX', label: 'MAX（最大值）' },
                    { value: 'MIN', label: 'MIN（最小值）' }
                  ]}
                />
              </Form.Item>
              <Form.Item
                name="precision"
                label="小数位数"
                rules={[{ required: true, message: '请设置小数位数' }]}
              >
                <InputNumber min={0} max={20} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="emptyAsZero" label="空集显示为 0" valuePropName="checked">
                <Switch size="small" />
              </Form.Item>
            </>
          ) : null}

          <Divider style={{ margin: '8px 0 16px' }} />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            校验规则
          </Typography.Text>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 8 }}>
            <Form.Item name="required" label="必填" valuePropName="checked" style={{ marginBottom: 12 }}>
              <Switch size="small" />
            </Form.Item>
            <Form.Item name="unique" label="唯一" valuePropName="checked" style={{ marginBottom: 12 }}>
              <Switch size="small" />
            </Form.Item>
          </div>

          <Divider style={{ margin: '8px 0 16px' }} />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            展示
          </Typography.Text>
          <Form.Item name="placeholder" label="占位提示" style={{ marginTop: 8 }}>
            <Input placeholder="请输入客户名称" maxLength={64} />
          </Form.Item>
          <Form.Item name="helpText" label="帮助文字">
            <Input.TextArea rows={2} maxLength={200} showCount placeholder="展示在表单字段下方的填写说明" />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title="添加索引"
        open={indexModal}
        onCancel={() => setIndexModal(false)}
        onOk={() => indexForm.submit()}
        destroyOnClose
      >
        <Form form={indexForm} layout="vertical" onFinish={submitIndex}>
          <Form.Item
            name="name"
            label="索引名"
            rules={[{ required: true, message: '请输入索引名' }, { pattern: CODE_PATTERN, message: '小写字母开头，最长 32 位' }]}
          >
            <Input placeholder="idx_customer_name" />
          </Form.Item>
          <Form.Item name="columns" label="字段" rules={[{ required: true, message: '请选择字段' }]}>
            <Select
              mode="multiple"
              options={(selectedTable?.fields ?? []).map((field) => ({
                value: field.code,
                label: `${field.displayName} (${field.code})`
              }))}
            />
          </Form.Item>
          <Form.Item name="unique" label="唯一索引" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="添加关联"
        open={relationModal}
        onCancel={() => setRelationModal(false)}
        onOk={() => relationForm.submit()}
        destroyOnClose
      >
        <Form form={relationForm} layout="vertical" onFinish={submitRelation}>
          <Form.Item name="code" label="关联编码" rules={[{ required: true, pattern: CODE_PATTERN }]}>
            <Input placeholder="customer_owner" />
          </Form.Item>
          <Form.Item name="displayName" label="关联名称" rules={[{ required: true, message: '请输入关联名称' }]}>
            <Input placeholder="所属客户" />
          </Form.Item>
          <Form.Item name="type" label="关联类型" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'MANY_TO_ONE', label: '多对一' },
                { value: 'ONE_TO_MANY', label: '一对多' },
                { value: 'MANY_TO_MANY', label: '多对多' }
              ]}
            />
          </Form.Item>
          <Form.Item name="targetTableId" label="目标表" rules={[{ required: true, message: '请选择目标表' }]}>
            <Select
              options={(draft?.tables ?? [])
                .filter((table) => table.id !== selectedTableId)
                .map((table) => ({ value: table.id, label: `${table.displayName} (${table.code})` }))}
            />
          </Form.Item>
          <Form.Item name="sourceFieldId" label="本表字段">
            <Select
              allowClear
              options={(selectedTable?.fields ?? []).map((field) => ({
                value: field.id,
                label: `${field.displayName} (${field.code})`
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="结构演进预览"
        open={preview !== null}
        onCancel={() => setPreview(null)}
        footer={
          <Space>
            <Button onClick={() => setPreview(null)}>关闭</Button>
            <Button
              type="primary"
              disabled={preview?.steps.length === 0}
              onClick={() => {
                setPreview(null);
                publish();
              }}
            >
              执行发布
            </Button>
          </Space>
        }
        width={900}
      >
        {preview && preview.steps.length > 0 ? (
          <Table
            rowKey={(step) => `${step.operation}-${step.tableCode}-${step.objectName}`}
            size="small"
            pagination={false}
            dataSource={preview.steps}
            columns={[
              { title: '操作', dataIndex: 'operation', width: 140 },
              { title: '表', dataIndex: 'tableCode', width: 140 },
              { title: '对象', dataIndex: 'objectName', width: 160 },
              { title: 'SQL', dataIndex: 'forwardSql' }
            ]}
          />
        ) : (
          <Empty description="没有结构差异" />
        )}
      </Modal>

      <Drawer title="迁移历史" width={720} open={history !== null} onClose={() => setHistory(null)}>
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={history ?? []}
          columns={[
            { title: '版本', render: (_, record) => `${record.fromVersion} → ${record.toVersion}`, width: 100 },
            { title: '状态', dataIndex: 'status', width: 100 },
            { title: '步数', dataIndex: 'stepCount', width: 70 },
            { title: '操作人', dataIndex: 'operator', width: 100 },
            { title: '时间', dataIndex: 'createdAt' },
            {
              title: '操作',
              width: 90,
              render: (_, record) =>
                record.status === 'APPLIED' ? (
                  <Button type="link" danger size="small" onClick={() => rollback(record)}>
                    回滚
                  </Button>
                ) : null
            }
          ]}
        />
      </Drawer>
    </Layout>
  );
}
