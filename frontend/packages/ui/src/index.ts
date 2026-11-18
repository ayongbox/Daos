/**
 * 设计令牌唯一来源。业务包禁止写死色值与圆角。
 */
export const tokens = {
  colorPrimary: '#3370FF',
  colorPrimaryHover: '#4E83FD',
  colorPrimaryActive: '#245BDB',
  colorPrimaryBg: '#E1EAFF',
  colorSuccess: '#34C724',
  colorWarning: '#FF8800',
  colorError: '#F54A45',
  colorBgLayout: '#F5F6F7',
  colorBgContainer: '#FFFFFF',
  colorFillTertiary: '#F5F6F7',
  colorTextPrimary: '#1F2329',
  colorTextSecondary: '#646A73',
  colorTextTertiary: '#8F959E',
  colorTextDisabled: '#BBBFC4',
  colorBorder: '#DEE0E3',
  radiusSm: 6,
  radiusMd: 8,
  radiusLg: 10,
  fontSizeSm: 12,
  fontSizeMd: 14,
  fontSizeLg: 16,
  fontSizeHeading: 18,
  controlHeight: 32,
  headerHeight: 48,
  siderWidth: 240,
  fontFamily: '-apple-system, "PingFang SC", "Microsoft YaHei", sans-serif',
  shadowSecondary: '0 2px 8px rgba(31,35,41,0.08)'
} as const;

export type UiSize = 'small' | 'middle' | 'large';
export type ThemeTokens = typeof tokens;
