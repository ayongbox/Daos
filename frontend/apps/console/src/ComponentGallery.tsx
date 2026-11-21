import { useState, type ReactNode } from 'react';
import { App as AntApp, Button, Card, Divider, Input, Space, Tag, Typography } from 'antd';
import {
  CanvasItemShell,
  PanelField,
  PanelSection,
  PropertyPanel,
  SwitchRow
} from '@daos/designer';
import {
  DetailDrawer,
  EmptyState,
  ErrorState,
  ForbiddenState,
  PageHeader,
  SkeletonCard,
  SkeletonTable,
  StatCards,
  ToolbarRow,
  tokens
} from '@daos/ui';

/**
 * 组件预览页：新增基础组件须先在本页登记展示，确认视觉一致后再进入业务开发。
 * 集中展示四态组件、色彩令牌与圆角/控件高度等规格，是 UI 规范的可视化验收入口。
 */

function SectionTitle({ title, description }: { title: string; description?: string }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <Typography.Title level={5} style={{ margin: 0, fontSize: tokens.fontSizeLg }}>
        {title}
      </Typography.Title>
      {description ? (
        <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
          {description}
        </Typography.Text>
      ) : null}
    </div>
  );
}

/** 组件登记卡：名称 + 用途 + 预览区。 */
function ComponentEntry({ name, usage, children }: { name: string; usage: string; children: ReactNode }) {
  return (
    <Card styles={{ body: { padding: 16 } }}>
      <Space size={8} style={{ marginBottom: 12 }}>
        <Typography.Text strong>{name}</Typography.Text>
        <Tag style={{ marginInlineEnd: 0 }}>{usage}</Tag>
      </Space>
      <div
        style={{
          border: `1px dashed ${tokens.colorBorder}`,
          borderRadius: tokens.radiusMd,
          overflow: 'hidden'
        }}
      >
        {children}
      </div>
    </Card>
  );
}

/** 色彩令牌色板。 */
function Swatch({ name, value, dark = false }: { name: string; value: string; dark?: boolean }) {
  return (
    <div>
      <div
        style={{
          height: 48,
          borderRadius: tokens.radiusSm,
          background: value,
          border: `1px solid ${tokens.colorBorder}`
        }}
      />
      <div style={{ fontSize: tokens.fontSizeSm, color: tokens.colorTextPrimary, marginTop: 6 }}>{name}</div>
      <div style={{ fontSize: tokens.fontSizeSm, color: tokens.colorTextTertiary }}>{value}{dark ? ' · 深底' : ''}</div>
    </div>
  );
}

/** 规格条目：名称 + 数值 + 视觉示意。 */
function SpecItem({ label, value, preview }: { label: string; value: string; preview: ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
      <div style={{ width: 120, fontSize: tokens.fontSizeSm, color: tokens.colorTextSecondary }}>{label}</div>
      <div style={{ width: 72, fontSize: tokens.fontSizeSm, color: tokens.colorTextPrimary, fontWeight: 500 }}>{value}</div>
      <div style={{ flex: 1 }}>{preview}</div>
    </div>
  );
}

