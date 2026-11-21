import { useEffect, useMemo, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Divider,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  Tooltip,
  Typography
} from 'antd';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  PlusOutlined
} from '@ant-design/icons';
import {
  CanvasItemShell,
  PanelField,
  PropertyPanel,
  SwitchRow,
  newId,
  uniqueCode
} from '@daos/designer';
import { EmptyState, tokens } from '@daos/ui';
import { componentFor, componentLabel } from '@daos/renderer';
import type {
  FieldLogicalType,
  FormField,
  FormLayout,
  LinkageRule,
  RelationConfig,
  SchemaApp,
  SchemaField,
  SchemaForm,
  SchemaTable,
  ValueType
} from '@daos/metadata';

const DEFAULT_LAYOUT: FormLayout = {
  scene: 'CREATE',
  columns: 24,
  labelPosition: 'top',
  labelWidth: 120,
  labelAlign: 'left',
  layoutMode: 'vertical',
  size: 'middle',
  gutter: 16,
  dense: false,
  width: '100%',
  maxWidth: 960,
  showSubmit: true,
  showReset: false,
  submitText: '提交',
  resetText: '重置',
  redirectAfterSubmit: null,
  subformSummary: false
};

function isComputed(type: FieldLogicalType): boolean {
  return type === 'rollup' || type === 'formula' || type === 'auto_number';
}

function optionsOf(field: SchemaField): Array<{ label: string; value: string }> | null {
  if (field.logicalType !== 'select' && field.logicalType !== 'multi_select') {
    return null;
  }
  const config = field.config as { options?: Array<{ label: string; code: string }> };
  return (config.options ?? []).map((option) => ({ label: option.label, value: option.code }));
}

const LINKAGE_OPERATOR_OPTIONS = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'empty', label: '为空' },
  { value: 'not_empty', label: '不为空' },
  { value: 'gt', label: '大于' },
  { value: 'lt', label: '小于' }
];

const LINKAGE_ACTION_OPTIONS = [
  { value: 'show', label: '显示' },
  { value: 'hide', label: '隐藏' },
  { value: 'require', label: '设为必填' },
  { value: 'optional', label: '恢复必填基线' },
  { value: 'readonly', label: '设为只读' },
  { value: 'editable', label: '恢复只读基线' },
  { value: 'set_value', label: '赋值' },
  { value: 'clear_value', label: '清空值' }
];

function linkageValueType(field: FormField | undefined): ValueType {
  if (!field) return 'STRING';
  if (['number', 'currency', 'percent', 'progress', 'rating'].includes(field.component)) {
    return 'NUMBER';
  }
  if (field.component === 'switch') return 'BOOLEAN';
  if (field.component === 'date') return 'DATE';
  if (field.component === 'datetime') return 'DATETIME';
  return 'STRING';
}

