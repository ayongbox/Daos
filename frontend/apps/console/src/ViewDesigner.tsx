import { useEffect, useMemo, useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Empty,
  Input,
  InputNumber,
  List,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tokens } from '@daos/ui';
import type {
  AggregateFunction,
  ConditionOperator,
  MenuType,
  SchemaApp,
  SchemaMenu,
  SchemaTable,
  SchemaView,
  SortDirection,
  ViewAggregation,
  ViewColumn
} from '@daos/metadata';

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,31}$/;

const AGGREGATE_FUNCTIONS: Array<{ value: AggregateFunction; label: string }> = [
  { value: 'count', label: '计数' },
  { value: 'count_distinct', label: '去重计数' },
  { value: 'sum', label: '求和' },
  { value: 'avg', label: '平均值' },
  { value: 'min', label: '最小值' },
  { value: 'max', label: '最大值' }
];

const FILTER_OPERATORS: Array<{ value: ConditionOperator; label: string }> = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'contains', label: '包含' },
  { value: 'not_contains', label: '不包含' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '大于等于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '小于等于' },
  { value: 'empty', label: '为空' },
  { value: 'not_empty', label: '不为空' },
  { value: 'in', label: '属于' },
  { value: 'not_in', label: '不属于' }
];

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

function visibleFields(table: SchemaTable) {
  return table.fields.filter(
    (field) => !field.deleted && field.logicalType !== 'formula' && field.logicalType !== 'rollup'
  );
}

function defaultColumns(table: SchemaTable): ViewColumn[] {
  return table.fields
    .filter((field) => !field.deleted)
    .map((field, index) => ({
      fieldId: field.id,
      visible: !field.ui.hiddenInList,
      width: null,
      sortOrder: index
    }));
}

function uniqueCode(base: string, existing: string[]): string {
  let code = base.slice(0, 28);
  let index = 2;
  while (existing.includes(code)) {
    code = `${base.slice(0, 26)}_${index}`.slice(0, 32);
    index += 1;
  }
  return code;
}

function createView(app: SchemaApp, table: SchemaTable): SchemaView {
  const timestamp = now();
  return {
    id: newId(),
    appId: app.id,
    tableId: table.id,
    code: uniqueCode(`view_${table.code}`, (app.views ?? []).map((view) => view.code)),
    displayName: `${table.displayName}列表`,
    description: null,
    viewType: 'GRID',
    columns: defaultColumns(table),
    filters: [],
    sorts: [],
    groupBy: [],
    aggregations: [],
    pageSize: 20,
    status: 'DRAFT',
    ext: {},
    createdBy: 'console',
    createdAt: timestamp,
    updatedBy: 'console',
    updatedAt: timestamp,
    deleted: false,
    schemaVersion: 1
  };
}

function menuTargets(app: SchemaApp, type: MenuType) {
  if (type === 'VIEW') return (app.views ?? []).map((view) => ({ value: view.id, label: view.displayName }));
  if (type === 'FORM') return (app.forms ?? []).map((form) => ({ value: form.id, label: form.displayName }));
  return app.tables.map((table) => ({ value: table.id, label: table.displayName }));
}

function createMenu(app: SchemaApp, type: MenuType): SchemaMenu {
  const timestamp = now();
  const target = menuTargets(app, type)[0];
  return {
    id: newId(),
    appId: app.id,
    code: uniqueCode(
      `menu_${(app.menus ?? []).length + 1}`,
      (app.menus ?? []).map((menu) => menu.code)
    ),
    displayName: target?.label ?? '新菜单',
    icon: null,
    menuType: type,
    targetId: target?.value ?? '',
    sortOrder: (app.menus ?? []).length,
    visible: true,
    status: 'DRAFT',
    ext: {},
    createdBy: 'console',
    createdAt: timestamp,
    updatedBy: 'console',
    updatedAt: timestamp,
    deleted: false,
    schemaVersion: 1
  };
}