export function ComponentGallery() {
  const { message } = AntApp.useApp();
  const [retryCount, setRetryCount] = useState(0);
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <div>
        <Typography.Title level={4} style={{ margin: 0, fontSize: tokens.fontSizeHeading }}>
          组件预览
        </Typography.Title>
        <Typography.Text type="secondary">
          新增组件需先在本页登记，确认视觉一致后再进入业务开发；颜色与圆角只能取自设计令牌。
        </Typography.Text>
      </div>

      <div>
        <SectionTitle title="四态组件" description="加载（骨架屏）/ 空态 / 错误态 / 无权限，来自 @daos/ui，替代全屏 Spin 与散落的 message.error。" />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          <ComponentEntry name="SkeletonCard" usage="卡片加载">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <SkeletonCard avatar rows={3} />
            </div>
          </ComponentEntry>
          <ComponentEntry name="SkeletonTable" usage="表格加载">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <SkeletonTable rows={3} columns={4} />
            </div>
          </ComponentEntry>
          <ComponentEntry name="EmptyState" usage="空数据">
            <EmptyState
              title="还没有数据"
              description="轻量图标 + 一句引导 + 操作，不使用大面积色块。"
              action={<Button type="primary" onClick={() => message.info('已触发新建')}>立即创建</Button>}
            />
          </ComponentEntry>
          <ComponentEntry name="ErrorState" usage="加载失败">
            <ErrorState
              description={`请求未能完成，请稍后重试。${retryCount > 0 ? `（已重试 ${retryCount} 次）` : ''}`}
              onRetry={() => {
                setRetryCount((count) => count + 1);
                message.info('已触发重试');
              }}
            />
          </ComponentEntry>
          <ComponentEntry name="ForbiddenState" usage="无权限">
            <ForbiddenState
              description="你没有该模块的访问权限，可联系管理员开通。"
              action={<Button onClick={() => message.info('已发起权限申请')}>申请权限</Button>}
            />
          </ComponentEntry>
        </div>
      </div>

      <div>
        <SectionTitle title="通用业务组件" description="来自 @daos/ui patterns，替代各页自拼的页头/工具行/统计卡/详情抽屉版式。" />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          <ComponentEntry name="PageHeader" usage="页头">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <PageHeader
                title="页面标题"
                description="一句话说明页面用途"
                extra={<Button type="primary" size="small">主操作</Button>}
              />
            </div>
          </ComponentEntry>
          <ComponentEntry name="ToolbarRow" usage="工具行">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <ToolbarRow
                info={<Typography.Text type="secondary">共 12 条记录</Typography.Text>}
                actions={
                  <Space size={8}>
                    <Button size="small">次要操作</Button>
                    <Button size="small" type="primary">主要操作</Button>
                  </Space>
                }
              />
            </div>
          </ComponentEntry>
          <ComponentEntry name="StatCards" usage="统计卡">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <StatCards
                items={[
                  { key: 'a', label: '记录数', value: '1,280', hint: '较上周 +12' },
                  { key: 'b', label: '已发布表单', value: '6', hint: '共 8 个' },
                  { key: 'c', label: '今日新增', value: '37' }
                ]}
              />
            </div>
          </ComponentEntry>
          <ComponentEntry name="DetailDrawer" usage="详情抽屉">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <Button size="small" onClick={() => setDrawerOpen(true)}>打开详情抽屉</Button>
              <DetailDrawer
                title="详情"
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
              >
                <Typography.Text type="secondary">
                  统一详情抽屉：默认宽度 min(720px, 100vw - 48px)，关闭时销毁内容。
                </Typography.Text>
              </DetailDrawer>
            </div>
          </ComponentEntry>
        </div>
      </div>

      <div>
        <SectionTitle title="设计器共享组件" description="来自 @daos/designer，供表单/视图/页面设计器复用的属性面板与画布条目。" />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
          <ComponentEntry name="PropertyPanel + PanelField + SwitchRow" usage="属性面板">
            <div style={{ padding: 16, background: tokens.colorBgLayout, display: 'flex', justifyContent: 'center' }}>
              <PropertyPanel>
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <PanelField label="显示名称">
                    <Input defaultValue="订单编号" />
                  </PanelField>
                  <PanelField label="占宽" layout="inline" labelWidth={64}>
                    <Input defaultValue="整行" />
                  </PanelField>
                  <SwitchRow label="必填" checked />
                  <SwitchRow label="只读" checked={false} onChange={() => message.info('已切换')} />
                </Space>
              </PropertyPanel>
            </div>
          </ComponentEntry>
          <ComponentEntry name="PanelSection + CanvasItemShell" usage="面板分区/画布条目">
            <div style={{ padding: 16, background: tokens.colorBgLayout }}>
              <PanelSection
                title="列配置"
                extra={<Button size="small" type="link">同步表字段</Button>}
              >
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  <CanvasItemShell active>
                    <Typography.Text>选中态条目</Typography.Text>
                  </CanvasItemShell>
                  <CanvasItemShell>
                    <Typography.Text>普通条目</Typography.Text>
                  </CanvasItemShell>
                </Space>
              </PanelSection>
            </div>
          </ComponentEntry>
        </div>
      </div>

      <div>
        <SectionTitle title="色彩令牌" description="取值来自 @daos/ui tokens，业务组件禁止写死色值。" />
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 12 }}>
          <Swatch name="colorPrimary" value={tokens.colorPrimary} />
          <Swatch name="colorPrimaryHover" value={tokens.colorPrimaryHover} />
          <Swatch name="colorPrimaryActive" value={tokens.colorPrimaryActive} />
          <Swatch name="colorPrimaryBg" value={tokens.colorPrimaryBg} />
          <Swatch name="colorSuccess" value={tokens.colorSuccess} />
          <Swatch name="colorWarning" value={tokens.colorWarning} />
          <Swatch name="colorError" value={tokens.colorError} />
          <Swatch name="colorBgLayout" value={tokens.colorBgLayout} />
          <Swatch name="colorBgContainer" value={tokens.colorBgContainer} />
          <Swatch name="colorFillTertiary" value={tokens.colorFillTertiary} />
          <Swatch name="colorTextPrimary" value={tokens.colorTextPrimary} dark />
          <Swatch name="colorTextSecondary" value={tokens.colorTextSecondary} />
          <Swatch name="colorTextTertiary" value={tokens.colorTextTertiary} />
          <Swatch name="colorTextDisabled" value={tokens.colorTextDisabled} />
          <Swatch name="colorBorder" value={tokens.colorBorder} />
        </div>
      </div>

      <div>
        <SectionTitle title="圆角与规格" description="圆角 6 / 8 / 12 三档；高度规格随令牌统一。" />
        <Card styles={{ body: { padding: 20 } }}>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <SpecItem
              label="圆角 radiusSm"
              value={`${tokens.radiusSm}px`}
              preview={<div style={{ width: 64, height: 32, borderRadius: tokens.radiusSm, background: tokens.colorPrimaryBg, border: `1px solid ${tokens.colorPrimary}` }} />}
            />
            <SpecItem
              label="圆角 radiusMd"
              value={`${tokens.radiusMd}px`}
              preview={<div style={{ width: 64, height: 32, borderRadius: tokens.radiusMd, background: tokens.colorPrimaryBg, border: `1px solid ${tokens.colorPrimary}` }} />}
            />
            <SpecItem
              label="圆角 radiusLg"
              value={`${tokens.radiusLg}px`}
              preview={<div style={{ width: 64, height: 32, borderRadius: tokens.radiusLg, background: tokens.colorPrimaryBg, border: `1px solid ${tokens.colorPrimary}` }} />}
            />
            <Divider style={{ margin: 0 }} />
            <SpecItem
              label="控件高度"
              value={`${tokens.controlHeight}px`}
              preview={<Button type="primary">按钮</Button>}
            />
            <SpecItem
              label="顶部栏高"
              value={`${tokens.headerHeight}px`}
              preview={<div style={{ height: 20, borderRadius: tokens.radiusSm, background: tokens.colorFillTertiary, border: `1px solid ${tokens.colorBorder}`, display: 'flex', alignItems: 'center', padding: '0 8px', fontSize: tokens.fontSizeSm, color: tokens.colorTextSecondary }}>Header</div>}
            />
            <SpecItem
              label="工具栏高"
              value={`${tokens.toolbarHeight}px`}
              preview={<div style={{ height: 16, borderRadius: tokens.radiusSm, background: tokens.colorFillTertiary, border: `1px solid ${tokens.colorBorder}`, display: 'flex', alignItems: 'center', padding: '0 8px', fontSize: tokens.fontSizeSm, color: tokens.colorTextSecondary }}>Toolbar</div>}
            />
            <SpecItem
              label="状态区高"
              value={`${tokens.statusBarHeight}px`}
              preview={<div style={{ height: 12, borderRadius: tokens.radiusSm, background: tokens.colorFillTertiary, border: `1px solid ${tokens.colorBorder}`, display: 'flex', alignItems: 'center', padding: '0 8px', fontSize: tokens.fontSizeSm, color: tokens.colorTextSecondary }}>Status</div>}
            />
            <SpecItem
              label="字号"
              value={`${tokens.fontSizeSm} / ${tokens.fontSizeMd} / ${tokens.fontSizeLg} / ${tokens.fontSizeHeading}`}
              preview={
                <Space size={12}>
                  <span style={{ fontSize: tokens.fontSizeSm, color: tokens.colorTextSecondary }}>12 辅助</span>
                  <span style={{ fontSize: tokens.fontSizeMd, color: tokens.colorTextPrimary }}>14 正文</span>
                  <span style={{ fontSize: tokens.fontSizeLg, color: tokens.colorTextPrimary }}>16 强调</span>
                  <span style={{ fontSize: tokens.fontSizeHeading, color: tokens.colorTextPrimary }}>18 标题</span>
                </Space>
              }
            />
          </Space>
        </Card>
      </div>
    </Space>
  );
}
