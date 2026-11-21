import type { CSSProperties, ReactNode } from 'react';
import { Card, Drawer, Typography, type DrawerProps } from 'antd';
import { tokens } from './index';

/**
 * 通用业务组件：各页面反复出现的布局模式（页头 / 工具行 / 详情抽屉 / 统计卡）。
 * 只消费 tokens 与 antd，替换 console 各页自拼的等价版式，禁止写死色值与圆角。
 */

export interface PageHeaderProps {
  title: ReactNode;
  /** 标题下方的一句说明。 */
  description?: ReactNode;
  /** 标题行右侧操作区。 */
  extra?: ReactNode;
  style?: CSSProperties;
}

/** 页头：标题 + 一句描述（+ 右侧操作），统一各列表/配置页顶部版式。 */
export function PageHeader({ title, description, extra, style }: PageHeaderProps) {
  return (
    <div style={style}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <Typography.Title level={4} style={{ margin: 0, fontSize: tokens.fontSizeHeading }}>
          {title}
        </Typography.Title>
        {extra ? <div style={{ marginLeft: 'auto' }}>{extra}</div> : null}
      </div>
      {description ? <Typography.Text type="secondary">{description}</Typography.Text> : null}
    </div>
  );
}

export interface ToolbarRowProps {
  /** 左侧信息区（统计、说明）。 */
  info?: ReactNode;
  /** 右侧操作区。 */
  actions?: ReactNode;
  style?: CSSProperties;
}

/** 工具行：左信息 + 右操作，用于列表/面板顶部的统计与主操作。 */
export function ToolbarRow({ info, actions, style }: ToolbarRowProps) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, ...style }}>
      <div style={{ flex: 1, minWidth: 0 }}>{info}</div>
      {actions ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{actions}</div>
      ) : null}
    </div>
  );
}

export interface StatItem {
  key?: string;
  label: ReactNode;
  value: ReactNode;
  hint?: ReactNode;
  /** 提供时统计卡可点击（如跳转到对应模块）。 */
  onClick?: () => void;
}

export interface StatCardsProps {
  items: StatItem[];
  /** 单卡最小宽度，决定自适应列数。 */
  minWidth?: number;
  gap?: number;
  style?: CSSProperties;
}

/** 统计卡组：标签 + 数值 + 提示的自适应卡片网格，替代各页自拼的指标卡。 */
export function StatCards({ items, minWidth = 160, gap = 12, style }: StatCardsProps) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(auto-fit, minmax(${minWidth}px, 1fr))`,
        gap,
        ...style
      }}
    >
      {items.map((item) => (
        <Card
          key={item.key ?? String(item.label)}
          size="small"
          hoverable={Boolean(item.onClick)}
          onClick={item.onClick}
          styles={{ body: { padding: '14px 16px' } }}
        >
          <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
            {item.label}
          </Typography.Text>
          <div style={{ fontSize: 26, fontWeight: 600, lineHeight: '34px' }}>{item.value}</div>
          {item.hint ? (
            <Typography.Text type="secondary" style={{ fontSize: tokens.fontSizeSm }}>
              {item.hint}
            </Typography.Text>
          ) : null}
        </Card>
      ))}
    </div>
  );
}

/** 详情抽屉：统一响应式宽度与关闭即销毁行为，用于记录详情、字段配置等侧滑场景。 */
export function DetailDrawer({
  width = 'min(720px, calc(100vw - 48px))',
  destroyOnHidden = true,
  ...rest
}: DrawerProps) {
  return <Drawer width={width} destroyOnHidden={destroyOnHidden} {...rest} />;
}