function parseFilterValue(operator: ConditionOperator, text: string): unknown {
  if (operator === 'empty' || operator === 'not_empty') return null;
  if (operator === 'in' || operator === 'not_in') {
    return text
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => (Number.isNaN(Number(item)) ? item : Number(item)));
  }
  if (text.trim() === '') return null;
  return Number.isNaN(Number(text)) ? text : Number(text);
}

function filterText(value: unknown): string {
  if (value === null || value === undefined) return '';
  return Array.isArray(value) ? value.join(',') : String(value);
}

export function ViewDesigner({
  app,
  onChange
}: {
  app: SchemaApp;
  onChange: (next: SchemaApp) => void;
}) {
  const { message, modal } = AntApp.useApp();
  const [tab, setTab] = useState<'views' | 'menus'>('views');
  const views = app.views ?? [];
  const menus = app.menus ?? [];
  const [selectedViewId, setSelectedViewId] = useState<string | null>(views[0]?.id ?? null);
  const [selectedMenuId, setSelectedMenuId] = useState<string | null>(menus[0]?.id ?? null);

  useEffect(() => {
    if (selectedViewId && views.some((view) => view.id === selectedViewId)) return;
    setSelectedViewId(views[0]?.id ?? null);
  }, [selectedViewId, views]);

  useEffect(() => {
    if (selectedMenuId && menus.some((menu) => menu.id === selectedMenuId)) return;
    setSelectedMenuId(menus[0]?.id ?? null);
  }, [selectedMenuId, menus]);

  const selectedView = views.find((view) => view.id === selectedViewId) ?? null;
  const selectedMenu = menus.find((menu) => menu.id === selectedMenuId) ?? null;
  const viewTable = app.tables.find((table) => table.id === selectedView?.tableId) ?? null;

  function patchView(viewId: string, update: (view: SchemaView) => SchemaView) {
    onChange({
      ...app,
      views: views.map((view) => (view.id === viewId ? update(view) : view))
    });
  }

  function addView() {
    const table = app.tables[0];
    if (!table) {
      message.warning('请先在数据建模中创建数据表');
      return;
    }
    const view = createView(app, table);
    onChange({ ...app, views: [...views, view] });
    setSelectedViewId(view.id);
    setTab('views');
  }

  function deleteView(view: SchemaView) {
    const boundMenus = menus.filter(
      (menu) => menu.menuType === 'VIEW' && menu.targetId === view.id
    );
    modal.confirm({
      title: `删除视图「${view.displayName}」？`,
      content: boundMenus.length
        ? `同时会删除 ${boundMenus.length} 个指向该视图的菜单`
        : '删除后保存草稿生效',
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        onChange({
          ...app,
          views: views.filter((item) => item.id !== view.id),
          menus: menus.filter(
            (menu) => !(menu.menuType === 'VIEW' && menu.targetId === view.id)
          )
        });
      }
    });
  }

  function patchMenu(menuId: string, update: (menu: SchemaMenu) => SchemaMenu) {
    onChange({
      ...app,
      menus: menus.map((menu) => (menu.id === menuId ? update(menu) : menu))
    });
  }

  function addMenu() {
    const type: MenuType = views.length > 0 ? 'VIEW' : 'FORM';
    if (menuTargets(app, type).length === 0) {
      message.warning('请先创建视图或表单，再添加菜单');
      return;
    }
    const menu = createMenu(app, type);
    onChange({ ...app, menus: [...menus, menu] });
    setSelectedMenuId(menu.id);
    setTab('menus');
  }

  function deleteMenu(menu: SchemaMenu) {
    modal.confirm({
      title: `删除菜单「${menu.displayName}」？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => onChange({ ...app, menus: menus.filter((item) => item.id !== menu.id) })
    });
  }

  const columnRows = useMemo(
    () =>
      selectedView && viewTable
        ? [...selectedView.columns].sort((a, b) => a.sortOrder - b.sortOrder)
        : [],
    [selectedView, viewTable]
  );

  function updateColumn(fieldId: string, update: (column: ViewColumn) => ViewColumn) {
    if (!selectedView) return;
    patchView(selectedView.id, (view) => ({
      ...view,
      columns: view.columns.map((column) =>
        column.fieldId === fieldId ? update(column) : column
      )
    }));
  }

  function moveColumn(fieldId: string, delta: number) {
    if (!selectedView) return;
    const ordered = [...columnRows];
    const index = ordered.findIndex((column) => column.fieldId === fieldId);
    const target = index + delta;
    if (index < 0 || target < 0 || target >= ordered.length) return;
    const current = ordered[index];
    const swap = ordered[target];
    if (!current || !swap) return;
    patchView(selectedView.id, (view) => ({
      ...view,
      columns: view.columns.map((column) => {
        if (column.fieldId === current.fieldId) return { ...column, sortOrder: swap.sortOrder };
        if (column.fieldId === swap.fieldId) return { ...column, sortOrder: current.sortOrder };
        return column;
      })
    }));
  }

  function syncColumns() {
    if (!selectedView || !viewTable) return;
    const existing = new Map(selectedView.columns.map((column) => [column.fieldId, column]));
    patchView(selectedView.id, (view) => ({
      ...view,
      columns: defaultColumns(viewTable).map((column) => existing.get(column.fieldId) ?? column)
    }));
  }

  const columnConfig: ColumnsType<ViewColumn> = [
    {
      title: '显示',
      width: 60,
      render: (_, column) => (
        <Switch
          size="small"
          checked={column.visible}
          onChange={(visible) => updateColumn(column.fieldId, (item) => ({ ...item, visible }))}
        />
      )
    },
    {
      title: '字段',
      render: (_, column) => {
        const field = viewTable?.fields.find((item) => item.id === column.fieldId);
        return (
          <Space size={6}>
            <Typography.Text>{field?.displayName ?? column.fieldId}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {field?.code}
            </Typography.Text>
          </Space>
        );
      }
    },
    {
      title: '宽度',
      width: 100,
      render: (_, column) => (
        <InputNumber
          size="small"
          min={40}
          max={1000}
          placeholder="自动"
          value={column.width ?? undefined}
          onChange={(value) => updateColumn(column.fieldId, (item) => ({ ...item, width: value ?? null }))}
        />
      )
    },
    {
      title: '顺序',
      width: 90,
      render: (_, column) => (
        <Space size={2}>
          <Button size="small" type="text" onClick={() => moveColumn(column.fieldId, -1)}>
            ↑
          </Button>
          <Button size="small" type="text" onClick={() => moveColumn(column.fieldId, 1)}>
            ↓
          </Button>
        </Space>
      )
    }
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card size="small">
        <Space size={8} wrap>
          <Typography.Text strong>视图与报表</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            视图定义列表列、筛选与排序；菜单定义应用入口。两者都随「发布」进入生效快照。
          </Typography.Text>
        </Space>
      </Card>

      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as 'views' | 'menus')}
        items={[
          {
            key: 'views',
            label: `视图 (${views.length})`,
            children: (
              <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <Card
                  size="small"
                  style={{ width: 240, flex: '0 0 240px' }}
                  title="视图"
                  extra={
                    <Button size="small" type="text" onClick={addView}>
                      新建
                    </Button>
                  }
                >
                  <List
                    size="small"
                    dataSource={views}
                    locale={{ emptyText: '暂无视图' }}
                    renderItem={(view) => (
                      <List.Item
                        style={{
                          cursor: 'pointer',
                          borderRadius: tokens.radiusSm,
                          paddingInline: 12,
                          background: view.id === selectedViewId ? tokens.colorPrimaryBg : undefined
                        }}
                        onClick={() => setSelectedViewId(view.id)}
                      >
                        <List.Item.Meta
                          title={view.displayName}
                          description={app.tables.find((table) => table.id === view.tableId)?.displayName}
                        />
                      </List.Item>
                    )}
                  />
                </Card>

                {selectedView && viewTable ? (
                  <Card
                    size="small"
                    style={{ flex: 1, minWidth: 420 }}
                    title={
                      <Space size={8}>
                        <Input
                          value={selectedView.displayName}
                          style={{ width: 200 }}
                          onChange={(event) =>
                            patchView(selectedView.id, (view) => ({
                              ...view,
                              displayName: event.target.value,
                              updatedAt: now()
                            }))
                          }
                        />
                        <Tag>{selectedView.code}</Tag>
                      </Space>
                    }
                    extra={
                      <Button size="small" danger type="text" onClick={() => deleteView(selectedView)}>
                        删除视图
                      </Button>
                    }
                  >
                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                      <Space size={12} wrap>
                        <span>
                          数据表：
                          <Select
                            style={{ width: 200 }}
                            value={selectedView.tableId}
                            options={app.tables.map((table) => ({ value: table.id, label: table.displayName }))}
                            onChange={(tableId) => {
                              const table = app.tables.find((item) => item.id === tableId);
                              if (!table) return;
                              patchView(selectedView.id, (view) => ({
                                ...view,
                                tableId,
                                columns: defaultColumns(table),
                                filters: [],
                                sorts: []
                              }));
                            }}
                          />
                        </span>
                        <span>
                          视图编码：
                          <Input
                            style={{ width: 180 }}
                            value={selectedView.code}
                            status={CODE_PATTERN.test(selectedView.code) ? undefined : 'error'}
                            onChange={(event) =>
                              patchView(selectedView.id, (view) => ({
                                ...view,
                                code: event.target.value,
                                updatedAt: now()
                              }))
                            }
                          />
                        </span>
                        <span>
                          每页：
                          <InputNumber
                            min={1}
                            max={200}
                            value={selectedView.pageSize}
                            onChange={(value) =>
                              patchView(selectedView.id, (view) => ({
                                ...view,
                                pageSize: value ?? 20
                              }))
                            }
                          />
                        </span>
                      </Space>

                      <div>
                        <Space style={{ marginBottom: 8 }}>
                          <Typography.Text strong>列配置</Typography.Text>
                          <Button size="small" type="link" onClick={syncColumns}>
                            同步表字段
                          </Button>
                        </Space>
                        <Table
                          rowKey="fieldId"
                          size="small"
                          pagination={false}
                          columns={columnConfig}
                          dataSource={columnRows}
                          locale={{ emptyText: '还没有列配置，运行时默认显示全部字段' }}
                        />
                      </div>

                      <div>
                        <Space style={{ marginBottom: 8 }}>
                          <Typography.Text strong>默认筛选</Typography.Text>
                          <Button
                            size="small"
                            type="link"
                            onClick={() =>
                              patchView(selectedView.id, (view) => ({
                                ...view,
                                filters: [
                                  ...view.filters,
                                  { fieldId: visibleFields(viewTable)[0]?.id ?? '', operator: 'eq', value: null }
                                ]
                              }))
                            }
                          >
                            添加条件
                          </Button>
                        </Space>
                        {selectedView.filters.length === 0 ? (
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            没有默认筛选，运行时返回全部有权限的记录
                          </Typography.Text>
                        ) : (
                          <Space direction="vertical" style={{ width: '100%' }} size={6}>
                            {selectedView.filters.map((filter, index) => (
                              <Space key={`${filter.fieldId}-${index}`} size={6} wrap>
                                <Select
                                  style={{ width: 150 }}
                                  value={filter.fieldId || undefined}
                                  options={visibleFields(viewTable).map((field) => ({
                                    value: field.id,
                                    label: field.displayName
                                  }))}
                                  onChange={(fieldId) =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      filters: view.filters.map((item, itemIndex) =>
                                        itemIndex === index ? { ...item, fieldId } : item
                                      )
                                    }))
                                  }
                                />
                                <Select
                                  style={{ width: 110 }}
                                  value={filter.operator}
                                  options={FILTER_OPERATORS}
                                  onChange={(operator) =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      filters: view.filters.map((item, itemIndex) =>
                                        itemIndex === index ? { ...item, operator } : item
                                      )
                                    }))
                                  }
                                />
                                <Input
                                  style={{ width: 180 }}
                                  disabled={filter.operator === 'empty' || filter.operator === 'not_empty'}
                                  placeholder="多个值用逗号分隔"
                                  value={filterText(filter.value)}
                                  onChange={(event) =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      filters: view.filters.map((item, itemIndex) =>
                                        itemIndex === index
                                          ? { ...item, value: parseFilterValue(item.operator, event.target.value) }
                                          : item
                                      )
                                    }))
                                  }
                                />
                                <Button
                                  size="small"
                                  type="text"
                                  danger
                                  onClick={() =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      filters: view.filters.filter((_, itemIndex) => itemIndex !== index)
                                    }))
                                  }
                                >
                                  删除
                                </Button>
                              </Space>
                            ))}
                          </Space>
                        )}
                      </div>

                      <div>
                        <Space style={{ marginBottom: 8 }}>
                          <Typography.Text strong>默认排序</Typography.Text>
                          <Button
                            size="small"
                            type="link"
                            onClick={() =>
                              patchView(selectedView.id, (view) => ({
                                ...view,
                                sorts: [
                                  ...view.sorts,
                                  { fieldId: visibleFields(viewTable)[0]?.id ?? '', direction: 'DESC' }
                                ]
                              }))
                            }
                          >
                            添加排序
                          </Button>
                        </Space>
                        {selectedView.sorts.length === 0 ? (
                          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                            未配置默认排序
                          </Typography.Text>
                        ) : (
                          <Space direction="vertical" style={{ width: '100%' }} size={6}>
                            {selectedView.sorts.map((sort, index) => (
                              <Space key={`${sort.fieldId}-${index}`} size={6}>
                                <Select
                                  style={{ width: 150 }}
                                  value={sort.fieldId || undefined}
                                  options={(selectedView.aggregations.length > 0
                                    ? visibleFields(viewTable).filter((field) =>
                                        selectedView.groupBy.includes(field.id)
                                      )
                                    : visibleFields(viewTable)
                                  ).map((field) => ({
                                    value: field.id,
                                    label: field.displayName
                                  }))}
                                  onChange={(fieldId) =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      sorts: view.sorts.map((item, itemIndex) =>
                                        itemIndex === index ? { ...item, fieldId } : item
                                      )
                                    }))
                                  }
                                />
                                <Select
                                  style={{ width: 90 }}
                                  value={sort.direction}
                                  options={[
                                    { value: 'ASC', label: '升序' },
                                    { value: 'DESC', label: '降序' }
                                  ]}
                                  onChange={(direction: SortDirection) =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      sorts: view.sorts.map((item, itemIndex) =>
                                        itemIndex === index ? { ...item, direction } : item
                                      )
                                    }))
                                  }
                                />
                                <Button
                                  size="small"
                                  type="text"
                                  danger
                                  onClick={() =>
                                    patchView(selectedView.id, (view) => ({
                                      ...view,
                                      sorts: view.sorts.filter((_, itemIndex) => itemIndex !== index)
                                    }))
                                  }
                                >
                                  删除
                                </Button>
                              </Space>
                            ))}
                          </Space>
                        )}
                      </div>

                      <div>
                        <Space style={{ marginBottom: 8 }}>
                          <Typography.Text strong>分组与汇总</Typography.Text>
                          <Button
                            size="small"
                            type="link"
                            onClick={() =>
                              patchView(selectedView.id, (view) => ({
                                ...view,
                                aggregations: [
                                  ...view.aggregations,
                                  {
                                    function: 'count',
                                    fieldId: null,
                                    displayName: `记录数${view.aggregations.length + 1}`
                                  }
                                ]
                              }))
                            }
                          >
                            添加汇总列
                          </Button>
                        </Space>
                        <Space direction="vertical" style={{ width: '100%' }} size={8}>
                          <span>
                            分组字段：
                            <Select
                              mode="multiple"
                              style={{ minWidth: 280 }}
                              placeholder="不分组时对所有记录汇总"
                              value={selectedView.groupBy}
                              options={visibleFields(viewTable).map((field) => ({
                                value: field.id,
                                label: field.displayName
                              }))}
                              onChange={(groupBy: string[]) =>
                                patchView(selectedView.id, (view) => ({
                                  ...view,
                                  groupBy,
                                  sorts: view.sorts.filter((sort) => groupBy.includes(sort.fieldId))
                                }))
                              }
                            />
                          </span>

                          {selectedView.aggregations.length === 0 ? (
                            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                              未配置汇总列，视图按普通记录列表运行
                            </Typography.Text>
                          ) : (
                            <Space direction="vertical" style={{ width: '100%' }} size={6}>
                              {selectedView.aggregations.map((aggregate, index) => (
                                <Space key={`${aggregate.displayName}-${index}`} size={6} wrap>
                                  <Select
                                    style={{ width: 110 }}
                                    value={aggregate.function}
                                    options={AGGREGATE_FUNCTIONS}
                                    onChange={(func: AggregateFunction) =>
                                      patchView(selectedView.id, (view) => ({
                                        ...view,
                                        aggregations: view.aggregations.map((item, itemIndex) => {
                                          if (itemIndex !== index) return item;
                                          if (func !== 'count' && !item.fieldId) {
                                            const numericField = visibleFields(viewTable).find((field) =>
                                              ['number', 'decimal', 'rating', 'progress'].includes(
                                                field.logicalType
                                              )
                                            );
                                            return {
                                              ...item,
                                              function: func,
                                              fieldId: numericField?.id ?? visibleFields(viewTable)[0]?.id ?? null
                                            };
                                          }
                                          return { ...item, function: func };
                                        })
                                      }))
                                    }
                                  />
                                  <Select
                                    style={{ width: 160 }}
                                    value={aggregate.fieldId ?? '__all__'}
                                    options={[
                                      { value: '__all__', label: '全部记录' },
                                      ...visibleFields(viewTable).map((field) => ({
                                        value: field.id,
                                        label: field.displayName
                                      }))
                                    ]}
                                    onChange={(fieldId: string) =>
                                      patchView(selectedView.id, (view) => ({
                                        ...view,
                                        aggregations: view.aggregations.map((item, itemIndex) =>
                                          itemIndex === index
                                            ? {
                                                ...item,
                                                fieldId: fieldId === '__all__' ? null : fieldId
                                              }
                                            : item
                                        )
                                      }))
                                    }
                                  />
                                  <Input
                                    style={{ width: 160 }}
                                    placeholder="汇总列名称"
                                    value={aggregate.displayName}
                                    onChange={(event) =>
                                      patchView(selectedView.id, (view) => ({
                                        ...view,
                                        aggregations: view.aggregations.map((item, itemIndex) =>
                                          itemIndex === index
                                            ? { ...item, displayName: event.target.value }
                                            : item
                                        )
                                      }))
                                    }
                                  />
                                  <Button
                                    size="small"
                                    type="text"
                                    danger
                                    onClick={() =>
                                      patchView(selectedView.id, (view) => ({
                                        ...view,
                                        aggregations: view.aggregations.filter(
                                          (_, itemIndex) => itemIndex !== index
                                        )
                                      }))
                                    }
                                  >
                                    删除
                                  </Button>
                                </Space>
                              ))}
                              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                                汇总视图运行端只展示分组字段与汇总列；排序需使用分组字段。
                              </Typography.Text>
                            </Space>
                          )}
                        </Space>
                      </div>
                    </Space>
                  </Card>
                ) : (
                  <Card size="small" style={{ flex: 1, minWidth: 420 }}>
                    <Empty description="创建或选择一个视图" />
                  </Card>
                )}
              </div>
            )
          },
          {
            key: 'menus',
            label: `菜单 (${menus.length})`,
            children: (
              <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <Card
                  size="small"
                  style={{ width: 240, flex: '0 0 240px' }}
                  title="菜单"
                  extra={
                    <Button size="small" type="text" onClick={addMenu}>
                      新建
                    </Button>
                  }
                >
                  <List
                    size="small"
                    dataSource={[...menus].sort((a, b) => a.sortOrder - b.sortOrder)}
                    locale={{ emptyText: '暂无菜单' }}
                    renderItem={(menu) => (
                      <List.Item
                        style={{
                          cursor: 'pointer',
                          borderRadius: tokens.radiusSm,
                          paddingInline: 12,
                          background: menu.id === selectedMenuId ? tokens.colorPrimaryBg : undefined
                        }}
                        onClick={() => setSelectedMenuId(menu.id)}
                      >
                        <List.Item.Meta
                          title={
                            <Space size={6}>
                              <span>{menu.displayName}</span>
                              {menu.visible ? null : <Tag>隐藏</Tag>}
                            </Space>
                          }
                          description={menu.menuType === 'VIEW' ? '视图' : '表单'}
                        />
                      </List.Item>
                    )}
                  />
                </Card>

                {selectedMenu ? (
                  <Card
                    size="small"
                    style={{ flex: 1, minWidth: 420 }}
                    title="菜单配置"
                    extra={
                      <Button size="small" danger type="text" onClick={() => deleteMenu(selectedMenu)}>
                        删除菜单
                      </Button>
                    }
                  >
                    <Space direction="vertical" style={{ width: '100%' }} size={12}>
                      <Space size={12} wrap>
                        <span>
                          名称：
                          <Input
                            style={{ width: 180 }}
                            value={selectedMenu.displayName}
                            onChange={(event) =>
                              patchMenu(selectedMenu.id, (menu) => ({
                                ...menu,
                                displayName: event.target.value,
                                updatedAt: now()
                              }))
                            }
                          />
                        </span>
                        <span>
                          编码：
                          <Input
                            style={{ width: 160 }}
                            value={selectedMenu.code}
                            status={CODE_PATTERN.test(selectedMenu.code) ? undefined : 'error'}
                            onChange={(event) =>
                              patchMenu(selectedMenu.id, (menu) => ({
                                ...menu,
                                code: event.target.value,
                                updatedAt: now()
                              }))
                            }
                          />
                        </span>
                        <span>
                          排序：
                          <InputNumber
                            min={0}
                            max={999}
                            value={selectedMenu.sortOrder}
                            onChange={(value) =>
                              patchMenu(selectedMenu.id, (menu) => ({
                                ...menu,
                                sortOrder: value ?? 0
                              }))
                            }
                          />
                        </span>
                        <span>
                          显示：
                          <Switch
                            checked={selectedMenu.visible}
                            onChange={(visible) =>
                              patchMenu(selectedMenu.id, (menu) => ({ ...menu, visible }))
                            }
                          />
                        </span>
                      </Space>

                      <Space size={12} wrap>
                        <span>
                          类型：
                          <Select
                            style={{ width: 110 }}
                            value={selectedMenu.menuType}
                            options={[
                              { value: 'VIEW', label: '视图' },
                              { value: 'FORM', label: '表单' }
                            ]}
                            onChange={(menuType: MenuType) => {
                              const target = menuTargets(app, menuType)[0];
                              patchMenu(selectedMenu.id, (menu) => ({
                                ...menu,
                                menuType,
                                targetId: target?.value ?? ''
                              }));
                            }}
                          />
                        </span>
                        <span>
                          目标：
                          <Select
                            style={{ width: 220 }}
                            value={selectedMenu.targetId || undefined}
                            options={menuTargets(app, selectedMenu.menuType)}
                            placeholder="暂无可用目标"
                            onChange={(targetId) =>
                              patchMenu(selectedMenu.id, (menu) => ({ ...menu, targetId }))
                            }
                          />
                        </span>
                      </Space>

                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        菜单保存并发布后，可在「应用运行」中看到入口。隐藏菜单不会出现在运行端。
                      </Typography.Text>
                    </Space>
                  </Card>
                ) : (
                  <Card size="small" style={{ flex: 1, minWidth: 420 }}>
                    <Empty description="创建或选择一个菜单" />
                  </Card>
                )}
              </div>
            )
          }
        ]}
      />
    </Space>
  );
}
