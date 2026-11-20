import { useState } from 'react';
import {
  App as AntApp,
  Button,
  Card,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Space,
  Table,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { tokens } from '@daos/ui';
import { ApiError } from '@daos/sdk';
import type { SchemaApp, SchemaMenu } from '@daos/metadata';
import { api, type AppSummary, type ReleaseView } from './api';
import { AppIcon } from './appIcons';

type Capability = 'model' | 'form' | 'view' | 'page' | 'runtime';

interface StatCard {
  key: Capability;
  label: string;
  value: number;
  hint: string;
}

/** 应用工作台：进入应用后的首页，集中展示结构概览与页面入口。 */
export function AppWorkbench({
  app,
  summary,
  onOpenCapability,
  onOpenPage,
  onOpenTable,
  onRefreshSummary
}: {
  app: SchemaApp;
  summary?: AppSummary | null;
  onOpenCapability: (capability: Capability) => void;
  onOpenPage: (menuId: string) => void;
  onOpenTable: (tableId: string) => void;
  onRefreshSummary: () => void;
}) {
  const { message, modal } = AntApp.useApp();
  const [releasesOpen, setReleasesOpen] = useState(false);
  const [releases, setReleases] = useState<ReleaseView[]>([]);
  const [releasesLoading, setReleasesLoading] = useState(false);

  const forms = app.forms.filter((form) => !form.deleted);
  const views = (app.views ?? []).filter((view) => !view.deleted);
  const menus = (app.menus ?? []).filter((menu) => !menu.deleted);
  const tables = app.tables.filter((table) => !table.deleted);

  function showError(cause: unknown) {
    // 401 由全局会话失效事件统一处理并回到登录页，不再叠加提示
    if (cause instanceof ApiError && cause.status === 401) return;
    message.error(cause instanceof Error ? cause.message : '请求失败');
  }

  async function loadReleases() {
    setReleasesLoading(true);
    try {
      setReleases(await api.releases(app.id));
    } catch (cause) {
      showError(cause);
    } finally {
      setReleasesLoading(false);
    }
  }

  function openReleases() {
    setReleasesOpen(true);
    void loadReleases();
  }

  function publish() {
    let remark = '';
    modal.confirm({
      title: '发布并执行结构演进？',
      content: (
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Typography.Text type="secondary">
            将按当前草稿执行 DDL 并激活新版本；失败会自动回滚 SQL。
          </Typography.Text>
          <Input.TextArea
            rows={3}
            placeholder="发布说明（可选）"
            onChange={(event) => {
              remark = event.target.value;
            }}
          />
        </Space>
      ),
      onOk: async () => {
        try {
          await api.apply(app.id, remark.trim() || null);
          message.success('发布成功');
          onRefreshSummary();
          if (releasesOpen) void loadReleases();
        } catch (cause) {
          showError(cause);
        }
      }
    });
  }

  async function rollbackToVersion(releaseVersion: number) {
    try {
      await api.rollbackToVersion(app.id, releaseVersion);
      message.success(`已回滚到 v${releaseVersion}`);
      await loadReleases();
      onRefreshSummary();
    } catch (cause) {
      showError(cause);
    }
  }

  const releaseColumns: ColumnsType<ReleaseView> = [
    {
      title: '版本',
      dataIndex: 'releaseVersion',
      width: 140,
      render: (value: number, release) => (
        <Space size={6}>
          <span>v{value}</span>
          {release.active ? <Tag color="green">当前生效</Tag> : null}
        </Space>
      )
    },
    { title: '发布人', dataIndex: 'createdBy', width: 120 },
    {
      title: '发布时间',
      dataIndex: 'createdAt',
      width: 160,
      render: (value: string) => (value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-')
    },
    {
      title: '说明',
      dataIndex: 'remark',
      render: (value: string | null) => value?.trim() || '-'
    },
    {
      title: '操作',
      width: 100,
      render: (_, release) =>
        release.active ? null : (
          <Popconfirm
            title={`回滚到 v${release.releaseVersion}？`}
            description="将执行结构回退并切换生效版本，被删除列的数据不会恢复。"
            okText="回滚"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => rollbackToVersion(release.releaseVersion)}
          >
            <Button size="small" type="link" danger>
              回滚
            </Button>
          </Popconfirm>
        )
    }
  ];

  const stats: StatCard[] = [
    { key: 'model', label: '数据表', value: tables.length, hint: '字段与索引' },
    { key: 'form', label: '表单', value: forms.length, hint: '录入与编辑' },
    { key: 'view', label: '视图', value: views.length, hint: '列表与汇总' },
    { key: 'page', label: '页面', value: menus.length, hint: '运行端导航' }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        bodyStyle={{ padding: 24 }}
        style={{
          background: `linear-gradient(120deg, ${tokens.colorPrimaryBg} 0%, ${tokens.colorBgContainer} 70%)`,
          border: `1px solid ${tokens.colorBorder}`
        }}
      >
        <div
          style={{
            display: 'flex',
            gap: 20,
            alignItems: 'flex-start',
            flexWrap: 'wrap'
          }}
        >
          <AppIcon icon={app.icon} name={app.displayName} size={56} />
          <div style={{ flex: 1, minWidth: 240 }}>
            <Space size={8} wrap style={{ marginBottom: 6 }}>
              <Typography.Title level={3} style={{ margin: 0 }}>
                {app.displayName}
              </Typography.Title>
              {summary && summary.activeReleaseVersion > 0 ? (
                <Tag color="green">已发布 v{summary.activeReleaseVersion}</Tag>
              ) : (
                <Tag>未发布</Tag>
              )}
              {summary?.changedSinceRelease ? <Tag color="orange">未发布改动</Tag> : null}
            </Space>
            <Typography.Paragraph
              type="secondary"
              style={{ margin: 0, maxWidth: 640 }}
              ellipsis={{ rows: 2 }}
            >
              {app.description?.trim() || '暂无描述，可在应用设置中补充这个应用的用途。'}
            </Typography.Paragraph>
          </div>
          <Space size={8} wrap>
            <Button onClick={openReleases}>版本记录</Button>
            <Button onClick={publish}>发布</Button>
            <Button onClick={() => onOpenCapability('page')}>配置页面</Button>
            <Button type="primary" onClick={() => onOpenCapability('runtime')}>
              打开应用
            </Button>
          </Space>
        </div>
      </Card>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: 12
        }}
      >
        {stats.map((stat) => (
          <Card
            key={stat.key}
            hoverable
            size="small"
            onClick={() => onOpenCapability(stat.key)}
            bodyStyle={{ padding: '14px 16px' }}
          >
            <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
              {stat.label}
            </Typography.Text>
            <div style={{ fontSize: 26, fontWeight: 600, lineHeight: '34px' }}>
              {stat.value}
            </div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {stat.hint}
            </Typography.Text>
          </Card>
        ))}
      </div>

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 16,
          alignItems: 'start'
        }}
      >
        <Card
          size="small"
          title="应用页面"
          extra={
            <Button size="small" type="link" onClick={() => onOpenCapability('page')}>
              管理页面
            </Button>
          }
          bodyStyle={{ padding: menus.length === 0 ? 24 : 12 }}
        >
          {menus.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="还没有页面，从数据表一键生成"
            >
              <Button size="small" type="primary" onClick={() => onOpenCapability('page')}>
                去生成页面
              </Button>
            </Empty>
          ) : (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
                gap: 8
              }}
            >
              {[...menus]
                .sort((left, right) => left.sortOrder - right.sortOrder)
                .map((menu: SchemaMenu) => (
                  <button
                    key={menu.id}
                    type="button"
                    onClick={() => onOpenPage(menu.id)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      padding: '10px 12px',
                      borderRadius: tokens.radiusMd,
                      border: `1px solid ${tokens.colorBorder}`,
                      background: tokens.colorBgContainer,
                      cursor: 'pointer',
                      textAlign: 'left'
                    }}
                  >
                    <AppIcon icon={menu.icon} name={menu.displayName} size={28} />
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <Typography.Text strong ellipsis style={{ display: 'block' }}>
                        {menu.displayName}
                      </Typography.Text>
                      <Typography.Text
                        type="secondary"
                        style={{ fontSize: 12 }}
                        ellipsis
                      >
                        {menu.visible ? menu.code : '已隐藏'}
                      </Typography.Text>
                    </div>
                  </button>
                ))}
            </div>
          )}
        </Card>

        <Card
          size="small"
          title="数据表"
          extra={
            <Button size="small" type="link" onClick={() => onOpenCapability('model')}>
              数据建模
            </Button>
          }
          bodyStyle={{ padding: tables.length === 0 ? 24 : '4px 12px' }}
        >
          {tables.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有数据表" />
          ) : (
            <Space direction="vertical" size={0} style={{ width: '100%' }}>
              {tables.map((table) => (
                <div
                  key={table.id}
                  onClick={() => onOpenTable(table.id)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '8px 4px',
                    cursor: 'pointer',
                    borderBottom: `1px solid ${tokens.colorBorder}`
                  }}
                >
                  <AppIcon icon={table.icon} name={table.displayName} size={24} />
                  <Typography.Text ellipsis style={{ flex: 1, minWidth: 0 }}>
                    {table.displayName}
                  </Typography.Text>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    {table.fields.length} 字段
                  </Typography.Text>
                </div>
              ))}
            </Space>
          )}
        </Card>
      </div>

      <Drawer
        title="版本记录"
        width={720}
        open={releasesOpen}
        onClose={() => setReleasesOpen(false)}
        styles={{ body: { padding: 16 } }}
      >
        <Table<ReleaseView>
          rowKey={(release) => String(release.releaseVersion)}
          size="small"
          loading={releasesLoading}
          columns={releaseColumns}
          dataSource={releases}
          pagination={false}
          locale={{ emptyText: <Empty description="还没有发布记录" /> }}
          style={{
            borderRadius: tokens.radiusMd,
            border: `1px solid ${tokens.colorBorder}`
          }}
        />
      </Drawer>
    </Space>
  );
}
