import { useEffect, useMemo, useState } from 'react';
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
  Rate,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  TimePicker,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import { api, type FormRenderResult, type FormSummary, type RecordPage, type RenderNode } from './api';

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

function fieldNodes(nodes: RenderNode[]): RenderNode[] {
  return nodes.flatMap((node) => {
    const own = node.fieldCode ? [node] : [];
    return [...own, ...fieldNodes(node.children)];
  });
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

function control(node: RenderNode) {
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
    default:
      return <Input disabled={disabled} placeholder={placeholder} />;
  }
}

function renderNode(node: RenderNode): ReactNode {
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
  if (node.children.length > 0) {
    return (
      <div key={node.id} style={{ width: '100%' }}>
        {node.label ? (
          <Typography.Text type="secondary" style={{ fontSize: 12, paddingInline: 8 }}>
            {node.label}
          </Typography.Text>
        ) : null}
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>{node.children.map(renderNode)}</div>
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
        {control(node)}
      </Form.Item>
    </div>
  );
}

export function FormRuntime({ appId }: { appId: string }) {
  const { message } = AntApp.useApp();
  const [forms, setForms] = useState<FormSummary[] | null>(null);
  const [formCode, setFormCode] = useState<string | null>(null);
  const [result, setResult] = useState<FormRenderResult | null>(null);
  const [records, setRecords] = useState<RecordPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [runtimeForm] = Form.useForm();

  useEffect(() => {
    let cancelled = false;
    setForms(null);
    setFormCode(null);
    setResult(null);
    setRecords(null);
    api
      .forms(appId)
      .then((value) => {
        if (cancelled) return;
        setForms(value);
        setFormCode(value[0]?.formCode ?? null);
      })
      .catch((cause) => {
        if (cancelled) return;
        setForms([]);
        showError(cause);
      });
    return () => {
      cancelled = true;
    };
  }, [appId]);

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

  const leaves = useMemo(() => (result ? fieldNodes(result.tree.nodes) : []), [result]);

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
        payload[node.fieldCode] = normalizeValue(node.component, values[node.fieldCode]);
      }
      setSubmitting(true);
      await api.createRecord(appId, result.tableCode, payload);
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
      .map((node) => ({
        title: node.label ?? node.fieldCode ?? '',
        dataIndex: node.fieldCode as string,
        key: node.fieldCode as string,
        ellipsis: true,
        render: (value: unknown) => (value === null || value === undefined ? '-' : String(value))
      }));
    return [
      ...fieldColumns,
      { title: 'ID', dataIndex: 'id', key: 'id', width: 180, ellipsis: true }
    ];
  }, [leaves]);

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
          <Typography.Text strong>表单运行</Typography.Text>
          <Select
            style={{ minWidth: 200 }}
            value={formCode ?? undefined}
            options={forms.map((item) => ({ value: item.formCode, label: item.displayName }))}
            onChange={setFormCode}
          />
          {result ? <Tag style={{ marginInlineEnd: 0 }}>{result.tableCode}</Tag> : null}
        </Space>
        <Button onClick={refreshRecords} disabled={!result} loading={loading}>
          刷新数据
        </Button>
      </div>

      <Card size="small" title={result?.displayName ?? '表单'} loading={loading && !result}>
        {result ? (
          <Form form={runtimeForm} layout="vertical" requiredMark>
            <div style={{ display: 'flex', flexWrap: 'wrap' }}>{result.tree.nodes.map(renderNode)}</div>
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
        />
      </Card>
    </Space>
  );
}
