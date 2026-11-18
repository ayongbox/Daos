import { useEffect, useMemo, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Divider,
  Empty,
  Form,
  Input,
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
import { tokens } from '@daos/ui';
import type {
  FieldLogicalType,
  FormComponent,
  FormField,
  FormLayout,
  SchemaApp,
  SchemaField,
  SchemaForm,
  SchemaTable
} from '@daos/metadata';

const CODE_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

function newId(): string {
  let time = Date.now();
  let out = '';
  for (let i = 0; i < 10; i += 1) {
    out = CODE_ALPHABET.charAt(time % 32) + out;
    time = Math.floor(time / 32);
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  for (const byte of bytes) {
    out += CODE_ALPHABET.charAt(byte % 32);
  }
  return out;
}

function uniqueCode(base: string, existing: string[]): string {
  if (!existing.includes(base)) return base;
  for (let index = 2; index < 100; index += 1) {
    const candidate = `${base}_${index}`;
    if (!existing.includes(candidate)) return candidate;
  }
  return `${base}_${Date.now()}`;
}

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

const COMPONENT_LABELS: Record<string, string> = {
  input: '单行文本',
  textarea: '多行文本',
  richtext: '富文本',
  number: '数字',
  select: '单选',
  multi_select: '多选',
  switch: '开关',
  date: '日期',
  datetime: '日期时间',
  member: '成员',
  member_multi: '成员（多选）',
  department: '部门',
  department_multi: '部门（多选）',
  attachment: '附件',
  relation: '关联记录',
  rollup: '引用汇总',
  formula: '公式',
  auto_number: '自动编号',
  rating: '评分',
  progress: '进度',
  location: '地理位置',
  barcode: '条码'
};

function componentLabel(component: FormComponent): string {
  return COMPONENT_LABELS[component] ?? component;
}

function componentFor(type: FieldLogicalType): FormComponent {
  switch (type) {
    case 'text_long':
      return 'textarea';
    case 'rich_text':
      return 'richtext';
    case 'number':
    case 'decimal':
      return 'number';
    case 'date':
      return 'date';
    case 'datetime':
      return 'datetime';
    case 'select':
      return 'select';
    case 'multi_select':
      return 'multi_select';
    case 'boolean':
      return 'switch';
    case 'member':
      return 'member';
    case 'member_multi':
      return 'member_multi';
    case 'department':
      return 'department';
    case 'department_multi':
      return 'department_multi';
    case 'attachment':
      return 'attachment';
    case 'relation':
      return 'relation';
    case 'rollup':
      return 'rollup';
    case 'formula':
      return 'formula';
    case 'auto_number':
      return 'auto_number';
    case 'rating':
      return 'rating';
    case 'progress':
      return 'progress';
    case 'location':
      return 'location';
    case 'barcode':
      return 'barcode';
    default:
      return 'input';
  }
}

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

function createForm(app: SchemaApp, table: SchemaTable, displayName: string): SchemaForm {
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

export function FormDesigner({
  draft,
  onChange
}: {
  draft: SchemaApp;
  onChange: (forms: SchemaForm[]) => void;
}) {
  const { message } = AntApp.useApp();
  const [selectedFormId, setSelectedFormId] = useState<string | null>(draft.forms[0]?.id ?? null);
  const [selectedFieldId, setSelectedFieldId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createFormInstance] = Form.useForm<CreateFormValues>();

  const form = draft.forms.find((item) => item.id === selectedFormId) ?? null;
  const table = form ? draft.tables.find((item) => item.id === form.tableId) ?? null : null;
  const selectedField = form?.fields.find((item) => item.id === selectedFieldId) ?? null;

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
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="还没有表单"
          style={{ margin: '12px 0' }}
        />
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
    <Card size="small" title="数据表字段" style={{ marginTop: 12 }}>
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
              <Tag style={{ marginInlineEnd: 0 }}>{form.fields.length} 项</Tag>
              <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                需发布后生效
              </Tag>
            </Space>
          ) : null
        }
      >
        {!form ? (
          <Empty description="新建一个表单开始设计" style={{ padding: '40px 0' }} />
        ) : !table ? (
          <Empty description="表单所属数据表已不存在，请重新创建表单" style={{ padding: '40px 0' }} />
        ) : form.fields.length === 0 ? (
          <Empty description="从左侧添加字段" style={{ padding: '40px 0' }} />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {form.fields.map((item, index) => {
              const bound = table.fields.find((field) => field.id === item.fieldId);
              const missing = !bound;
              return (
                <div
                  key={item.id}
                  onClick={() => setSelectedFieldId(item.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    padding: '10px 12px',
                    borderRadius: tokens.radiusSm,
                    border: `1px solid ${
                      item.id === selectedFieldId ? tokens.colorPrimary : tokens.colorBorder
                    }`,
                    background:
                      item.id === selectedFieldId ? tokens.colorPrimaryBg : tokens.colorBgContainer,
                    cursor: 'pointer'
                  }}
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
                      {missing ? (
                        <Tag color="error" style={{ marginInlineEnd: 0 }}>
                          字段已删除
                        </Tag>
                      ) : null}
                    </div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {componentLabel(item.component)}
                      {bound ? ` · ${bound.code}` : ''}
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
                </div>
              );
            })}
          </Space>
        )}
      </Card>

      <Card size="small" title="属性" style={{ width: 280, flex: '0 0 280px' }}>
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
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    显示名称
                  </Typography.Text>
                  <Input
                    style={{ marginTop: 4 }}
                    value={selectedField.label ?? ''}
                    maxLength={40}
                    onChange={(event) =>
                      patchField(selectedField.id, (current) => ({
                        ...current,
                        label: event.target.value
                      }))
                    }
                  />
                </div>
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    占宽
                  </Typography.Text>
                  <Select
                    style={{ marginTop: 4, width: '100%' }}
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
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 13 }}>必填</span>
                  <Switch
                    size="small"
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
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 13 }}>只读</span>
                  <Switch
                    size="small"
                    checked={selectedField.readonly}
                    onChange={(checked) =>
                      patchField(selectedField.id, (current) => ({ ...current, readonly: checked }))
                    }
                  />
                </div>
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    占位提示
                  </Typography.Text>
                  <Input
                    style={{ marginTop: 4 }}
                    value={selectedField.placeholder ?? ''}
                    maxLength={64}
                    onChange={(event) =>
                      patchField(selectedField.id, (current) => ({
                        ...current,
                        placeholder: event.target.value.trim() ? event.target.value : null
                      }))
                    }
                  />
                </div>
                <div>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    帮助文字
                  </Typography.Text>
                  <Input.TextArea
                    style={{ marginTop: 4 }}
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
                </div>
              </Space>
            )}
          </Space>
        )}
      </Card>

      <Modal
        title="新建表单"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createFormInstance.submit()}
        okText="创建"
        cancelText="取消"
        destroyOnClose
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
    </div>
  );
}
