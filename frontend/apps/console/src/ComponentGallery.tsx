import { useState, type ReactNode } from 'react';
import { App as AntApp, Button, Card, Divider, Space, Tag, Typography } from 'antd';
import {
  EmptyState,
  ErrorState,
  ForbiddenState,
  SkeletonCard,
  SkeletonTable,
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
