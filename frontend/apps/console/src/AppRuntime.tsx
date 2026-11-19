import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  App as AntApp,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import type { SchemaApp } from '@daos/metadata';
import {
  api,
  type FormRecordDetail,
  type FormSummary,
  type MenuSummary,
  type RenderNode,
  type ViewAggregatePage,
  type ViewDetail,
  type ViewPage,
  type ViewSummary
} from './api';
import { AppIcon } from './appIcons';
import { FormRuntime } from './FormRuntime';
import { readBlocks } from './pageLayout';

function displayValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (Array.isArray(value)) return `${value.length} 项`;
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

function metricValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  if (typeof value === 'boolean') return value ? '是' : '否';
  const numeric = Number(value);
  if (Number.isFinite(numeric)) {
    return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(numeric);
  }
  return String(value);
}

/** 无图表库的横向条形图，按分组结果展示单个汇总指标。 */
function GroupBars({
  rows,
  groupCodes,
  metric
}: {
  rows: Record<string, unknown>[];
  groupCodes: string[];
  metric: string;
}) {
  if (rows.length === 0) return <Empty description="暂无数据" />;
  const max = rows.reduce((acc, row) => {
    const value = Number(row[metric]);
    return Number.isFinite(value) ? Math.max(acc, value) : acc;
  }, 0);
  return (
    <Space direction="vertical" style={{ width: '100%' }} size={8}>
      {rows.map((row, index) => {
        const raw = row[metric];
        const value = Number(raw);
        const width =
          max > 0 && Number.isFinite(value) ? Math.max((value / max) * 100, 2) : 0;
        const label =
          groupCodes.map((code) => displayValue(row[code])).join(' / ') || `分组 ${index + 1}`;
        return (
          <div key={`${label}-${index}`} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <Typography.Text
              ellipsis
              style={{ width: 160, flex: '0 0 160px', fontSize: tokens.fontSizeSm }}
            >
              {label}
            </Typography.Text>
            <div
              style={{
                flex: 1,
                height: 16,
                borderRadius: 4,
                background: tokens.colorFillTertiary
              }}
            >
              <div
                style={{
                  width: `${width}%`,
                  height: '100%',
                  borderRadius: 4,
                  background: tokens.colorPrimary
                }}
              />
            </div>
            <Typography.Text
              style={{
                width: 96,
                flex: '0 0 96px',
                textAlign: 'right',
                fontVariantNumeric: 'tabular-nums'
              }}
            >
              {metricValue(raw)}
            </Typography.Text>
          </div>
        );
      })}
    </Space>
  );
}

