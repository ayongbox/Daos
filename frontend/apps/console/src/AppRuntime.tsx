import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App as AntApp,
  Card,
  Empty,
  Menu,
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

function ViewRuntime({ appId, viewCode }: { appId: string; viewCode: string }) {
  const { message } = AntApp.useApp();
  const [detail, setDetail] = useState<ViewDetail | null>(null);
  const [page, setPage] = useState<ViewPage | null>(null);
  const [aggregatePage, setAggregatePage] = useState<ViewAggregatePage | null>(null);
  const [current, setCurrent] = useState(1);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setPage(null);
    setAggregatePage(null);
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

  const aggregated = (detail?.aggregations.length ?? 0) > 0;

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
            <Tag key={`${sort.fieldCode}-${sort.direction}`}>
              排序 {sort.fieldCode} {sort.direction === 'ASC' ? '升序' : '降序'}
            </Tag>
          ))}
        </Space>
      </Card>
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
