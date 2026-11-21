import type { CSSProperties, ReactNode } from 'react';
import { Button, Skeleton } from 'antd';
import { tokens } from './index';

/**
 * 四态基础组件：加载（骨架屏）、空态、错误态、无权限。
 * 只消费 tokens，不写死色值与圆角；图标为 1.5 线宽自建线性图标，不复用品牌图形。
 */

/** 通用线性图标容器：1.5 线宽、24px，颜色随所在状态取次级文字色。 */
function StateIcon({ children, color = tokens.colorTextTertiary }: { children: ReactNode; color?: string }) {
  return (
    <div
      style={{
        width: 56,
        height: 56,
        borderRadius: '50%',
        background: tokens.colorFillTertiary,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color
      }}
    >
      <svg width={24} height={24} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={1.5} strokeLinecap="round" strokeLinejoin="round">
        {children}
      </svg>
    </div>
  );
}

/** 空态图标：空盒子。 */
function EmptyGlyph() {
  return (
    <>
      <path d="M3 8.5 12 4l9 4.5v7L12 20l-9-4.5v-7Z" />
      <path d="M3 8.5 12 13l9-4.5" />
      <path d="M12 13v7" />
    </>
  );
}

/** 错误态图标：感叹号圆圈。 */
function ErrorGlyph() {
  return (
    <>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 8v4.5" />
      <path d="M12 16h.01" />
    </>
  );
}

/** 无权限图标：锁。 */
function LockGlyph() {
  return (
    <>
      <rect x="5" y="10.5" width="14" height="9.5" rx="2" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
    </>
  );
}

/** 状态视图统一版式：轻量图标 + 标题 + 一句描述 + 操作区；compact 用于卡片内嵌场景。 */
function StateView({
  icon,
  title,
  description,
  action,
  compact = false
}: {
  icon: ReactNode;
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  compact?: boolean;
}) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: compact ? 8 : 12,
        padding: compact ? '24px 16px' : '48px 24px',
        textAlign: 'center'
      }}
    >
      {icon}
      <div style={{ fontSize: tokens.fontSizeMd, fontWeight: 500, color: tokens.colorTextPrimary }}>{title}</div>
      {description ? (
        <div style={{ fontSize: tokens.fontSizeSm, color: tokens.colorTextSecondary, maxWidth: 320, lineHeight: 1.6 }}>
          {description}
        </div>
      ) : null}
      {action ? <div style={{ marginTop: 4 }}>{action}</div> : null}
    </div>
  );
}

/** 骨架屏闪烁块：统一用 antd Skeleton.Node 保证动效一致（150-200ms 过渡口径由 antd 主题承担）。 */
function Shimmer({ width, height, style }: { width: number | string; height: number; style?: CSSProperties }) {
  return <Skeleton.Node active style={{ width, height, ...style }} />;
}

export interface SkeletonCardProps {
  /** 标题下方的骨架行数。 */
  rows?: number;
  /** 是否渲染头像位（用于带头像的卡片骨架）。 */
  avatar?: boolean;
  style?: CSSProperties;
}

/** 卡片骨架屏：替代卡片区域的全屏 Spin，保持卡片版式稳定。 */
export function SkeletonCard({ rows = 3, avatar = false, style }: SkeletonCardProps) {
  return (
    <div
      style={{
        background: tokens.colorBgContainer,
        border: `1px solid ${tokens.colorBorder}`,
        borderRadius: tokens.radiusMd,
        padding: 20,
        ...style
      }}
    >
      {avatar ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
          <Shimmer width={40} height={40} style={{ borderRadius: '50%' }} />
          <Shimmer width="40%" height={16} />
        </div>
      ) : null}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {Array.from({ length: rows }).map((_, index) => (
          <Shimmer key={index} width={index === rows - 1 ? '60%' : '100%'} height={12} />
        ))}
      </div>
    </div>
  );
}

export interface SkeletonTableProps {
  /** 数据行骨架数。 */
  rows?: number;
  /** 列数。 */
  columns?: number;
  style?: CSSProperties;
}

/** 表格骨架屏：表头底色 + 行骨架，行高 48 与表格规范一致。 */
export function SkeletonTable({ rows = 5, columns = 4, style }: SkeletonTableProps) {
  const columnWidth = `${Math.floor(100 / columns)}%`;
  return (
    <div
      style={{
        background: tokens.colorBgContainer,
        border: `1px solid ${tokens.colorBorder}`,
        borderRadius: tokens.radiusMd,
        overflow: 'hidden',
        ...style
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          height: 40,
          padding: '0 12px',
          background: tokens.colorFillTertiary,
          gap: 12
        }}
      >
        {Array.from({ length: columns }).map((_, index) => (
          <Shimmer key={index} width={columnWidth} height={10} />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div
          key={rowIndex}
          style={{
            display: 'flex',
            alignItems: 'center',
            height: 48,
            padding: '0 12px',
            gap: 12,
            borderTop: rowIndex > 0 ? `1px solid ${tokens.colorBorder}` : 'none'
          }}
        >
          {Array.from({ length: columns }).map((_, colIndex) => (
            <Shimmer key={colIndex} width={columnWidth} height={12} />
          ))}
        </div>
      ))}
    </div>
  );
}

export interface EmptyStateProps {
  title?: string;
  description?: ReactNode;
  /** 操作区（如「新建」按钮），由调用方传入以复用 antd 按钮。 */
  action?: ReactNode;
  /** 紧凑版式：用于卡片内嵌空态。 */
  compact?: boolean;
}

/** 空态：轻量图标 + 一句引导 + 操作，不使用大面积色块。 */
export function EmptyState({ title = '暂无数据', description, action, compact = false }: EmptyStateProps) {
  return (
    <StateView
      compact={compact}
      icon={
        <StateIcon>
          <EmptyGlyph />
        </StateIcon>
      }
      title={title}
      description={description}
      action={action}
    />
  );
}

export interface ErrorStateProps {
  title?: string;
  description?: ReactNode;
  /** 重试回调；提供时渲染「重试」按钮。 */
  onRetry?: () => void;
  retryText?: string;
}

/** 错误态：描述 + 重试，替代散落的 message.error 占位。 */
export function ErrorState({ title = '加载失败', description, onRetry, retryText = '重试' }: ErrorStateProps) {
  return (
    <StateView
      icon={
        <StateIcon color={tokens.colorError}>
          <ErrorGlyph />
        </StateIcon>
      }
      title={title}
      description={description}
      action={
        onRetry ? (
          <Button onClick={onRetry}>{retryText}</Button>
        ) : undefined
      }
    />
  );
}

export interface ForbiddenStateProps {
  title?: string;
  description?: ReactNode;
  /** 补充操作（如「申请权限」「返回」），由调用方传入。 */
  action?: ReactNode;
}

/** 无权限态：独立于错误态，避免把权限问题误报为加载失败。 */
export function ForbiddenState({ title = '无权访问', description, action }: ForbiddenStateProps) {
  return (
    <StateView
      icon={
        <StateIcon>
          <LockGlyph />
        </StateIcon>
      }
      title={title}
      description={description}
      action={action}
    />
  );
}
