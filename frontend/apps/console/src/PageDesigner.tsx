import { useMemo, useState } from 'react';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tag,
  Typography
} from 'antd';
import { CanvasItemShell, PanelField } from '@daos/designer';
import { EmptyState, tokens } from '@daos/ui';
import type { SchemaApp, SchemaForm, SchemaMenu, SchemaTable, SchemaView } from '@daos/metadata';
import { AppIcon, AppIconPicker } from './appIcons';
import {
  PAGE_BLOCK_LABELS,
  readBlocks,
  writeBlocks,
  type PageBlock,
  type PageBlockType
} from './pageLayout';
import { scaffoldTablePage } from './pageScaffold';

interface AppPage {
  menu: SchemaMenu;
  view: SchemaView | null;
  form: SchemaForm | null;
  table: SchemaTable | null;
}

/** 页面 = 菜单 + 目标视图 + 绑定表单，不新增元数据对象。 */
function toPage(app: SchemaApp, menu: SchemaMenu): AppPage {
  const view =
    menu.menuType === 'VIEW'
      ? (app.views ?? []).find((item) => !item.deleted && item.id === menu.targetId) ?? null
      : null;
  const targetForm =
    menu.menuType === 'FORM'
      ? app.forms.find((item) => !item.deleted && item.id === menu.targetId) ?? null
      : null;
  const tableId =
    view?.tableId ??
    targetForm?.tableId ??
    (menu.menuType === 'TABLE' ? menu.targetId : null);
  const table = tableId ? app.tables.find((item) => item.id === tableId) ?? null : null;
  const boundFormId = typeof menu.ext?.formId === 'string' ? menu.ext.formId : null;
  const boundForm = boundFormId
    ? app.forms.find((item) => !item.deleted && item.id === boundFormId) ?? null
    : null;
  const form =
    targetForm ??
    boundForm ??
    (table ? app.forms.find((item) => !item.deleted && item.tableId === table.id) ?? null : null);
  return { menu, view, form, table };
}

