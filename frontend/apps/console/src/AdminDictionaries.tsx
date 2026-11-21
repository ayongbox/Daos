import { useEffect, useRef, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EmptyState, SkeletonCard, tokens, ToolbarRow } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import {
  api,
  type AppSummary,
  type DictionaryItemInput,
  type DictionaryView
} from './api';

interface DictionaryFormValues {
  code: string;
  name: string;
  description?: string;
  enabled: boolean;
}

interface ItemRow extends DictionaryItemInput {
  key: string;
}

type DictSelection = { mode: 'create' } | { mode: 'edit'; code: string } | null;

/** 字典管理：按应用维护数据字典与字典项。 */
export function AdminDictionaries() {
  const { message } = AntApp.useApp();
  const [apps, setApps] = useState<AppSummary[]>([]);
  const [appId, setAppId] = useState<string | null>(null);
  const [dictionaries, setDictionaries] = useState<DictionaryView[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [selection, setSelection] = useState<DictSelection>(null);
  const [items, setItems] = useState<ItemRow[]>([]);
  const [treeFlag, setTreeFlag] = useState(false);
  const [detailForm] = Form.useForm<DictionaryFormValues>();
  const rowKeyRef = useRef(0);

  function showError(cause: unknown) {
    // 401 由全局会话失效事件统一处理并回到登录页，不再叠加提示
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  useEffect(() => {
    api.listApps().then(setApps).catch(showError);
  }, []);

  function openDictionary(dict: DictionaryView) {
    setSelection({ mode: 'edit', code: dict.code });
    setTreeFlag(dict.tree);
    detailForm.setFieldsValue({
      code: dict.code,
      name: dict.name,
      description: dict.description ?? undefined,
      enabled: dict.enabled
    });
    setItems(
      dict.items.map((item) => ({
        key: `item-${item.id}`,
        id: item.id,
        parentId: item.parentId,
        code: item.code,
        label: item.label,
        value: item.value,
        color: item.color,
        icon: item.icon,
        sortOrder: item.sortOrder,
        enabled: item.enabled,
        defaultSelected: item.defaultSelected,
        description: item.description
      }))
    );
  }

  function openCreate() {
    setSelection({ mode: 'create' });
    setTreeFlag(false);
    detailForm.resetFields();
    detailForm.setFieldsValue({ enabled: true });
    setItems([]);
  }

  function clearEditor() {
    setSelection(null);
    detailForm.resetFields();
    setItems([]);
  }

  /** 拉取当前应用的字典列表，并尽量保持当前选中。 */
  function refreshDictionaries(keepCode?: string) {
    if (!appId) return Promise.resolve();
    setLoading(true);
    return api
      .listDictionaries(appId)
      .then((list) => {
        setDictionaries(list);
        const wanted = keepCode ?? (selection?.mode === 'edit' ? selection.code : undefined);
        const matched = wanted ? list.find((dict) => dict.code === wanted) : undefined;
        if (matched) {
          openDictionary(matched);
        } else {
          clearEditor();
        }
      })
      .catch(showError)
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    if (!appId) {
      setDictionaries([]);
      clearEditor();
      return;
    }
    refreshDictionaries();
  }, [appId]);

  function patchItem(key: string, patch: Partial<ItemRow>) {
    setItems((previous) =>
      previous.map((row) => (row.key === key ? { ...row, ...patch } : row))
    );
  }

  function addItem() {
    rowKeyRef.current += 1;
    setItems((previous) => [
      ...previous,
      {
        key: `new-${rowKeyRef.current}`,
        code: '',
        label: '',
        value: null,
        sortOrder: previous.length,
        enabled: true,
        defaultSelected: false
      }
    ]);
  }

  function removeItem(key: string) {
    setItems((previous) => previous.filter((row) => row.key !== key));
  }

  async function saveDictionary() {
    if (!appId || !selection) return;
    let values: DictionaryFormValues;
    try {
      values = await detailForm.validateFields();
    } catch {
      return;
    }
    for (const row of items) {
      if (!row.code.trim() || !row.label.trim()) {
        message.error('请补全字典项的编码与名称');
        return;
      }
    }
    setSaving(true);
    try {
      const code = selection.mode === 'edit' ? selection.code : values.code.trim();
      await api.saveDictionary(appId, code, {
        name: values.name.trim(),
        description: values.description?.trim(),
        tree: treeFlag,
        enabled: values.enabled,
        items: items.map(({ key: _key, ...item }) => item)
      });
      message.success('字典已保存');
      await refreshDictionaries(code);
    } catch (cause) {
      showError(cause);
    } finally {
      setSaving(false);
    }
  }

  async function removeDictionary(code: string) {
    if (!appId) return;
    try {
      await api.deleteDictionary(appId, code);
      message.success('字典已删除');
      await refreshDictionaries();
    } catch (cause) {
      showError(cause);
    }
  }

  const itemColumns: ColumnsType<ItemRow> = [
    {
      title: '编码',
      width: 160,
      render: (_, row) => (
        <Input
          size="small"
          value={row.code}
          placeholder="pending"
          onChange={(event) => patchItem(row.key, { code: event.target.value })}
        />
      )
    },
    {
      title: '名称',
      width: 160,
      render: (_, row) => (
        <Input
          size="small"
          value={row.label}
          placeholder="待处理"
          onChange={(event) => patchItem(row.key, { label: event.target.value })}
        />
      )
    },
    {
      title: '值',
      width: 140,
      render: (_, row) => (
        <Input
          size="small"
          value={row.value ?? ''}
          placeholder="可选"
          onChange={(event) => patchItem(row.key, { value: event.target.value })}
        />
      )
    },
    {
      title: '排序',
      width: 90,
      render: (_, row) => (
        <InputNumber
          size="small"
          style={{ width: '100%' }}
          value={row.sortOrder}
          min={0}
          onChange={(value) => patchItem(row.key, { sortOrder: value ?? 0 })}
        />
      )
    },
    {
      title: '启用',
      width: 70,
      render: (_, row) => (
        <Switch
          size="small"
          checked={row.enabled}
          onChange={(checked) => patchItem(row.key, { enabled: checked })}
        />
      )
    },
    {
      title: '操作',
      width: 70,
      render: (_, row) => (
        <Button
          className="daos-row-actions"
          size="small"
          type="link"
          danger
          onClick={() => removeItem(row.key)}
        >
          删除
        </Button>
      )
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Space size={8}>
        <Typography.Text type="secondary">应用</Typography.Text>
        <Select
          showSearch
          optionFilterProp="label"
          style={{ width: 240 }}
          placeholder="选择应用"
          value={appId ?? undefined}
          options={apps.map((app) => ({ value: app.id, label: app.displayName }))}
          onChange={(value) => setAppId(value)}
        />
      </Space>

      {!appId ? (
        <Card>
          <EmptyState title="请先选择应用" />
        </Card>
      ) : (
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', minWidth: 0 }}>
          <Card
            size="small"
            style={{ width: 240, flex: '0 0 240px' }}
            title="字典"
            extra={
              <Button size="small" type="text" onClick={openCreate}>
                新建
              </Button>
            }
            styles={{ body: { padding: dictionaries.length === 0 ? 24 : '4px 8px' } }}
          >
            {loading ? (
              <SkeletonCard rows={3} />
            ) : dictionaries.length === 0 ? (
              <EmptyState compact title="暂无字典" />
            ) : (
                <Space direction="vertical" size={0} style={{ width: '100%' }}>
                  {dictionaries.map((dict) => (
                    <div
                      key={dict.id}
                      onClick={() => openDictionary(dict)}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                        padding: '8px 10px',
                        borderRadius: tokens.radiusSm,
                        cursor: 'pointer',
                        background:
                          selection?.mode === 'edit' && selection.code === dict.code
                            ? tokens.colorPrimaryBg
                            : undefined,
                        color:
                          selection?.mode === 'edit' && selection.code === dict.code
                            ? tokens.colorPrimary
                            : tokens.colorTextPrimary
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <Typography.Text
                          strong
                          ellipsis
                          style={{ display: 'block', color: 'inherit' }}
                        >
                          {dict.name}
                        </Typography.Text>
                        <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                          {dict.code}
                        </Typography.Text>
                      </div>
                      {!dict.enabled ? <Tag>停用</Tag> : null}
                      <Popconfirm
                        title="删除这个字典？"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                        onConfirm={() => removeDictionary(dict.code)}
                      >
                        <Button
                          className="daos-row-actions"
                          type="text"
                          size="small"
                          danger
                          onClick={(event) => event.stopPropagation()}
                        >
                          删除
                        </Button>
                      </Popconfirm>
                    </div>
                  ))}
                </Space>
              )}
          </Card>

          <Card
            size="small"
            style={{ flex: 1, minWidth: 0 }}
            title={selection ? (selection.mode === 'create' ? '新建字典' : '字典详情') : '字典详情'}
            extra={
              selection ? (
                <Button type="primary" loading={saving} onClick={saveDictionary}>
                  保存
                </Button>
              ) : null
            }
          >
            {!selection ? (
              <EmptyState title="选择左侧字典或新建字典" />
            ) : (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Form form={detailForm} layout="vertical">
                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                      gap: 12
                    }}
                  >
                    <Form.Item
                      name="code"
                      label="编码"
                      rules={[{ required: true, message: '请输入字典编码' }]}
                    >
                      <Input
                        placeholder="order_status"
                        disabled={selection.mode === 'edit'}
                      />
                    </Form.Item>
                    <Form.Item
                      name="name"
                      label="名称"
                      rules={[{ required: true, message: '请输入字典名称' }]}
                    >
                      <Input placeholder="订单状态" maxLength={40} />
                    </Form.Item>
                  </div>
                  <Form.Item name="description" label="描述">
                    <Input.TextArea rows={2} maxLength={200} placeholder="这个字典的用途" />
                  </Form.Item>
                  <Form.Item name="enabled" label="启用" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Form>

                <div>
                  <ToolbarRow
                    style={{ marginBottom: 8 }}
                    info={<Typography.Text strong>字典项</Typography.Text>}
                    actions={
                      <Button size="small" onClick={addItem}>
                        添加字典项
                      </Button>
                    }
                  />
                  <Table
                    rowKey="key"
                    size="small"
                    pagination={false}
                    columns={itemColumns}
                    dataSource={items}
                    locale={{
                      emptyText: <EmptyState compact title="暂无字典项" />
                    }}
                  />
                </div>
              </Space>
            )}
          </Card>
        </div>
      )}
    </Space>
  );
}
