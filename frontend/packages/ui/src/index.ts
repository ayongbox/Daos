/**
 * 设计令牌唯一来源。业务包禁止写死色值与圆角。
 */
export const tokens = {
  colorPrimary: '#2563EB',
  colorPrimaryHover: '#3B82F6',
  colorPrimaryActive: '#1D4ED8',
  colorPrimaryBg: '#EFF6FF',
  colorSuccess: '#16A34A',
  colorWarning: '#F59E0B',
  colorError: '#EF4444',
  colorBgLayout: '#F5F7FA',
  colorBgContainer: '#FFFFFF',
  colorFillTertiary: '#F5F7FA',
  colorTextPrimary: '#111827',
  colorTextSecondary: '#6B7280',
  colorTextTertiary: '#9CA3AF',
  colorTextDisabled: '#D1D5DB',
  colorTextInverse: '#FFFFFF',
  colorBorder: '#E5E7EB',
  radiusSm: 6,
  radiusMd: 8,
  radiusLg: 12,
  fontSizeSm: 12,
  fontSizeMd: 14,
  fontSizeLg: 16,
  fontSizeHeading: 18,
  controlHeight: 36,
  headerHeight: 56,
  toolbarHeight: 48,
  statusBarHeight: 32,
  siderWidth: 240,
  fontFamily: '-apple-system, "PingFang SC", "Microsoft YaHei", sans-serif',
  shadowSecondary: '0 2px 8px rgba(31,35,41,0.08)'
} as const;

export type UiSize = 'small' | 'middle' | 'large';
export type ThemeTokens = typeof tokens;
