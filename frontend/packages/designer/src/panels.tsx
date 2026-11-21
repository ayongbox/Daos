import type { CSSProperties, ReactNode } from 'react';
import { Card, Space, Switch, Typography } from 'antd';
import { tokens } from '@daos/ui';

export interface PropertyPanelProps {
  title?: ReactNode;
  /** 面板宽度，默认 280（右侧属性栏常用值）。 */
  width?: number;
  style?: CSSProperties;
  children?: ReactNode;
}

/** 属性面板容器：设计器右侧配置栏的统一卡片壳。 */
export function PropertyPanel({ title = '属性', width = 280, style, children }: PropertyPanelProps) {
  return (
    <Card size="small" title={title} style={{ width, flex: `0 0 ${width}px`, ...style }}>
      {children}
    </Card>
  );
}

export interface PanelFieldProps {
  label: ReactNode;
  /** 标签与控件的排布：top 为上下两行，inline 为左标签右控件。 */
  layout?: 'top' | 'inline';
  /** inline 布局下的标签列宽度，默认 120。 */
  labelWidth?: number;
  children: ReactNode;
}

/** 属性面板表单项：统一小号次要标签与控件间距。 */
export function PanelField({ label, layout = 'top', labelWidth = 120, children }: PanelFieldProps) {
  if (layout === 'inline') {
    return (
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `${labelWidth}px 1fr`,
          gap: 12,
          alignItems: 'center'
        }}
      >
        <Typography.Text type="secondary">{label}</Typography.Text>
        {children}
      </div>
    );
  }
  return (
    <div>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {label}
      </Typography.Text>
      <div style={{ marginTop: 4 }}>{children}</div>
    </div>
  );
}

export interface SwitchRowProps {
  label: ReactNode;
  checked?: boolean;
  disabled?: boolean;
  onChange?: (checked: boolean) => void;
}

/** 开关行：左标签右 Switch 的单行配置项。 */
export function SwitchRow({ label, checked, disabled, onChange }: SwitchRowProps) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
      <span style={{ fontSize: 13 }}>{label}</span>
      <Switch size="small" checked={checked} disabled={disabled} onChange={onChange} />
    </div>
  );
}

export interface PanelSectionProps {
  title: ReactNode;
  /** 标题行右侧操作（如「同步字段」链接按钮）。 */
  extra?: ReactNode;
  style?: CSSProperties;
  children?: ReactNode;
}

/** 面板分区：小标题 + 操作 + 内容，统一各设计器的分区节奏。 */
export function PanelSection({ title, extra, style, children }: PanelSectionProps) {
  return (
    <div style={style}>
      <Space style={{ marginBottom: 8 }}>
        <Typography.Text strong>{title}</Typography.Text>
        {extra}
      </Space>
      {children}
    </div>
  );
}

export interface CanvasItemShellProps {
  active?: boolean;
  padding?: string;
  radius?: number;
  onClick?: () => void;
  children: ReactNode;
}

/** 画布条目外壳：选中高亮边框/背景的统一容器，替代各设计器自拼选中态。 */
export function CanvasItemShell({
  active = false,
  padding = '10px 12px',
  radius = tokens.radiusSm,
  onClick,
  children
}: CanvasItemShellProps) {
  return (
    <div
      onClick={onClick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding,
        borderRadius: radius,
        border: `1px solid ${active ? tokens.colorPrimary : tokens.colorBorder}`,
        background: active ? tokens.colorPrimaryBg : tokens.colorBgContainer,
        cursor: onClick ? 'pointer' : undefined
      }}
    >
      {children}
    </div>
  );
}
