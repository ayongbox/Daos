import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  DatePicker,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Rate,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  TimePicker,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import {
  api,
  type FormRecordDetail,
  type FormRenderResult,
  type FormSummary,
  type RecordPage,
  type RelationOption,
  type RenderNode
} from './api';

const READONLY_COMPONENTS = new Set([
  'formula',
  'rollup',
  'auto_number',
  'creator',
  'create_time',
  'modifier',
  'modify_time',
  'record_id'
]);

function isReadonly(node: RenderNode): boolean {
  return node.readonly || READONLY_COMPONENTS.has(node.component);
}

function mainFieldNodes(nodes: RenderNode[]): RenderNode[] {
  return nodes.flatMap((node) => {
    if (node.component === 'subform') return [];
    const own = node.fieldCode ? [node] : [];
    return [...own, ...mainFieldNodes(node.children)];
  });
}

function subformNodes(nodes: RenderNode[]): RenderNode[] {
  return nodes.flatMap((node) =>
    node.component === 'subform' ? [node] : subformNodes(node.children)
  );
}

function normalizeValue(component: string, value: unknown): unknown {
  if (value === null || value === undefined) return value;
  if (typeof value !== 'object') return value;
  const formattable = value as { format?: (pattern: string) => string; toISOString?: () => string };
  if (component === 'date' && formattable.format) {
    return formattable.format('YYYY-MM-DD');
  }
  if (component === 'time' && formattable.format) {
    return formattable.format('HH:mm:ss');
  }
  if (formattable.toISOString) {
    return formattable.toISOString();
  }
  return value;
}

function optionsOf(node: RenderNode): Array<{ label: string; value: string }> {
  const options = node.props.options;
  if (!Array.isArray(options)) return [];
  return options.filter(
    (item): item is { label: string; value: string } =>
      typeof item === 'object' && item !== null && 'label' in item && 'value' in item
  );
}

function RelationSelect({
  appId,
  tableCode,
  node,
  value,
  onChange
}: {
  appId: string;
  tableCode: string;
  node: RenderNode;
  value?: unknown;
  onChange?: (value: unknown) => void;
}) {
  const [options, setOptions] = useState<RelationOption[]>([]);
  const [loading, setLoading] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fieldCode = node.fieldCode ?? '';
  const multiple = node.props.multiple === true;
  const placeholder =
    typeof node.props.placeholder === 'string' ? node.props.placeholder : '搜索并选择关联记录';

  function load(keyword = '') {
    if (!fieldCode) return;
    setLoading(true);
    api
      .relationOptions(appId, tableCode, fieldCode, keyword)
      .then(setOptions)
      .catch(() => setOptions([]))
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [appId, tableCode, fieldCode]);

  return (
    <Select
      mode={multiple ? 'multiple' : undefined}
      allowClear
      showSearch
      filterOption={false}
      loading={loading}
      disabled={isReadonly(node)}
      placeholder={placeholder}
      options={options}
      value={value as string | string[] | undefined}
      onChange={(next) => onChange?.(next)}
      onSearch={(text) => {
        if (timer.current) clearTimeout(timer.current);
        timer.current = setTimeout(() => load(text), 300);
      }}
    />
  );
}

function control(node: RenderNode, appId: string, tableCode: string) {
  const disabled = isReadonly(node);
  const placeholder = typeof node.props.placeholder === 'string' ? node.props.placeholder : undefined;
  const options = optionsOf(node);

  switch (node.component) {
    case 'textarea':
    case 'richtext':
      return <Input.TextArea rows={3} disabled={disabled} placeholder={placeholder} />;
    case 'number':
    case 'currency':
    case 'percent':
    case 'progress':
      return <InputNumber style={{ width: '100%' }} disabled={disabled} placeholder={placeholder} />;
    case 'select':
      return options.length > 0 ? (
        <Select allowClear disabled={disabled} placeholder={placeholder} options={options} />
      ) : (
        <Input disabled={disabled} placeholder={placeholder} />
      );
    case 'multi_select':
      return options.length > 0 ? (
        <Select mode="multiple" allowClear disabled={disabled} placeholder={placeholder} options={options} />
      ) : (
        <Select mode="tags" disabled={disabled} placeholder={placeholder} />
      );
    case 'radio':
      return <Select allowClear disabled={disabled} placeholder={placeholder} options={options} />;
    case 'checkbox':
      return (
        <Select mode="multiple" allowClear disabled={disabled} placeholder={placeholder} options={options} />
      );
    case 'switch':
      return <Switch disabled={disabled} />;
    case 'date':
      return <DatePicker style={{ width: '100%' }} disabled={disabled} />;
    case 'datetime':
      return <DatePicker style={{ width: '100%' }} showTime disabled={disabled} />;
    case 'time':
      return <TimePicker style={{ width: '100%' }} disabled={disabled} />;
    case 'rating':
      return <Rate disabled={disabled} />;
    case 'relation':
      return <RelationSelect appId={appId} tableCode={tableCode} node={node} />;
    default:
      return <Input disabled={disabled} placeholder={placeholder} />;
  }
}