export function PageDesigner({
  app,
  onChange
}: {
  app: SchemaApp;
  onChange: (app: SchemaApp) => void;
  }) {
  const { message, modal } = AntApp.useApp();
  const [selectedMenuId, setSelectedMenuId] = useState<string | null>(null);
  const [selectedBlockId, setSelectedBlockId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm<{ tableId: string }>();

  const pages = useMemo(
    () =>
      (app.menus ?? [])
        .filter((menu) => !menu.deleted && menu.menuType !== 'TABLE')
        .sort((left, right) => left.sortOrder - right.sortOrder)
        .map((menu) => toPage(app, menu)),
    [app]
  );

  const selected = pages.find((page) => page.menu.id === selectedMenuId) ?? null;
  const blocks = selected
    ? readBlocks(selected.menu, {
        viewId: selected.view?.id,
        formId: selected.form?.id
      })
    : [];
  const activeBlock = blocks.find((block) => block.id === selectedBlockId) ?? null;

  function applyBlocks(next: PageBlock[]) {
    if (!selected) return;
    patchMenu(selected.menu.id, (menu) => writeBlocks(menu, next));
  }

  function addBlock(type: PageBlockType) {
    if (!selected) return;
    const block: PageBlock = {
      id: crypto.randomUUID(),
      type,
      title: PAGE_BLOCK_LABELS[type]
    };
    if (type === 'list' || type === 'metrics') block.viewId = selected.view?.id;
    if (type === 'form') block.formId = selected.form?.id;
    if (type === 'text') block.text = '';
    applyBlocks([...blocks, block]);
    setSelectedBlockId(block.id);
  }

  function patchBlock(blockId: string, update: Partial<PageBlock>) {
    applyBlocks(blocks.map((block) => (block.id === blockId ? { ...block, ...update } : block)));
  }

  function moveBlock(index: number, offset: number) {
    const target = index + offset;
    if (target < 0 || target >= blocks.length) return;
    const next = [...blocks];
    const [moved] = next.splice(index, 1);
    if (!moved) return;
    next.splice(target, 0, moved);
    applyBlocks(next);
  }

  function removeBlock(blockId: string) {
    applyBlocks(blocks.filter((block) => block.id !== blockId));
    setSelectedBlockId((current) => (current === blockId ? null : current));
  }

  function patchMenu(menuId: string, update: (menu: SchemaMenu) => SchemaMenu) {
    onChange({
      ...app,
      menus: (app.menus ?? []).map((menu) => (menu.id === menuId ? update(menu) : menu))
    });
  }

  function createPage(values: { tableId: string }) {
    const table = app.tables.find((item) => item.id === values.tableId);
    if (!table) return;
    const { app: next, created } = scaffoldTablePage(app, table);
    const view = (next.views ?? []).find((item) => !item.deleted && item.tableId === table.id);
    const menu = (next.menus ?? []).find(
      (item) => !item.deleted && item.menuType === 'VIEW' && item.targetId === view?.id
    );
    if (!menu) {
      message.warning('该表还没有字段，请先在数据建模中添加字段');
      return;
    }
    if (created.length > 0) {
      onChange(next);
      message.success(`已生成${created.join('、')}`);
    } else {
      message.info('该表已有页面，已为你打开');
    }
    setSelectedMenuId(menu.id);
    setCreateOpen(false);
    createForm.resetFields();
  }

  function deletePage(menu: SchemaMenu) {
    modal.confirm({
      title: `删除页面「${menu.displayName}」？`,
      content: '只删除页面菜单，关联的表单与视图会保留',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        onChange({
          ...app,
          menus: (app.menus ?? []).filter((item) => item.id !== menu.id)
        });
        setSelectedMenuId((current) => (current === menu.id ? null : current));
      }
    });
  }

  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
      <Card
        size="small"
        title="页面"
        extra={
          <Button size="small" type="primary" onClick={() => setCreateOpen(true)}>
            新建页面
          </Button>
        }
        style={{ width: 280, flex: '0 0 280px' }}
        styles={{ body: { padding: pages.length === 0 ? 24 : 8 } }}
      >
        {pages.length === 0 ? (
          <EmptyState compact title="还没有页面" description="从数据表生成一个。" />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size={4}>
            {pages.map((page) => (
              <button
                key={page.menu.id}
                type="button"
                onClick={() => setSelectedMenuId(page.menu.id)}
                style={{
                  width: '100%',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  padding: '8px 10px',
                  borderRadius: tokens.radiusMd,
                  border: 'none',
                  cursor: 'pointer',
                  textAlign: 'left',
                  background:
                    page.menu.id === selectedMenuId ? tokens.colorPrimaryBg : 'transparent'
                }}
              >
                <AppIcon icon={page.menu.icon} name={page.menu.displayName} size={30} />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <Typography.Text strong ellipsis style={{ display: 'block' }}>
                    {page.menu.displayName}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                    {page.table?.displayName ?? '未知数据表'}
                  </Typography.Text>
                </div>
                {page.menu.visible ? null : <Tag>隐藏</Tag>}
              </button>
            ))}
          </Space>
        )}
      </Card>

      <div style={{ flex: 1, minWidth: 0 }}>
        {!selected ? (
          <Card size="small">
            <EmptyState title="选择左侧页面进行配置，或新建页面" />
          </Card>
        ) : (
          <Card
            size="small"
            title={
              <Space>
                <span>{selected.menu.displayName}</span>
                <Tag>{selected.menu.code}</Tag>
              </Space>
            }
            extra={
              <Button size="small" danger onClick={() => deletePage(selected.menu)}>
                删除页面
              </Button>
            }
          >
            <Space direction="vertical" style={{ width: '100%' }} size={14}>
              {!selected.view || !selected.form ? (
                <Alert
                  type="warning"
                  showIcon
                  message="页面目标不完整"
                  description="请先选择该数据表下的视图与表单；没有可用选项时回数据表点击「生成页面」。"
                />
              ) : null}

              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <PanelField label="页面名称" layout="inline">
                  <Input
                    value={selected.menu.displayName}
                    onChange={(event) =>
                      patchMenu(selected.menu.id, (menu) => ({
                        ...menu,
                        displayName: event.target.value
                      }))
                    }
                  />
                </PanelField>

                <PanelField label="页面图标" layout="inline">
                  <AppIconPicker
                    value={selected.menu.icon ?? undefined}
                    onChange={(icon) =>
                      patchMenu(selected.menu.id, (menu) => ({ ...menu, icon }))
                    }
                  />
                </PanelField>

                <PanelField label="数据表" layout="inline">
                  <Space>
                    <Tag>{selected.table?.code ?? '-'}</Tag>
                    <Typography.Text>{selected.table?.displayName ?? '未绑定'}</Typography.Text>
                  </Space>
                </PanelField>

                <PanelField label="绑定视图" layout="inline">
                  <Select
                    style={{ width: '100%' }}
                    value={selected.view?.id}
                    placeholder="选择视图"
                    options={(app.views ?? [])
                      .filter(
                        (view) => !view.deleted && view.tableId === selected.table?.id
                      )
                      .map((view) => ({ value: view.id, label: view.displayName }))}
                    onChange={(viewId) =>
                      patchMenu(selected.menu.id, (menu) => ({
                        ...menu,
                        menuType: 'VIEW',
                        targetId: viewId
                      }))
                    }
                  />
                </PanelField>

                <PanelField label="绑定表单" layout="inline">
                  <Select
                    style={{ width: '100%' }}
                    value={selected.form?.id}
                    placeholder="选择表单"
                    options={app.forms
                      .filter(
                        (form) => !form.deleted && form.tableId === selected.table?.id
                      )
                      .map((form) => ({ value: form.id, label: form.displayName }))}
                    onChange={(formId) =>
                      patchMenu(selected.menu.id, (menu) => ({
                        ...menu,
                        ext: { ...menu.ext, formId }
                      }))
                    }
                  />
                </PanelField>

                <PanelField label="排序" layout="inline">
                  <InputNumber
                    min={0}
                    value={selected.menu.sortOrder}
                    onChange={(value) =>
                      patchMenu(selected.menu.id, (menu) => ({
                        ...menu,
                        sortOrder: value ?? menu.sortOrder
                      }))
                    }
                  />
                </PanelField>

                <PanelField label="运行端可见" layout="inline">
                  <Switch
                    checked={selected.menu.visible}
                    onChange={(visible) =>
                      patchMenu(selected.menu.id, (menu) => ({ ...menu, visible }))
                    }
                  />
                </PanelField>
              </Space>

              <Divider style={{ margin: '4px 0' }} />

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  flexWrap: 'wrap',
                  gap: 8
                }}
              >
                <div>
                  <Typography.Text strong>页面布局</Typography.Text>
                  <Typography.Text
                    type="secondary"
                    style={{ fontSize: 12, marginInlineStart: 8 }}
                  >
                    按顺序组合说明、指标卡、列表与表单
                  </Typography.Text>
                </div>
                <Space size={4} wrap>
                  <Button size="small" onClick={() => addBlock('text')}>
                    ＋ 说明
                  </Button>
                  <Button size="small" onClick={() => addBlock('metrics')}>
                    ＋ 指标卡
                  </Button>
                  <Button size="small" onClick={() => addBlock('list')}>
                    ＋ 数据列表
                  </Button>
                  <Button size="small" onClick={() => addBlock('form')}>
                    ＋ 表单
                  </Button>
                </Space>
              </div>

              {blocks.length === 0 ? (
                <EmptyState compact title="还没有布局" description="添加组件组成页面。" />
              ) : (
                <Space direction="vertical" style={{ width: '100%' }} size={8}>
                  {blocks.map((block, index) => {
                    const active = block.id === selectedBlockId;
                    return (
                      <CanvasItemShell
                        key={block.id}
                        active={active}
                        padding="8px 10px"
                        radius={tokens.radiusMd}
                        onClick={() => setSelectedBlockId(block.id)}
                      >
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
                          <Tag style={{ marginInlineEnd: 0 }}>
                            {PAGE_BLOCK_LABELS[block.type]}
                          </Tag>
                          <Typography.Text ellipsis style={{ flex: 1, minWidth: 0 }}>
                            {block.title || '未命名'}
                          </Typography.Text>
                          <Button
                            size="small"
                            type="text"
                            disabled={index === 0}
                            onClick={(event) => {
                              event.stopPropagation();
                              moveBlock(index, -1);
                            }}
                          >
                            ↑
                          </Button>
                          <Button
                            size="small"
                            type="text"
                            disabled={index === blocks.length - 1}
                            onClick={(event) => {
                              event.stopPropagation();
                              moveBlock(index, 1);
                            }}
                          >
                            ↓
                          </Button>
                          <Button
                            size="small"
                            type="text"
                            danger
                            onClick={(event) => {
                              event.stopPropagation();
                              removeBlock(block.id);
                            }}
                          >
                            删除
                          </Button>
                        </div>

                        {active ? (
                          <Space
                            direction="vertical"
                            size={8}
                            style={{ width: '100%', marginTop: 10 }}
                            onClick={(event) => event.stopPropagation()}
                          >
                            <PanelField label="标题" layout="inline" labelWidth={72}>
                              <Input
                                size="small"
                                value={block.title}
                                placeholder="组件的显示标题"
                                onChange={(event) =>
                                  patchBlock(block.id, { title: event.target.value })
                                }
                              />
                            </PanelField>

                            {block.type === 'text' ? (
                              <PanelField label="内容" layout="inline" labelWidth={72}>
                                <Input.TextArea
                                  rows={3}
                                  value={block.text}
                                  placeholder="页面说明、操作指引等"
                                  onChange={(event) =>
                                    patchBlock(block.id, { text: event.target.value })
                                  }
                                />
                              </PanelField>
                            ) : block.type === 'form' ? (
                              <PanelField label="表单" layout="inline" labelWidth={72}>
                                <Select
                                  size="small"
                                  style={{ width: '100%' }}
                                  value={block.formId}
                                  placeholder="选择表单"
                                  options={app.forms
                                    .filter((form) => !form.deleted)
                                    .map((form) => ({
                                      value: form.id,
                                      label: `${form.displayName} · ${form.code}`
                                    }))}
                                  onChange={(formId) => patchBlock(block.id, { formId })}
                                />
                              </PanelField>
                            ) : (
                              <PanelField label="视图" layout="inline" labelWidth={72}>
                                <Select
                                  size="small"
                                  style={{ width: '100%' }}
                                  value={block.viewId}
                                  placeholder="选择视图"
                                  options={(app.views ?? [])
                                    .filter((view) => !view.deleted)
                                    .map((view) => ({
                                      value: view.id,
                                      label: `${view.displayName} · ${view.code}`
                                    }))}
                                  onChange={(viewId) => patchBlock(block.id, { viewId })}
                                />
                              </PanelField>
                            )}
                          </Space>
                        ) : null}
                      </CanvasItemShell>
                    );
                  })}
                </Space>
              )}

              <Alert
                type="info"
                showIcon
                message="权限说明"
                description="运行端入口需要 app:view 权限且页面处于可见状态；记录的新增、编辑、删除由 table:read / create / update / delete 权限控制。"
              />
            </Space>
          </Card>
        )}
      </div>

      <Modal
        title="新建页面"
        open={createOpen}
        okText="生成页面"
        cancelText="取消"
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" onFinish={createPage}>
          <Form.Item
            name="tableId"
            label="数据表"
            rules={[{ required: true, message: '请选择数据表' }]}
            extra="系统会补齐该表的默认表单、默认列表和默认菜单"
          >
            <Select
              placeholder="选择数据表"
              options={app.tables
                .filter((table) => !table.deleted)
                .map((table) => ({
                  value: table.id,
                  label: `${table.displayName}（${table.code}）`
                }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
