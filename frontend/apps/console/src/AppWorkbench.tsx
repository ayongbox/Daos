import { Button, Card, Space, Tag, Typography } from 'antd';
import { EmptyState, tokens } from '@daos/ui';
import type { SchemaApp, SchemaMenu } from '@daos/metadata';
import type { AppSummary } from './api';
import { AppIcon } from './appIcons';

type Capability = 'model' | 'form' | 'view' | 'page' | 'runtime' | 'release';

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
  onOpenTable
}: {
  app: SchemaApp;
  summary?: AppSummary | null;
  onOpenCapability: (capability: Capability) => void;
  onOpenPage: (menuId: string) => void;
  onOpenTable: (tableId: string) => void;
}) {
  const forms = app.forms.filter((form) => !form.deleted);
  const views = (app.views ?? []).filter((view) => !view.deleted);
  const menus = (app.menus ?? []).filter((menu) => !menu.deleted);
  const tables = app.tables.filter((table) => !table.deleted);

  const stats: StatCard[] = [
    { key: 'model', label: '数据表', value: tables.length, hint: '字段与索引' },
    { key: 'form', label: '表单', value: forms.length, hint: '录入与编辑' },
    { key: 'view', label: '视图', value: views.length, hint: '列表与汇总' },
    { key: 'page', label: '页面', value: menus.length, hint: '运行端导航' }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        styles={{ body: { padding: 24 } }}
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
            <Button onClick={() => onOpenCapability('release')}>版本与发布</Button>
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
            styles={{ body: { padding: '14px 16px' } }}
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
          styles={{ body: { padding: menus.length === 0 ? 24 : 12 } }}
        >
          {menus.length === 0 ? (
            <EmptyState
              compact
              title="还没有页面"
              description="从数据表一键生成。"
              action={
                <Button size="small" type="primary" onClick={() => onOpenCapability('page')}>
                  去生成页面
                </Button>
              }
            />
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
          styles={{ body: { padding: tables.length === 0 ? 24 : '4px 12px' } }}
        >
          {tables.length === 0 ? (
            <EmptyState compact title="还没有数据表" description="先在数据建模中创建数据表。" />
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
    </Space>
  );
}