function LinkageValueEditor({
  field,
  value,
  onChange
}: {
  field: FormField | undefined;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  if (field && ['number', 'currency', 'percent', 'progress', 'rating'].includes(field.component)) {
    return (
      <InputNumber
        style={{ width: 180 }}
        value={typeof value === 'number' ? value : null}
        onChange={onChange}
      />
    );
  }
  if (field?.component === 'switch') {
    return (
      <Select
        style={{ width: 180 }}
        value={typeof value === 'boolean' ? value : undefined}
        options={[
          { value: true, label: '是' },
          { value: false, label: '否' }
        ]}
        onChange={onChange}
      />
    );
  }
  const options = Array.isArray(field?.props.options)
    ? field.props.options.filter(
        (item): item is { label: string; value: string } =>
          typeof item === 'object' && item !== null && 'label' in item && 'value' in item
      )
    : [];
  if (options.length > 0) {
    return (
      <Select
        style={{ width: 180 }}
        value={typeof value === 'string' ? value : undefined}
        options={options}
        onChange={onChange}
      />
    );
  }
  return (
    <Input
      style={{ width: 180 }}
      value={value === null || value === undefined ? '' : String(value)}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

/** 把建模字段转成表单项，字段语义只来自元数据，组件仅决定交互形态。 */
function toFormField(field: SchemaField, sortOrder: number): FormField {
  const options = optionsOf(field);
  const props: Record<string, unknown> = {};
  if (options && options.length > 0) {
    props.options = options;
  }
  return {
    id: newId(),
    fieldId: field.id,
    component: componentFor(field.logicalType),
    label: field.displayName,
    span: 24,
    offset: 0,
    sortOrder,
    placeholder: field.ui.placeholder ?? null,
    helpText: field.ui.helpText ?? null,
    tooltip: null,
    readonly: isComputed(field.logicalType) || field.ui.readonlyInForm,
    hidden: false,
    disabled: false,
    requiredOverride: field.constraints.required ? true : null,
    defaultValue: field.constraints.defaultValue ?? null,
    props,
    style: {},
    children: [],
    subformTableId: null,
    subformFields: [],
    minRows: 0,
    maxRows: 200,
    allowAdd: true,
    allowDelete: true,
    allowSort: true,
    showRowNumber: false,
    showSummary: false,
    summaryFields: {},
    tabKey: null,
    tabLabel: null,
    collapsible: false,
    defaultCollapsed: false,
    columnCount: 2
  };
}

export function createForm(app: SchemaApp, table: SchemaTable, displayName: string): SchemaForm {
  const code = uniqueCode(`form_${table.code}`, app.forms.map((form) => form.code));
  const timestamp = new Date().toISOString();
  return {
    id: newId(),
    appId: app.id,
    tableId: table.id,
    code,
    displayName,
    description: null,
    formType: 'RECORD',
    scenes: { CREATE: DEFAULT_LAYOUT },
    fields: table.fields.filter((field) => !field.deleted).map(toFormField),
    rules: [],
    linkages: [],
    events: [],
    status: 'DRAFT',
    version: 1,
    ext: {},
    createdBy: 'console',
    createdAt: timestamp,
    updatedBy: 'console',
    updatedAt: timestamp,
    deleted: false,
    schemaVersion: 1
  };
}

interface CreateFormValues {
  tableId: string;
  displayName: string;
}

interface SubformValues {
  childTableId: string;
  displayName: string;
  fieldIds: string[];
  minRows: number;
  maxRows: number;
  allowAdd: boolean;
  allowDelete: boolean;
}

/** 子表列：排除指向主表的关联外键字段。 */
function subformColumns(child: SchemaTable, parent: SchemaTable): SchemaField[] {
  return child.fields.filter((field) => {
    if (field.deleted) return false;
    if (field.logicalType !== 'relation') return true;
    const target = (field.config as { targetTableId?: string }).targetTableId;
    return target !== parent.id;
  });
}

/** 子表缺少指向主表的外键时自动补一个关联字段，复用运行期约定。 */
function buildSubformForeignKey(child: SchemaTable, parent: SchemaTable): SchemaField {
  const base = `${parent.code}_ref`;
  const existingCodes = child.fields.map((field) => field.code);
  let code = base;
  for (let index = 2; existingCodes.includes(code); index += 1) {
    code = `${base}_${index}`;
  }
  const timestamp = new Date().toISOString();
  const displayField =
    parent.fields.find((field) => field.nameField) ?? parent.fields[0];
  return {
    id: newId(),
    appId: child.appId,
    tableId: child.id,
    code,
    displayName: `${parent.displayName}外键`,
    description: null,
    logicalType: 'relation',
    config: {
      configType: 'relation',
      targetTableId: parent.id,
      displayFieldId: displayField?.id ?? '',
      multiple: false,
      maxSelected: 0,
      filterExpression: null,
      onDelete: 'RESTRICT',
      quickCreate: false,
      showAs: 'SELECT',
      bidirectionalFieldId: null
    } as RelationConfig,
    constraints: {
      required: false,
      unique: false,
      indexed: true,
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
      hiddenInList: true,
      hiddenInDetail: true,
      readonlyInForm: false
    },
    status: 'DRAFT',
    system: false,
    nameField: false,
    sortOrder: child.fields.length,
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

export function FormDesigner({
  draft,
  onChange,
  onTablesChange
}: {
  draft: SchemaApp;
  onChange: (forms: SchemaForm[]) => void;
  onTablesChange: (tables: SchemaTable[]) => void;
}) {
  const { message } = AntApp.useApp();
  const [selectedFormId, setSelectedFormId] = useState<string | null>(draft.forms[0]?.id ?? null);
  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [linkageOpen, setLinkageOpen] = useState(false);
  const [createFormInstance] = Form.useForm<CreateFormValues>();
  const [subformOpen, setSubformOpen] = useState(false);
  const [subformFormInstance] = Form.useForm<SubformValues>();
  const subformTableId = Form.useWatch('childTableId', subformFormInstance);

  const form = draft.forms.find((item) => item.id === selectedFormId) ?? null;
  const table = form ? draft.tables.find((item) => item.id === form.tableId) ?? null : null;
  const selectedField = form?.fields.find((item) => item.id === selectedFieldId) ?? null;
  const linkageFields = form?.fields.filter((item) => item.fieldId && item.component !== 'subform') ?? [];

  useEffect(() => {
    if (draft.forms.length === 0) {
      setSelectedFormId(null);
      setSelectedFieldId(null);
      return;
    }
    const first = draft.forms[0];
    if (first && !draft.forms.some((item) => item.id === selectedFormId)) {
      setSelectedFormId(first.id);
      setSelectedFieldId(null);
    }
  }, [draft.forms, selectedFormId]);

  const availableFields = useMemo(() => {
    if (!form || !table) return [];
    const used = new Set(form.fields.map((item) => item.fieldId).filter(Boolean));
    return table.fields.filter((field) => !field.deleted && !used.has(field.id));
  }, [form, table]);

  function patchForm(formId: string, update: (current: SchemaForm) => SchemaForm) {
    onChange(draft.forms.map((item) => (item.id === formId ? update(item) : item)));
  }

  function patchField(fieldId: string, update: (current: FormField) => FormField) {
    if (!form) return;
    patchForm(form.id, (current) => ({
      ...current,
      fields: current.fields.map((item) => (item.id === fieldId ? update(item) : item))
    }));
  }

  function patchLinkageRule(ruleId: string, update: (current: LinkageRule) => LinkageRule) {
    if (!form) return;
    patchForm(form.id, (current) => ({
      ...current,
      linkages: current.linkages.map((rule) => (rule.id === ruleId ? update(rule) : rule))
    }));
  }

  function patchCondition(
    rule: LinkageRule,
    update: Partial<LinkageRule['when'][number]>
  ) {
    patchLinkageRule(rule.id, (current) => {
      const condition = current.when[0];
      return condition ? { ...current, when: [{ ...condition, ...update }] } : current;
    });
  }

  function patchAction(
    rule: LinkageRule,
    update: Partial<LinkageRule['then'][number]>
  ) {
    patchLinkageRule(rule.id, (current) => {
      const action = current.then[0];
      return action ? { ...current, then: [{ ...action, ...update }] } : current;
    });
  }

  function addLinkageRule() {
    if (!form) return;
    const source = linkageFields[0];
    const target = linkageFields[1] ?? source;
    if (!source?.fieldId || !target?.fieldId) {
      message.error('请先向表单添加字段');
      return;
    }
    const rule: LinkageRule = {
      id: newId(),
      name: `规则 ${form.linkages.length + 1}`,
      priority: (form.linkages.length + 1) * 100,
      enabled: true,
      scenes: [],
      logic: 'AND',
      when: [
        {
          fieldId: source.fieldId,
          operator: 'eq',
          value: '',
          valueFieldId: null,
          valueType: linkageValueType(source)
        }
      ],
      then: [
        {
          fieldId: target.fieldId,
          targetFieldIds: [],
          action: 'require',
          value: null,
          expression: null
        }
      ],
      otherwise: []
    };
    patchForm(form.id, (current) => ({ ...current, linkages: [...current.linkages, rule] }));
  }

  function removeLinkageRule(ruleId: string) {
    if (!form) return;
    patchForm(form.id, (current) => ({
      ...current,
      linkages: current.linkages.filter((rule) => rule.id !== ruleId)
    }));
  }

  function submitCreate(values: CreateFormValues) {
    const target = draft.tables.find((item) => item.id === values.tableId);
    if (!target) {
      message.error('数据表不存在');
      return;
    }
    const created = createForm(draft, target, values.displayName.trim());
    onChange([...draft.forms, created]);
    setSelectedFormId(created.id);
    setSelectedFieldId(null);
    setCreateOpen(false);
    createFormInstance.resetFields();
    message.success('表单已创建，记得保存并发布');
  }

  const subformTables = useMemo(() => {
    if (!form || !table) return [];
    return draft.tables.filter(
      (candidate) => !candidate.deleted && candidate.id !== table.id
    );
  }, [draft.tables, form, table]);

  const subformColumnCandidates = useMemo(() => {
    if (!table || !subformTableId) return [];
    const child = draft.tables.find((item) => item.id === subformTableId);
    return child ? subformColumns(child, table) : [];
  }, [draft.tables, subformTableId, table]);

  function openSubform() {
    const first = subformTables[0];
    subformFormInstance.resetFields();
    subformFormInstance.setFieldsValue({
      childTableId: first?.id ?? '',
      displayName: first?.displayName ?? '明细',
      fieldIds: first && table ? subformColumns(first, table).map((field) => field.id) : [],
      minRows: 0,
      maxRows: 200,
      allowAdd: true,
      allowDelete: true
    });
    setSubformOpen(true);
  }

  function submitSubform(values: SubformValues) {
    if (!form || !table) return;
    const child = draft.tables.find((item) => item.id === values.childTableId);
    if (!child) {
      message.error('子表不存在');
      return;
    }
    const columns = subformColumns(child, table).filter((field) =>
      (values.fieldIds ?? []).includes(field.id)
    );
    if (columns.length === 0) {
      message.error('请至少选择一列');
      return;
    }
    const hasForeignKey = child.fields.some(
      (field) =>
        !field.deleted &&
        field.logicalType === 'relation' &&
        (field.config as { targetTableId?: string }).targetTableId === table.id
    );
    if (!hasForeignKey) {
      const foreignKey = buildSubformForeignKey(child, table);
      onTablesChange(
        draft.tables.map((item) =>
          item.id === child.id ? { ...item, fields: [...item.fields, foreignKey] } : item
        )
      );
    }
    const item: FormField = {
      id: newId(),
      fieldId: null,
      component: 'subform',
      label: values.displayName.trim() || child.displayName,
      span: 24,
      offset: 0,
      sortOrder: form.fields.length,
      placeholder: null,
      helpText: null,
      tooltip: null,
      readonly: false,
      hidden: false,
      disabled: false,
      requiredOverride: null,
      defaultValue: null,
      props: {},
      style: {},
      children: [],
      subformTableId: child.id,
      subformFields: columns.map((field, index) => toFormField(field, index)),
      minRows: values.minRows ?? 0,
      maxRows: values.maxRows ?? 200,
      allowAdd: values.allowAdd ?? true,
      allowDelete: values.allowDelete ?? true,
      allowSort: true,
      showRowNumber: true,
      showSummary: false,
      summaryFields: {},
      tabKey: null,
      tabLabel: null,
      collapsible: false,
      defaultCollapsed: false,
      columnCount: 2
    };
    patchForm(form.id, (current) => ({ ...current, fields: [...current.fields, item] }));
    setSelectedFieldId(item.id);
    setSubformOpen(false);
    message.success('子表单已添加，记得保存并发布');
  }

  function addField(field: SchemaField) {
    if (!form) return;
    const item = toFormField(field, form.fields.length);
    patchForm(form.id, (current) => ({ ...current, fields: [...current.fields, item] }));
    setSelectedFieldId(item.id);
  }

  function moveField(index: number, direction: -1 | 1) {
    if (!form) return;
    const target = index + direction;
    if (target < 0 || target >= form.fields.length) return;
    const fields = [...form.fields];
    const item = fields[index];
    if (!item) return;
    fields.splice(index, 1);
    fields.splice(target, 0, item);
    patchForm(form.id, (current) => ({
      ...current,
      fields: fields.map((field, order) => ({ ...field, sortOrder: order }))
    }));
  }

  function removeField(fieldId: string) {
    if (!form) return;
    patchForm(form.id, (current) => ({
      ...current,
      fields: current.fields
        .filter((item) => item.id !== fieldId)
        .map((field, order) => ({ ...field, sortOrder: order }))
    }));
    if (selectedFieldId === fieldId) {
      setSelectedFieldId(null);
    }
  }

  const formList = (
    <Card
      size="small"
      title="表单"
      extra={
        <Button
          size="small"
          type="text"
          icon={<PlusOutlined />}
          disabled={draft.tables.length === 0}
          onClick={() => {
            createFormInstance.resetFields();
            createFormInstance.setFieldsValue({
              tableId: draft.tables[0]?.id,
              displayName: draft.tables[0] ? `${draft.tables[0].displayName}表单` : ''
            });
            setCreateOpen(true);
          }}
        >
          新建
        </Button>
      }
    >
      {draft.forms.length === 0 ? (
        <div style={{ margin: '12px 0' }}>
          <EmptyState compact title="还没有表单" />
        </div>
      ) : (
        <List
          size="small"
          dataSource={draft.forms}
          renderItem={(item) => {
            const boundTable = draft.tables.find((candidate) => candidate.id === item.tableId);
            return (
              <List.Item
                style={{
                  cursor: 'pointer',
                  borderRadius: tokens.radiusSm,
                  paddingInline: 10,
                  background: item.id === selectedFormId ? tokens.colorPrimaryBg : undefined
                }}
                onClick={() => {
                  setSelectedFormId(item.id);
                  setSelectedFieldId(null);
                }}
              >
                <List.Item.Meta
                  title={
                    <span style={{ color: boundTable ? undefined : tokens.colorError }}>
                      {item.displayName}
                    </span>
                  }
                  description={
                    boundTable ? `${boundTable.displayName} · ${item.fields.length} 项` : '所属表已删除'
                  }
                />
              </List.Item>
            );
          }}
        />
      )}
    </Card>
  );

  const palette = (
    <Card
      size="small"
      title="数据表字段"
      style={{ marginTop: 12 }}
      extra={
        form && table ? (
          <Button
            size="small"
            type="text"
            icon={<PlusOutlined />}
            disabled={subformTables.length === 0}
            onClick={openSubform}
          >
            子表单
          </Button>
        ) : null
      }
    >
      {!form || !table ? (
        <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
          选择表单后添加字段
        </Typography.Text>
      ) : availableFields.length === 0 ? (
        <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
          该表字段已全部加入表单
        </Typography.Text>
      ) : (
        <List
          size="small"
          dataSource={availableFields}
          renderItem={(field) => (
            <List.Item
              style={{ paddingInline: 4, cursor: 'pointer' }}
              onClick={() => addField(field)}
              actions={[
                <Tooltip title="添加到表单" key="add">
                  <PlusOutlined style={{ color: tokens.colorPrimary }} />
                </Tooltip>
              ]}
            >
              <List.Item.Meta
                title={field.displayName}
                description={componentLabel(componentFor(field.logicalType))}
              />
            </List.Item>
          )}
        />
      )}
    </Card>
  );

  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', minWidth: 0 }}>
      <div style={{ width: 240, flex: '0 0 240px' }}>
        {formList}
        {palette}
      </div>

      <Card
        size="small"
        style={{ flex: '1 1 420px', minWidth: 0 }}
        title={form ? form.displayName : '表单画布'}
        extra={
          form ? (
            <Space size={6}>
              <Button size="small" onClick={() => setLinkageOpen(true)}>
                联动规则
              </Button>
              <Tag style={{ marginInlineEnd: 0 }}>{form.fields.length} 项</Tag>
              <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                需发布后生效
              </Tag>
            </Space>
          ) : null
        }
      >
        {!form ? (
          <div style={{ padding: '40px 0' }}>
            <EmptyState title="新建一个表单开始设计" />
          </div>
        ) : !table ? (
          <div style={{ padding: '40px 0' }}>
            <EmptyState title="表单所属数据表已不存在，请重新创建表单" />
          </div>
        ) : form.fields.length === 0 ? (
          <div style={{ padding: '40px 0' }}>
            <EmptyState title="从左侧添加字段" />
          </div>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {form.fields.map((item, index) => {
              const isSubform = item.component === 'subform';
              const bound = isSubform
                ? undefined
                : table.fields.find((field) => field.id === item.fieldId);
              const missing = !isSubform && !bound;
              const subformTable = isSubform
                ? draft.tables.find((candidate) => candidate.id === item.subformTableId)
                : null;
              return (
                <CanvasItemShell
                  key={item.id}
                  active={item.id === selectedFieldId}
                  onClick={() => setSelectedFieldId(item.id)}
                >
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                      <Typography.Text strong>{item.label || bound?.displayName || '未命名字段'}</Typography.Text>
                      {item.requiredOverride || bound?.constraints.required ? (
                        <Tag color="red" style={{ marginInlineEnd: 0 }}>
                          必填
                        </Tag>
                      ) : null}
                      {item.readonly ? (
                        <Tag style={{ marginInlineEnd: 0 }}>只读</Tag>
                      ) : null}
                      {isSubform ? (
                        <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                          明细表
                        </Tag>
                      ) : null}
                      {missing ? (
                        <Tag color="error" style={{ marginInlineEnd: 0 }}>
                          字段已删除
                        </Tag>
                      ) : null}
                    </div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {componentLabel(item.component)}
                      {isSubform
                        ? ` · ${subformTable?.displayName ?? '子表已删除'} · ${item.subformFields.length} 列`
                        : bound
                          ? ` · ${bound.code}`
                          : ''}
                      {item.span < 24 ? ` · 占 ${Math.round((item.span / 24) * 100)}%` : ''}
                    </Typography.Text>
                  </div>
                  <Space size={2} onClick={(event) => event.stopPropagation()}>
                    <Button
                      size="small"
                      type="text"
                      icon={<ArrowUpOutlined />}
                      disabled={index === 0}
                      onClick={() => moveField(index, -1)}
                    />
                    <Button
                      size="small"
                      type="text"
                      icon={<ArrowDownOutlined />}
                      disabled={index === form.fields.length - 1}
                      onClick={() => moveField(index, 1)}
                    />
                    <Button
                      size="small"
                      type="text"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={() => removeField(item.id)}
                    />
                  </Space>
                </CanvasItemShell>
              );
            })}
          </Space>
        )}
      </Card>

      <PropertyPanel>
        {!form ? (
          <Typography.Text type="secondary">选择表单后编辑属性</Typography.Text>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={12}>
            <div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                表单
              </Typography.Text>
              <Input
                style={{ marginTop: 4 }}
                value={form.displayName}
                maxLength={40}
                onChange={(event) =>
                  patchForm(form.id, (current) => ({
                    ...current,
                    displayName: event.target.value
                  }))
                }
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                绑定数据表：{table?.displayName ?? '已删除'}
              </Typography.Text>
            </div>
            <Divider style={{ margin: 0 }} />
            {!selectedField ? (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                点击画布中的字段编辑属性
              </Typography.Text>
            ) : (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <PanelField label="显示名称">
                  <Input
                    value={selectedField.label ?? ''}
                    maxLength={40}
                    onChange={(event) =>
                      patchField(selectedField.id, (current) => ({
                        ...current,
                        label: event.target.value
                      }))
                    }
                  />
                </PanelField>
                <PanelField label="占宽">
                  <Select
                    style={{ width: '100%' }}
                    value={selectedField.span}
                    options={[
                      { value: 24, label: '整行' },
                      { value: 12, label: '半行' },
                      { value: 8, label: '三分之一' }
                    ]}
                    onChange={(value) =>
                      patchField(selectedField.id, (current) => ({ ...current, span: value }))
                    }
                  />
                </PanelField>
                {selectedField.component === 'subform' ? (
                  <>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      子表：
                      {draft.tables.find((item) => item.id === selectedField.subformTableId)
                        ?.displayName ?? '已删除'}
                      {' · '}
                      {selectedField.subformFields.length} 列
                    </Typography.Text>
                    <PanelField label="行数下限 / 上限">
                      <Space style={{ width: '100%' }}>
                        <InputNumber
                          min={0}
                          max={200}
                          value={selectedField.minRows}
                          onChange={(value) =>
                            patchField(selectedField.id, (current) => ({
                              ...current,
                              minRows: value ?? 0
                            }))
                          }
                        />
                        <InputNumber
                          min={1}
                          max={200}
                          value={selectedField.maxRows}
                          onChange={(value) =>
                            patchField(selectedField.id, (current) => ({
                              ...current,
                              maxRows: value ?? 200
                            }))
                          }
                        />
                      </Space>
                    </PanelField>
                    <SwitchRow
                      label="允许新增行"
                      checked={selectedField.allowAdd}
                      onChange={(checked) =>
                        patchField(selectedField.id, (current) => ({
                          ...current,
                          allowAdd: checked
                        }))
                      }
                    />
                    <SwitchRow
                      label="允许删除行"
                      checked={selectedField.allowDelete}
                      onChange={(checked) =>
                        patchField(selectedField.id, (current) => ({
                          ...current,
                          allowDelete: checked
                        }))
                      }
                    />
                  </>
                ) : (
                  <>
                    <SwitchRow
                      label="必填"
                      checked={Boolean(
                        selectedField.requiredOverride ||
                          table?.fields.find((field) => field.id === selectedField.fieldId)?.constraints
                            .required
                      )}
                      disabled={Boolean(
                        table?.fields.find((field) => field.id === selectedField.fieldId)?.constraints
                          .required
                      )}
                      onChange={(checked) =>
                        patchField(selectedField.id, (current) => ({
                          ...current,
                          requiredOverride: checked ? true : null
                        }))
                      }
                    />
                    <SwitchRow
                      label="只读"
                      checked={selectedField.readonly}
                      onChange={(checked) =>
                        patchField(selectedField.id, (current) => ({ ...current, readonly: checked }))
                      }
                    />
                    <PanelField label="占位提示">
                      <Input
                        value={selectedField.placeholder ?? ''}
                        maxLength={64}
                        onChange={(event) =>
                          patchField(selectedField.id, (current) => ({
                            ...current,
                            placeholder: event.target.value.trim() ? event.target.value : null
                          }))
                        }
                      />
                    </PanelField>
                    <PanelField label="帮助文字">
                      <Input.TextArea
                        rows={2}
                        maxLength={200}
                        value={selectedField.helpText ?? ''}
                        onChange={(event) =>
                          patchField(selectedField.id, (current) => ({
                            ...current,
                            helpText: event.target.value.trim() ? event.target.value : null
                          }))
                        }
                      />
                    </PanelField>
                  </>
                )}
              </Space>
            )}
          </Space>
        )}
      </PropertyPanel>

      <Modal
        title="联动规则"
        open={linkageOpen}
        onCancel={() => setLinkageOpen(false)}
        footer={
          <Button type="primary" onClick={() => setLinkageOpen(false)}>
            完成
          </Button>
        }
        width={920}
        destroyOnHidden
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space style={{ width: '100%', justifyContent: 'space-between' }}>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              每条规则包含一个条件和一个命中动作；未命中时的恢复动作可用相反条件配置。
            </Typography.Text>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={linkageFields.length === 0}
              onClick={addLinkageRule}
            >
              添加规则
            </Button>
          </Space>
          {!form || form.linkages.length === 0 ? (
            <EmptyState compact title="还没有联动规则" />
          ) : (
            form.linkages.map((rule) => {
              const condition = rule.when[0];
              const action = rule.then[0];
              if (!condition || !action) return null;
              const source = linkageFields.find((field) => field.fieldId === condition.fieldId);
              const target = linkageFields.find((field) => field.fieldId === action.fieldId);
              const valueOptional = condition.operator === 'empty' || condition.operator === 'not_empty';
              return (
                <Card
                  key={rule.id}
                  size="small"
                  title={
                    <Input
                      style={{ maxWidth: 240 }}
                      value={rule.name ?? ''}
                      maxLength={40}
                      onChange={(event) =>
                        patchLinkageRule(rule.id, (current) => ({
                          ...current,
                          name: event.target.value
                        }))
                      }
                    />
                  }
                  extra={
                    <Space>
                      <Switch
                        size="small"
                        checked={rule.enabled}
                        onChange={(enabled) =>
                          patchLinkageRule(rule.id, (current) => ({ ...current, enabled }))
                        }
                      />
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => removeLinkageRule(rule.id)}
                      />
                    </Space>
                  }
                >
                  <Space direction="vertical" style={{ width: '100%' }} size={10}>
                    <Space wrap size={8}>
                      <Typography.Text strong>当</Typography.Text>
                      <Select
                        style={{ width: 170 }}
                        value={condition.fieldId}
                        options={linkageFields.map((field) => ({
                          value: field.fieldId,
                          label: field.label ?? field.fieldId
                        }))}
                        onChange={(fieldId) =>
                          patchCondition(rule, {
                            fieldId,
                            value: null,
                            valueFieldId: null,
                            valueType: linkageValueType(
                              linkageFields.find((field) => field.fieldId === fieldId)
                            )
                          })
                        }
                      />
                      <Select
                        style={{ width: 110 }}
                        value={condition.operator}
                        options={LINKAGE_OPERATOR_OPTIONS}
                        onChange={(operator) =>
                          patchCondition(rule, {
                            operator,
                            value: null,
                            valueFieldId: null
                          })
                        }
                      />
                      {!valueOptional ? (
                        <>
                          <Select
                            style={{ width: 110 }}
                            value={condition.valueFieldId ? 'FIELD' : 'STATIC'}
                            options={[
                              { value: 'STATIC', label: '固定值' },
                              { value: 'FIELD', label: '字段值' }
                            ]}
                            onChange={(mode) =>
                              patchCondition(rule, {
                                value: null,
                                valueFieldId:
                                  mode === 'FIELD' ? linkageFields[0]?.fieldId ?? null : null
                              })
                            }
                          />
                          {condition.valueFieldId ? (
                            <Select
                              style={{ width: 180 }}
                              value={condition.valueFieldId}
                              options={linkageFields.map((field) => ({
                                value: field.fieldId,
                                label: field.label ?? field.fieldId
                              }))}
                              onChange={(valueFieldId) => patchCondition(rule, { valueFieldId })}
                            />
                          ) : (
                            <LinkageValueEditor
                              field={source}
                              value={condition.value}
                              onChange={(value) => patchCondition(rule, { value })}
                            />
                          )}
                        </>
                      ) : null}
                    </Space>
                    <Space wrap size={8}>
                      <Typography.Text strong>则</Typography.Text>
                      <Select
                        style={{ width: 170 }}
                        value={action.fieldId ?? undefined}
                        options={linkageFields.map((field) => ({
                          value: field.fieldId,
                          label: field.label ?? field.fieldId
                        }))}
                        onChange={(fieldId) => patchAction(rule, { fieldId, value: null })}
                      />
                      <Select
                        style={{ width: 150 }}
                        value={action.action}
                        options={LINKAGE_ACTION_OPTIONS}
                        onChange={(nextAction) =>
                          patchAction(rule, { action: nextAction, value: null })
                        }
                      />
                      {action.action === 'set_value' ? (
                        <LinkageValueEditor
                          field={target}
                          value={action.value}
                          onChange={(value) => patchAction(rule, { value })}
                        />
                      ) : null}
                    </Space>
                  </Space>
                </Card>
              );
            })
          )}
        </Space>
      </Modal>

      <Modal
        title="新建表单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createFormInstance.submit()}
        okText="创建"
        cancelText="取消"
        destroyOnHidden
        width={440}
      >
        <Form form={createFormInstance} layout="vertical" onFinish={submitCreate}>
          <Form.Item
            name="tableId"
            label="数据表"
            rules={[{ required: true, message: '请选择数据表' }]}
          >
            <Select
              options={draft.tables
                .filter((item) => !item.deleted)
                .map((item) => ({ value: item.id, label: item.displayName }))}
              onChange={(value) => {
                const target = draft.tables.find((item) => item.id === value);
                if (target) {
                  createFormInstance.setFieldsValue({ displayName: `${target.displayName}表单` });
                }
              }}
            />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="表单名称"
            rules={[{ required: true, message: '请输入表单名称' }]}
          >
            <Input placeholder="客户表单" maxLength={40} />
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            创建后会自动加入该表的全部字段，可再调整顺序与属性。
          </Typography.Text>
        </Form>
      </Modal>

      <Modal
        title="添加子表单"
        open={subformOpen}
        onCancel={() => setSubformOpen(false)}
        onOk={() => subformFormInstance.submit()}
        okText="添加"
        cancelText="取消"
        destroyOnHidden
        width={520}
      >
        <Form form={subformFormInstance} layout="vertical" onFinish={submitSubform}>
          <Form.Item
            name="childTableId"
            label="子表"
            rules={[{ required: true, message: '请选择子表' }]}
          >
            <Select
              options={subformTables.map((item) => ({
                value: item.id,
                label: item.displayName
              }))}
              onChange={(value) => {
                const target = draft.tables.find((item) => item.id === value);
                if (target && table) {
                  subformFormInstance.setFieldsValue({
                    displayName: target.displayName,
                    fieldIds: subformColumns(target, table).map((field) => field.id)
                  });
                }
              }}
            />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="显示名称"
            rules={[{ required: true, message: '请输入显示名称' }]}
          >
            <Input maxLength={40} />
          </Form.Item>
          <Form.Item
            name="fieldIds"
            label="展示列"
            rules={[{ required: true, message: '请选择展示列' }]}
          >
            <Select
              mode="multiple"
              options={subformColumnCandidates.map((field) => ({
                value: field.id,
                label: field.displayName
              }))}
            />
          </Form.Item>
          <Space size={16}>
            <Form.Item name="minRows" label="最少行数">
              <InputNumber min={0} max={200} />
            </Form.Item>
            <Form.Item name="maxRows" label="最多行数">
              <InputNumber min={1} max={200} />
            </Form.Item>
          </Space>
          <Space size={24}>
            <Form.Item name="allowAdd" label="允许新增行" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="allowDelete" label="允许删除行" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            缺少外键时会自动在子表创建一个指向当前主表的关联字段，该列不会出现在展示列中。
          </Typography.Text>
        </Form>
      </Modal>
    </div>
  );
}
