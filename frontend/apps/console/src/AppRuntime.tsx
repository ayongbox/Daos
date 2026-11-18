import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntApp,
  Card,
  Empty,
  Menu,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import {
  api,
  type MenuSummary,
  type ViewAggregatePage,
  type ViewDetail,
  type ViewPage
} from './api';
import { FormRuntime } from './FormRuntime';

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

function ViewRuntime({ appId, viewCode }: { appId: string; viewCode: string }) {
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
  }, [appId, viewCode, current, detail]);

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
        render: (value: unknown) => displayValue(value)
      }));
    }
    const sample = page?.records[0];
    if (!sample) return [];
    return Object.keys(sample).map((key) => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (value: unknown) => displayValue(value)
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
      <Card size="small">
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

export function AppRuntime({ appId }: { appId: string }) {
  const { message } = AntApp.useApp();
  const [menus, setMenus] = useState<MenuSummary[] | null>(null);
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setMenus(null);
    setActiveMenuId(null);
    api
      .menus(appId)
      .then((value) => {
        if (cancelled) return;
        setMenus(value);
        setActiveMenuId(value[0]?.menuId ?? null);
      })
      .catch((cause) => {
        if (cancelled) return;
        setMenus([]);
        if (!(cause instanceof ApiError && cause.status === 401)) {
          message.error(cause instanceof Error ? cause.message : '菜单加载失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [appId]);

  const activeMenu = menus?.find((menu) => menu.menuId === activeMenuId) ?? null;

  if (menus === null) {
    return (
      <Card size="small">
        <Spin />
      </Card>
    );
  }

  if (menus.length === 0) {
    return (
      <Card size="small">
        <Empty description="还没有已发布的菜单，请先在「视图与报表」配置菜单并发布" />
      </Card>
    );
  }

  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
      <Card size="small" style={{ width: 220, flex: '0 0 220px' }} bodyStyle={{ padding: 8 }}>
        <Menu
          mode="inline"
          selectedKeys={activeMenuId ? [activeMenuId] : []}
          style={{ borderInlineEnd: 'none', background: 'transparent' }}
          items={menus.map((menu) => ({
            key: menu.menuId,
            label: menu.displayName
          }))}
          onClick={({ key }) => setActiveMenuId(key)}
        />
      </Card>

      <div style={{ flex: 1, minWidth: 0 }}>
        {!activeMenu ? (
          <Card size="small">
            <Empty description="选择一个菜单" />
          </Card>
        ) : activeMenu.targetCode === null || activeMenu.targetCode === undefined ? (
          <Alert type="warning" showIcon message="菜单指向的目标已不存在，请重新发布应用" />
        ) : activeMenu.menuType === 'FORM' ? (
          <FormRuntime appId={appId} fixedFormCode={activeMenu.targetCode} />
        ) : activeMenu.menuType === 'VIEW' ? (
          <ViewRuntime appId={appId} viewCode={activeMenu.targetCode} />
        ) : (
          <Card size="small">
            <Empty description={`菜单类型 ${activeMenu.menuType} 暂未提供运行端`} />
          </Card>
        )}
      </div>

      <div style={{ width: '100%' }}>
        <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
          运行端展示的是最近一次发布的内容；草稿改动需要重新发布后才会生效。
        </Typography.Text>
      </div>
    </div>
  );
}