function SubformEditor({ node, appId }: { node: RenderNode; appId: string }) {
  const childTableCode =
    typeof node.props.tableCode === 'string' ? node.props.tableCode : '';
  const allowAdd = node.props.allowAdd !== false;
  const allowDelete = node.props.allowDelete !== false;
  const minRows = typeof node.props.minRows === 'number' ? node.props.minRows : 0;
  const maxRows = typeof node.props.maxRows === 'number' ? node.props.maxRows : 200;
  const showRowNumber = node.props.showRowNumber !== false;
  const columns = node.children.filter((child) => child.fieldCode);

  return (
    <Form.List name={['__subforms', node.id]}>
      {(rows, { add, remove }) => {
        const tableColumns: ColumnsType<{ key: number; name: number }> = [
          ...(showRowNumber
            ? [
                {
                  title: '#',
                  key: '__index',
                  width: 48,
                  render: (_: unknown, __: { key: number }, index: number) => index + 1
                }
              ]
            : []),
          ...columns.map((child) => ({
            title: child.label ?? child.fieldCode,
            key: child.id,
            render: (_: unknown, row: { key: number; name: number }) => (
              <Form.Item name={[row.name, child.fieldCode as string]} noStyle>
                {control(child, appId, childTableCode)}
              </Form.Item>
            )
          })),
          ...(allowDelete
            ? [
                {
                  title: '',
                  key: '__actions',
                  width: 64,
                  render: (_: unknown, row: { key: number; name: number }) => (
                    <Button type="link" danger size="small" onClick={() => remove(row.name)}>
                      删除
                    </Button>
                  )
                }
              ]
            : [])
        ];

        return (
          <div style={{ width: '100%', paddingInline: 8, marginBottom: 16 }}>
            <Table
              rowKey="key"
              size="small"
              pagination={false}
              scroll={{ x: 600 }}
              dataSource={rows}
              columns={tableColumns}
              locale={{ emptyText: '暂无明细' }}
            />
            <Space style={{ marginTop: 8 }}>
              {allowAdd ? (
                <Button size="small" onClick={() => add()} disabled={rows.length >= maxRows}>
                  新增行
                </Button>
              ) : null}
              {minRows > 0 ? (
                <Typography.Text type="secondary">至少 {minRows} 行</Typography.Text>
              ) : null}
              {rows.length >= maxRows ? (
                <Typography.Text type="secondary">最多 {maxRows} 行</Typography.Text>
              ) : null}
            </Space>
          </div>
        );
      }}
    </Form.List>
  );
}