/** 指标卡：按视图汇总展示；未配置汇总时回退为记录数。 */
function MetricsBlock({ appId, viewCode }: { appId: string; viewCode: string }) {
  const { message } = AntApp.useApp();
  const [detail, setDetail] = useState<ViewDetail | null>(null);
  const [summary, setSummary] = useState<Record<string, unknown> | null>(null);
  const [total, setTotal] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setSummary(null);
    setTotal(null);
    setLoading(true);
    api
      .view(appId, viewCode)
      .then(async (value) => {
        if (cancelled) return;
        setDetail(value);
        if (value.aggregations.length > 0) {
          const data = await api.viewSummary(appId, viewCode);
          if (!cancelled) setSummary(data);
        } else {
          const page = await api.viewRecords(appId, viewCode, 1, 1);
          if (!cancelled) setTotal(page.total);
        }
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '指标加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [appId, viewCode]);

  if (loading) return <Spin size="small" />;
  if (!detail) return <Empty description="指标视图不可用" />;

  const items =
    detail.aggregations.length > 0
      ? detail.aggregations.map((aggregate) => ({
          label: aggregate.displayName,
          value: summary?.[aggregate.displayName]
        }))
      : [{ label: '记录数', value: total }];

  return (
    <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
      {items.map((item) => (
        <Card key={item.label} size="small" style={{ minWidth: 160, flex: '1 1 160px' }}>
          <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
            {item.label}
          </Typography.Text>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {metricValue(item.value)}
          </Typography.Title>
        </Card>
      ))}
    </div>
  );
}

/** 把草稿菜单解析成运行菜单结构，用于设计预览。 */
function draftMenuList(draft: SchemaApp): MenuSummary[] {
  return (draft.menus ?? [])
    .filter((menu) => !menu.deleted && menu.visible)
    .sort((left, right) => left.sortOrder - right.sortOrder)
    .map((menu) => {
      const view =
        menu.menuType === 'VIEW'
          ? (draft.views ?? []).find((item) => !item.deleted && item.id === menu.targetId)
          : undefined;
      const targetForm =
        menu.menuType === 'FORM'
          ? draft.forms.find((item) => !item.deleted && item.id === menu.targetId)
          : undefined;
      const boundFormId = typeof menu.ext?.formId === 'string' ? menu.ext.formId : null;
      const boundForm = boundFormId
        ? draft.forms.find((item) => !item.deleted && item.id === boundFormId)
        : undefined;
      const tableId =
        view?.tableId ??
        targetForm?.tableId ??
        (menu.menuType === 'TABLE' ? menu.targetId : null);
      const fallbackForm = tableId
        ? draft.forms.find((item) => !item.deleted && item.tableId === tableId)
        : undefined;
      return {
        menuId: menu.id,
        menuCode: menu.code,
        displayName: menu.displayName,
        icon: menu.icon,
        menuType: menu.menuType,
        targetId: menu.targetId,
        targetCode: view?.code ?? targetForm?.code ?? null,
        formCode: (targetForm ?? boundForm ?? fallbackForm)?.code ?? null,
        layout: {
          blocks: readBlocks(menu, {
            viewId: view?.id,
            formId: (targetForm ?? boundForm ?? fallbackForm)?.id
          })
        }
      };
    });
}

function leafNodes(nodes: RenderNode[]): RenderNode[] {
  return nodes.flatMap((node) =>
    node.component === 'subform'
      ? []
      : [...(node.fieldCode ? [node] : []), ...leafNodes(node.children)]
  );
}

function subformNodes(nodes: RenderNode[]): RenderNode[] {
  return nodes.flatMap((node) =>
    node.component === 'subform' ? [node] : subformNodes(node.children)
  );
}

/** 记录详情：只读展示主表字段与子表数据，编辑操作交给表单运行时。 */
function RecordViewer({
  appId,
  formCode,
  recordId
}: {
  appId: string;
  formCode: string;
  recordId: string;
}) {
  const [detail, setDetail] = useState<FormRecordDetail | null>(null);
  const [nodes, setNodes] = useState<RenderNode[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setNodes(null);
    setError(null);
    Promise.all([
      api.renderForm(appId, formCode, 'VIEW'),
      api.formRecord(appId, formCode, recordId)
    ])
      .then(([render, record]) => {
        if (cancelled) return;
        setNodes(render.tree.nodes);
        setDetail(record);
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause instanceof Error ? cause.message : '详情加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [appId, formCode, recordId]);

  if (error) return <Alert type="error" showIcon message={error} />;
  if (!detail || !nodes) return <Spin />;

  const fields = leafNodes(nodes).filter((node) => !node.hidden);
  const subforms = subformNodes(nodes);

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Descriptions bordered size="small" column={1}>
        {fields.map((node) => {
          const code = node.fieldCode as string;
          const display = detail.record[`${code}_display`];
          return (
            <Descriptions.Item key={node.id} label={node.label ?? code}>
              {displayValue(
                display !== null && display !== undefined && display !== ''
                  ? display
                  : detail.record[code]
              )}
            </Descriptions.Item>
          );
        })}
      </Descriptions>

      {subforms.map((node) => {
        const columns: ColumnsType<Record<string, unknown>> = node.children
          .filter((child) => child.fieldCode)
          .map((child) => {
            const code = child.fieldCode as string;
            return {
              title: child.label ?? code,
              dataIndex: code,
              key: code,
              ellipsis: true,
              render: (value: unknown, record: Record<string, unknown>) =>
                displayValue(record[`${code}_display`] ?? value)
            };
          });
        return (
          <Card key={node.id} size="small" title={node.label ?? '明细'}>
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              scroll={{ x: 600 }}
              columns={columns}
              dataSource={detail.subforms[node.id] ?? []}
              locale={{ emptyText: '暂无明细' }}
            />
          </Card>
        );
      })}
    </Space>
  );
}

function ViewRuntime({
  appId,
  viewCode,
  formCode,
  reloadKey = 0,
  onCreate,
  onOpenRecord
}: {
  appId: string;
  viewCode: string;
  formCode?: string | null;
  reloadKey?: number;
  onCreate?: () => void;
  onOpenRecord?: (recordId: string) => void;
}) {
  const { message } = AntApp.useApp();
  const [detail, setDetail] = useState<ViewDetail | null>(null);
  const [page, setPage] = useState<ViewPage | null>(null);
  const [aggregatePage, setAggregatePage] = useState<ViewAggregatePage | null>(null);
  const [summary, setSummary] = useState<Record<string, unknown> | null>(null);
  const [boardPage, setBoardPage] = useState<ViewAggregatePage | null>(null);
  const [viewMode, setViewMode] = useState<'table' | 'board'>('table');
  const [metric, setMetric] = useState<string | null>(null);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setPage(null);
    setAggregatePage(null);
    setSummary(null);
    setBoardPage(null);
    setMetric(null);
    setViewMode('table');
    setCurrent(1);
    setLoading(true);
    api
      .view(appId, viewCode)
      .then((value) => {
        if (!cancelled) setDetail(value);
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '视图加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [appId, viewCode]);

  useEffect(() => {
    if (!detail) return;
    let cancelled = false;
    setLoading(true);
    const aggregated = detail.aggregations.length > 0;
    const request = aggregated
      ? api.viewAggregate(appId, viewCode, current, detail.pageSize)
      : api.viewRecords(appId, viewCode, current, detail.pageSize);
    request
      .then((value) => {
        if (cancelled) return;
        if (aggregated) {
          setAggregatePage(value as ViewAggregatePage);
          setPage(null);
        } else {
          setPage(value as ViewPage);
          setAggregatePage(null);
        }
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '数据加载失败');
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [appId, viewCode, current, detail, reloadKey]);

  useEffect(() => {
    if (!detail || detail.aggregations.length === 0) {
      setSummary(null);
      return;
    }
    let cancelled = false;
    api
      .viewSummary(appId, viewCode)
      .then((value) => {
        if (!cancelled) setSummary(value);
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '汇总数据加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [appId, viewCode, detail]);

  useEffect(() => {
    if (!detail || detail.aggregations.length === 0 || viewMode !== 'board') {
      setBoardPage(null);
      return;
    }
    let cancelled = false;
    api
      .viewAggregate(appId, viewCode, 1, 200)
      .then((value) => {
        if (!cancelled) setBoardPage(value);
      })
      .catch((cause) => {
        if (!cancelled && !(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '看板数据加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [appId, viewCode, detail, viewMode]);

  const aggregated = (detail?.aggregations.length ?? 0) > 0;
  const activeMetric = metric ?? detail?.aggregations[0]?.displayName ?? null;

  const columns: ColumnsType<Record<string, unknown>> = useMemo(() => {
    if (detail && aggregated) {
      return [
        ...detail.groupBy.map((group) => ({
          title: group.label,
          dataIndex: group.fieldCode,
          key: group.fieldCode,
          ellipsis: true,
          render: (value: unknown) => displayValue(value)
        })),
        ...detail.aggregations.map((aggregate) => ({
          title: aggregate.displayName,
          dataIndex: aggregate.displayName,
          key: aggregate.displayName,
          ellipsis: true,
          render: (value: unknown) => displayValue(value)
        }))
      ];
    }
    const configured = detail?.columns.filter((column) => column.visible) ?? [];
    if (configured.length > 0) {
      return configured.map((column) => ({
        title: column.label,
        dataIndex: column.fieldCode,
        key: column.fieldCode,
        width: column.width ?? undefined,
        ellipsis: true,
        render: (value: unknown, record: Record<string, unknown>) =>
          displayValue(record[`${column.fieldCode}_display`] ?? value)
      }));
    }
    const sample = page?.records[0];
    if (!sample) return [];
    return Object.keys(sample).map((key) => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (value: unknown, record: Record<string, unknown>) =>
        displayValue(record[`${key}_display`] ?? value)
    }));
  }, [detail, page, aggregated]);

  if (loading && !detail) {
    return (
      <Card size="small">
        <Spin />
      </Card>
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={12}>
      <Card
        size="small"
        extra={
          !aggregated && formCode && onCreate ? (
            <Button size="small" type="primary" onClick={onCreate}>
              新建
            </Button>
          ) : null
        }
      >
        <Space size={12} wrap>
          <Typography.Text strong>{detail?.displayName ?? viewCode}</Typography.Text>
          <Tag>{detail?.tableCode}</Tag>
          {aggregated ? <Tag color="purple">汇总视图</Tag> : null}
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            共 {(aggregated ? aggregatePage?.total : page?.total) ?? 0}
            {aggregated ? ' 组' : ' 条'}
          </Typography.Text>
          {detail?.filters.map((filter) => (
            <Tag key={`${filter.fieldCode}-${filter.operator}`} color="blue">
              {filter.fieldCode} {filter.operator} {displayValue(filter.value)}
            </Tag>
          ))}
          {detail?.sorts.map((sort) => (
            <Tag key={`${sort.aggregation ?? sort.fieldCode}-${sort.direction}`}>
              排序 {sort.aggregation ?? sort.fieldCode} {sort.direction === 'ASC' ? '升序' : '降序'}
            </Tag>
          ))}
          {aggregated ? (
            <Segmented
              size="small"
              value={viewMode}
              onChange={(value) => setViewMode(value as 'table' | 'board')}
              options={[
                { label: '表格', value: 'table' },
                { label: '看板', value: 'board' }
              ]}
            />
          ) : null}
        </Space>
      </Card>
      {aggregated && viewMode === 'board' ? (
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {(detail?.aggregations ?? []).map((aggregate) => (
              <Card
                key={aggregate.displayName}
                size="small"
                style={{ minWidth: 160, flex: '1 1 160px' }}
              >
                <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
                  {aggregate.displayName}
                </Typography.Text>
                <Typography.Title level={3} style={{ margin: 0 }}>
                  {metricValue(summary?.[aggregate.displayName])}
                </Typography.Title>
              </Card>
            ))}
          </div>
          <Card
            size="small"
            title="分组分布"
            extra={
              detail && detail.aggregations.length > 1 ? (
                <Select
                  size="small"
                  style={{ width: 160 }}
                  value={activeMetric ?? undefined}
                  onChange={setMetric}
                  options={detail.aggregations.map((aggregate) => ({
                    value: aggregate.displayName,
                    label: aggregate.displayName
                  }))}
                />
              ) : null
            }
          >
            <Space direction="vertical" style={{ width: '100%' }} size={8}>
              {detail && detail.groupBy.length > 0 && activeMetric ? (
                <GroupBars
                  rows={boardPage?.rows ?? []}
                  groupCodes={detail.groupBy.map((group) => group.fieldCode)}
                  metric={activeMetric}
                />
              ) : (
                <Empty description="该视图未配置分组，看板仅展示指标卡" />
              )}
              {boardPage && boardPage.total > boardPage.size ? (
                <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
                  共 {boardPage.total} 组，图表仅展示前 {boardPage.size} 组
                </Typography.Text>
              ) : null}
            </Space>
          </Card>
        </Space>
      ) : (
        <Card size="small">
          <Table
            rowKey={(record) =>
              String(record.id ?? record.record_id ?? record._id ?? JSON.stringify(record))
            }
            size="small"
            loading={loading}
            columns={columns}
            dataSource={(aggregated ? aggregatePage?.rows : page?.records) ?? []}
            locale={{ emptyText: '暂无数据' }}
            scroll={{ x: 720 }}
            onRow={(record) => {
              const recordId = record.id ?? record.record_id ?? record._id;
              const clickable = !aggregated && !!formCode && !!onOpenRecord && recordId != null;
              return {
                style: clickable ? { cursor: 'pointer' } : undefined,
                onClick: clickable
                  ? () => onOpenRecord?.(String(recordId))
                  : undefined
              };
            }}
            pagination={{
              current: (aggregated ? aggregatePage?.current : page?.current) ?? 1,
              pageSize: (aggregated ? aggregatePage?.size : page?.size) ?? detail?.pageSize ?? 20,
              total: (aggregated ? aggregatePage?.total : page?.total) ?? 0,
              showSizeChanger: false,
              onChange: (next) => setCurrent(next)
            }}
          />
        </Card>
      )}
    </Space>
  );
}

export function AppRuntime({
  appId,
  draft,
  initialMenuId
}: {
  appId: string;
  draft?: SchemaApp;
  initialMenuId?: string | null;
}) {
  const { message } = AntApp.useApp();
  const [mode, setMode] = useState<'runtime' | 'preview'>('runtime');
  const [runtimeMenus, setRuntimeMenus] = useState<MenuSummary[] | null>(null);
  const [publishedForms, setPublishedForms] = useState<FormSummary[]>([]);
  const [publishedViews, setPublishedViews] = useState<ViewSummary[]>([]);
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);
  const [editor, setEditor] = useState<{ formCode: string; recordId?: string } | null>(null);
  const [viewer, setViewer] = useState<{ formCode: string; recordId: string } | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const appliedInitialMenu = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setRuntimeMenus(null);
    api
      .menus(appId)
      .then((value) => {
        if (!cancelled) setRuntimeMenus(value);
      })
      .catch((cause) => {
        if (cancelled) return;
        setRuntimeMenus([]);
        if (!(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '菜单加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [appId]);

  useEffect(() => {
    let cancelled = false;
    api
      .forms(appId)
      .then((value) => {
        if (!cancelled) setPublishedForms(value);
      })
      .catch(() => undefined);
    api
      .views(appId)
      .then((value) => {
        if (!cancelled) setPublishedViews(value);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [appId]);

  const previewMenus = useMemo(() => (draft ? draftMenuList(draft) : []), [draft]);
  const menus = mode === 'preview' ? previewMenus : runtimeMenus;

  useEffect(() => {
    setEditor(null);
    setViewer(null);
    if (!menus) return;
    if (
      initialMenuId &&
      appliedInitialMenu.current !== initialMenuId &&
      menus.some((menu) => menu.menuId === initialMenuId)
    ) {
      appliedInitialMenu.current = initialMenuId;
      setActiveMenuId(initialMenuId);
      return;
    }
    setActiveMenuId((current) =>
      current && menus.some((menu) => menu.menuId === current)
        ? current
        : menus[0]?.menuId ?? null
    );
  }, [menus, mode, initialMenuId]);

  /** 设计预览只允许进入已发布目标，避免草稿新增页面请求 404。 */
  function isAvailable(menu: MenuSummary): boolean {
    if (mode !== 'preview') return true;
    if (!menu.targetCode) return false;
    if (menu.menuType === 'VIEW') {
      return publishedViews.some((view) => view.viewCode === menu.targetCode);
    }
    if (menu.menuType === 'FORM') {
      return publishedForms.some((form) => form.formCode === menu.targetCode);
    }
    return false;
  }

  /** 布局块引用的是元数据 id，运行端在这里解析成可请求的编码。 */
  function viewCodeOf(viewId?: string): string | null {
    if (!viewId) return null;
    if (mode === 'preview') {
      return draft?.views?.find((view) => !view.deleted && view.id === viewId)?.code ?? null;
    }
    return publishedViews.find((view) => view.viewId === viewId)?.viewCode ?? null;
  }

  function formCodeOf(formId?: string): string | null {
    if (!formId) return null;
    if (mode === 'preview') {
      return draft?.forms.find((form) => !form.deleted && form.id === formId)?.code ?? null;
    }
    return publishedForms.find((form) => form.formId === formId)?.formCode ?? null;
  }

  const activeMenu = menus?.find((menu) => menu.menuId === activeMenuId) ?? null;
  const pageTargetCode = activeMenu?.targetCode ?? null;
  const layoutBlocks = activeMenu?.layout?.blocks ?? [];
  const activeFormCode =
    activeMenu?.formCode &&
    (mode !== 'preview' || publishedForms.some((form) => form.formCode === activeMenu.formCode))
      ? activeMenu.formCode
      : null;

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={0}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '12px 16px',
          background: tokens.colorBgContainer,
          border: `1px solid ${tokens.colorBorder}`,
          borderRadius: `${tokens.radiusMd}px ${tokens.radiusMd}px 0 0`,
          flexWrap: 'wrap'
        }}
      >
        <AppIcon icon={draft?.icon} name={draft?.displayName ?? '应用'} size={32} />
        <div style={{ minWidth: 0 }}>
          <Typography.Text strong ellipsis style={{ display: 'block', fontSize: 15 }}>
            {draft?.displayName ?? '应用运行端'}
          </Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {mode === 'preview' ? '设计预览 · 当前草稿结构' : '运行态 · 最近一次发布版本'}
          </Typography.Text>
        </div>
        <div style={{ flex: 1 }} />
        <Button size="small" onClick={() => setReloadKey((current) => current + 1)}>
          刷新
        </Button>
        {draft ? (
          <Segmented
            size="small"
            value={mode}
            onChange={(value) => setMode(value as 'runtime' | 'preview')}
            options={[
              { label: '运行态', value: 'runtime' },
              { label: '设计预览', value: 'preview' }
            ]}
          />
        ) : null}
      </div>

      {menus !== null && menus.length > 0 ? (
        <div
          style={{
            background: tokens.colorBgContainer,
            borderLeft: `1px solid ${tokens.colorBorder}`,
            borderRight: `1px solid ${tokens.colorBorder}`,
            borderTop: `1px solid ${tokens.colorBorder}`,
            padding: '0 8px'
          }}
        >
          <Tabs
            activeKey={activeMenuId ?? undefined}
            onChange={setActiveMenuId}
            tabBarStyle={{ marginBottom: 0 }}
            items={menus.map((menu) => ({
              key: menu.menuId,
              disabled: !isAvailable(menu),
              label: (
                <Space size={6}>
                  {menu.icon ? (
                    <AppIcon icon={menu.icon} name={menu.displayName} size={16} />
                  ) : null}
                  <span>{menu.displayName}</span>
                  {isAvailable(menu) ? null : <Tag>未发布</Tag>}
                </Space>
              )
            }))}
          />
        </div>
      ) : null}

      <div
        style={{
          padding: 16,
          background: tokens.colorBgContainer,
          border: `1px solid ${tokens.colorBorder}`,
          borderRadius: `0 0 ${tokens.radiusMd}px ${tokens.radiusMd}px`,
          minHeight: 320
        }}
      >
        {menus === null ? (
          <Spin />
        ) : menus.length === 0 ? (
          <Empty
            description={
              mode === 'preview'
                ? '草稿里还没有页面，请先在「页面」中生成'
                : '还没有已发布的菜单，请先在「页面」生成并发布'
            }
          />
        ) : !activeMenu ? (
          <Empty description="选择一个页面" />
        ) : !isAvailable(activeMenu) ? (
          <Alert
            type="info"
            showIcon
            message="该页面尚未发布"
            description="设计预览只能进入已发布页面；请先保存并发布应用。"
          />
        ) : activeMenu.targetCode === null || activeMenu.targetCode === undefined ? (
          <Alert type="warning" showIcon message="菜单指向的目标已不存在，请重新发布应用" />
        ) : layoutBlocks.length > 0 ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {layoutBlocks.map((block) => {
              if (block.type === 'text') {
                return (
                  <div key={block.id}>
                    {block.title ? (
                      <Typography.Title level={5} style={{ marginTop: 0 }}>
                        {block.title}
                      </Typography.Title>
                    ) : null}
                    <Typography.Paragraph
                      type="secondary"
                      style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}
                    >
                      {block.text ?? ''}
                    </Typography.Paragraph>
                  </div>
                );
              }
              if (block.type === 'metrics') {
                const code = viewCodeOf(block.viewId) ?? pageTargetCode;
                return (
                  <div key={block.id}>
                    {block.title ? (
                      <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                        {block.title}
                      </Typography.Text>
                    ) : null}
                    {code ? (
                      <MetricsBlock appId={appId} viewCode={code} />
                    ) : (
                      <Alert type="warning" showIcon message="该指标卡还没有选择视图" />
                    )}
                  </div>
                );
              }
              if (block.type === 'form') {
                const code = formCodeOf(block.formId) ?? activeFormCode;
                return (
                  <div key={block.id}>
                    {block.title ? (
                      <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                        {block.title}
                      </Typography.Text>
                    ) : null}
                    {code ? (
                      <FormRuntime appId={appId} fixedFormCode={code} />
                    ) : (
                      <Alert type="warning" showIcon message="该表单块还没有选择表单" />
                    )}
                  </div>
                );
              }
              const code = viewCodeOf(block.viewId) ?? pageTargetCode;
              const formCode = formCodeOf(block.formId) ?? activeFormCode;
              return code ? (
                <div key={block.id}>
                  {block.title ? (
                    <Typography.Text strong style={{ display: 'block', marginBottom: 8 }}>
                      {block.title}
                    </Typography.Text>
                  ) : null}
                  <ViewRuntime
                    appId={appId}
                    viewCode={code}
                    formCode={formCode}
                    reloadKey={reloadKey}
                    onCreate={
                      formCode ? () => setEditor({ formCode }) : undefined
                    }
                    onOpenRecord={
                      formCode
                        ? (recordId) => setViewer({ formCode, recordId })
                        : undefined
                    }
                  />
                </div>
              ) : (
                <Alert key={block.id} type="warning" showIcon message="该列表块还没有选择视图" />
              );
            })}
          </Space>
        ) : activeMenu.menuType === 'FORM' ? (
          <FormRuntime appId={appId} fixedFormCode={activeMenu.targetCode} />
        ) : activeMenu.menuType === 'VIEW' ? (
          <ViewRuntime
            appId={appId}
            viewCode={activeMenu.targetCode}
            formCode={activeFormCode}
            reloadKey={reloadKey}
            onCreate={
              activeFormCode ? () => setEditor({ formCode: activeFormCode }) : undefined
            }
            onOpenRecord={
              activeFormCode
                ? (recordId) => setViewer({ formCode: activeFormCode, recordId })
                : undefined
            }
          />
        ) : (
          <Empty description={`菜单类型 ${activeMenu.menuType} 暂未提供运行端`} />
        )}
      </div>

      <Drawer
        width={720}
        open={viewer !== null}
        title="记录详情"
        onClose={() => setViewer(null)}
        destroyOnClose
        extra={
          viewer ? (
            <Button
              type="primary"
              onClick={() => {
                setEditor(viewer);
                setViewer(null);
              }}
            >
              编辑
            </Button>
          ) : null
        }
      >
        {viewer ? (
          <RecordViewer
            appId={appId}
            formCode={viewer.formCode}
            recordId={viewer.recordId}
          />
        ) : null}
      </Drawer>

      <Drawer
        width={720}
        open={editor !== null}
        title={editor?.recordId ? '编辑记录' : '新建记录'}
        onClose={() => setEditor(null)}
        destroyOnClose
      >
        {editor ? (
          <FormRuntime
            appId={appId}
            fixedFormCode={editor.formCode}
            recordId={editor.recordId}
            embedded
            onSaved={() => {
              setEditor(null);
              setReloadKey((current) => current + 1);
            }}
          />
        ) : null}
      </Drawer>
    </Space>
  );
}