function RecordDetail({
  appId,
  formCode,
  recordId,
  nodes
}: {
  appId: string;
  formCode: string;
  recordId: string;
  nodes: RenderNode[];
}) {
  const [detail, setDetail] = useState<FormRecordDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api
      .formRecord(appId, formCode, recordId)
      .then((value) => {
        if (!cancelled) setDetail(value);
      })
      .catch(() => {
        if (!cancelled) setDetail(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [appId, formCode, recordId]);

  if (loading) return <Spin size="small" />;
  if (!detail) return <Typography.Text type="secondary">明细加载失败</Typography.Text>;

  const blocks = nodes
    .map((node) => ({ node, rows: detail.subforms[node.id] ?? [] }))
    .filter((block) => block.rows.length > 0);
  if (blocks.length === 0) return <Typography.Text type="secondary">没有明细数据</Typography.Text>;

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      {blocks.map(({ node, rows }) => {
        const columns: ColumnsType<Record<string, unknown>> = node.children
          .filter((child) => child.fieldCode)
          .map((child) => {
            const code = child.fieldCode as string;
            return {
              title: child.label ?? code,
              dataIndex: code,
              key: code,
              render: (value: unknown, record: Record<string, unknown>) => {
                const display = record[`${code}_display`];
                if (display !== null && display !== undefined) return String(display);
                if (value === null || value === undefined) return '-';
                return Array.isArray(value) ? value.join('、') : String(value);
              }
            };
          });
        return (
          <div key={node.id}>
            <Typography.Text strong>{node.label ?? '明细'}</Typography.Text>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              scroll={{ x: 600 }}
              columns={columns}
              dataSource={rows}
            />
          </div>
        );
      })}
    </Space>
  );
}

function renderNode(node: RenderNode, appId: string, tableCode: string): ReactNode {
  if (node.component === 'divider') {
    return <Divider key={node.id} style={{ margin: '8px 0' }} />;
  }
  if (node.component === 'static_text') {
    return (
      <Typography.Paragraph key={node.id}>
        {String(node.props.text ?? node.label ?? '')}
      </Typography.Paragraph>
    );
  }
  if (node.component === 'alert') {
    return (
      <Alert key={node.id} type="info" showIcon message={String(node.props.text ?? node.label ?? '')} />
    );
  }
  if (node.component === 'subform') {
    return (
      <div key={node.id} style={{ width: '100%' }}>
        <Typography.Text type="secondary" style={{ fontSize: 12, paddingInline: 8 }}>
          {node.label ?? '明细表'}
        </Typography.Text>
        <SubformEditor node={node} appId={appId} />
      </div>
    );
  }
  if (node.children.length > 0) {
    return (
      <div key={node.id} style={{ width: '100%' }}>
        {node.label ? (
          <Typography.Text type="secondary" style={{ fontSize: 12, paddingInline: 8 }}>
            {node.label}
          </Typography.Text>
        ) : null}
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          {node.children.map((child) => renderNode(child, appId, tableCode))}
        </div>
      </div>
    );
  }
  if (!node.fieldCode) return null;
  return (
    <div
      key={node.id}
      style={{ width: `${Math.min(100, Math.max(8, (node.span / 24) * 100))}%`, paddingInline: 8 }}
    >
      <Form.Item
        name={node.fieldCode}
        label={node.label ?? node.fieldCode}
        required={node.required}
        style={{ marginBottom: 16 }}
      >
        {control(node, appId, tableCode)}
      </Form.Item>
    </div>
  );
}

export function FormRuntime({
  appId,
  fixedFormCode
}: {
  appId: string;
  fixedFormCode?: string;
}) {
  const { message } = AntApp.useApp();
  const [forms, setForms] = useState<FormSummary[] | null>(null);
  const [formCode, setFormCode] = useState<string | null>(fixedFormCode ?? null);
  const [result, setResult] = useState<FormRenderResult | null>(null);
  const [records, setRecords] = useState<RecordPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [runtimeForm] = Form.useForm();

  useEffect(() => {
    let cancelled = false;
    setForms(null);
    setFormCode(fixedFormCode ?? null);
    setResult(null);
    setRecords(null);
    api
      .forms(appId)
      .then((value) => {
        if (cancelled) return;
        setForms(value);
        setFormCode(fixedFormCode ?? value[0]?.formCode ?? null);
      })
      .catch((cause) => {
        if (cancelled) return;
        setForms([]);
        showError(cause);
      });
    return () => {
      cancelled = true;
    };
  }, [appId, fixedFormCode]);

  useEffect(() => {
    if (!formCode) {
      setResult(null);
      setRecords(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    api
      .renderForm(appId, formCode)
      .then(async (value) => {
        if (cancelled) return;
        setResult(value);
        runtimeForm.resetFields();
        const page = await api.listRecords(appId, value.tableCode);
        if (!cancelled) setRecords(page);
      })
      .catch((cause) => {
        if (!cancelled) showError(cause);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [appId, formCode]);

  function showError(cause: unknown) {
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  const leaves = useMemo(() => (result ? mainFieldNodes(result.tree.nodes) : []), [result]);
  const subLeaves = useMemo(() => (result ? subformNodes(result.tree.nodes) : []), [result]);

  async function refreshRecords() {
    if (!result) return;
    try {
      setRecords(await api.listRecords(appId, result.tableCode));
    } catch (cause) {
      showError(cause);
    }
  }

  async function submit() {
    if (!result) return;
    try {
      const values = (await runtimeForm.validateFields()) as Record<string, unknown>;
      const payload: Record<string, unknown> = {};
      for (const node of leaves) {
        if (!node.fieldCode || isReadonly(node)) continue;
        if (!(node.fieldCode in values)) continue;
        const normalized = normalizeValue(node.component, values[node.fieldCode]);
        payload[node.fieldCode] =
          node.component === 'relation' && Array.isArray(normalized)
            ? JSON.stringify(normalized)
            : normalized;
      }
      const rawSubforms =
        (values.__subforms as Record<string, Record<string, unknown>[]> | undefined) ?? {};
      const subforms: Record<string, Record<string, unknown>[]> = {};
      for (const node of subLeaves) {
        const rows = rawSubforms[node.id] ?? [];
        subforms[node.id] = rows.map((row) => {
          const item: Record<string, unknown> = {};
          for (const child of node.children) {
            if (!child.fieldCode || isReadonly(child)) continue;
            if (!(child.fieldCode in row)) continue;
            const normalized = normalizeValue(child.component, row[child.fieldCode]);
            item[child.fieldCode] =
              child.component === 'relation' && Array.isArray(normalized)
                ? JSON.stringify(normalized)
                : normalized;
          }
          if (row.id !== null && row.id !== undefined) item.id = row.id;
          if (row.version !== null && row.version !== undefined) item.version = row.version;
          return item;
        });
      }
      setSubmitting(true);
      await api.createFormRecord(appId, result.formCode, { values: payload, subforms });
      runtimeForm.resetFields();
      message.success('已提交');
      await refreshRecords();
    } catch (cause) {
      if (cause instanceof ApiError) {
        showError(cause);
      }
    } finally {
      setSubmitting(false);
    }
  }

  const columns: ColumnsType<Record<string, unknown>> = useMemo(() => {
    const fieldColumns = leaves
      .filter((node) => node.fieldCode)
      .map((node) => {
        const code = node.fieldCode as string;
        return {
          title: node.label ?? code,
          dataIndex: code,
          key: code,
          ellipsis: true,
          render: (value: unknown, record: Record<string, unknown>) => {
            const display = record[`${code}_display`];
            if (display !== null && display !== undefined) return String(display);
            if (value === null || value === undefined) return '-';
            return Array.isArray(value) ? value.join('、') : String(value);
          }
        };
      });
    return [
      ...fieldColumns,
      { title: 'ID', dataIndex: 'id', key: 'id', width: 180, ellipsis: true },
      {
        title: '操作',
        key: '__actions',
        width: 80,
        render: (_: unknown, record: Record<string, unknown>) => (
          <Popconfirm
            title="删除记录？子表明细会一并删除"
            onConfirm={() => removeRecord(record)}
          >
            <Button type="link" danger size="small">
              删除
            </Button>
          </Popconfirm>
        )
      }
    ];
  }, [leaves]);

  async function removeRecord(record: Record<string, unknown>) {
    if (!result) return;
    try {
      await api.deleteFormRecord(
        appId,
        result.formCode,
        String(record.id),
        Number(record.version ?? 0)
      );
      message.success('已删除');
      await refreshRecords();
    } catch (cause) {
      showError(cause);
    }
  }

  if (forms === null) {
    return <Card loading />;
  }

  if (forms.length === 0) {
    return (
      <Card>
        <Empty description="还没有已发布的表单，请先在「表单设计」中创建并发布" />
      </Card>
    );
  }

  return (
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
          <Typography.Text strong>{fixedFormCode ? result?.displayName ?? '表单运行' : '表单运行'}</Typography.Text>
          {fixedFormCode ? null : (
            <Select
              style={{ minWidth: 200 }}
              value={formCode ?? undefined}
              options={forms.map((item) => ({ value: item.formCode, label: item.displayName }))}
              onChange={setFormCode}
            />
          )}
          {result ? <Tag style={{ marginInlineEnd: 0 }}>{result.tableCode}</Tag> : null}
        </Space>
        <Button onClick={refreshRecords} disabled={!result} loading={loading}>
          刷新数据
        </Button>
      </div>

      <Card size="small" title={result?.displayName ?? '表单'} loading={loading && !result}>
        {result ? (
          <Form form={runtimeForm} layout="vertical" requiredMark>
            <div style={{ display: 'flex', flexWrap: 'wrap' }}>
              {result.tree.nodes.map((node) => renderNode(node, appId, result.tableCode))}
            </div>
            <Divider style={{ margin: '4px 0 16px' }} />
            <Space>
              <Button type="primary" onClick={submit} loading={submitting}>
                提交
              </Button>
              <Button onClick={() => runtimeForm.resetFields()}>重置</Button>
            </Space>
          </Form>
        ) : (
          <Empty description="选择表单后开始填写" />
        )}
      </Card>

      <Card
        size="small"
        title={`最近记录${records ? ` · ${records.total}` : ''}`}
        extra={
          <Button size="small" type="text" onClick={refreshRecords} disabled={!result}>
            刷新
          </Button>
        }
      >
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          pagination={false}
          scroll={{ x: 720 }}
          columns={columns}
          dataSource={records?.records ?? []}
          locale={{ emptyText: '还没有记录' }}
          expandable={
            subLeaves.length > 0 && result
              ? {
                  expandedRowRender: (record: Record<string, unknown>) => (
                    <RecordDetail
                      appId={appId}
                      formCode={result.formCode}
                      recordId={String(record.id)}
                      nodes={subLeaves}
                    />
                  )
                }
              : undefined
          }
        />
      </Card>
    </Space>
  );
}
