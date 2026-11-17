/**
 * 设计令牌唯一来源。业务包禁止写死色值与圆角。
 */
export const tokens = {
  colorPrimary: '#3370FF',
  colorBgLayout: '#F5F6F7',
  colorBgContainer: '#FFFFFF',
  colorTextPrimary: '#1F2329',
  colorTextSecondary: '#646A73',
  colorBorder: '#DEE0E3',
  colorSuccess: '#34C724',
  colorWarning: '#FF8800',
  colorError: '#F54A45',
  radiusSm: 6,
  radiusMd: 8,
  radiusLg: 10,
  fontSizeSm: 12,
  fontSizeMd: 14,
  fontSizeLg: 16
} as const;

export type UiSize = 'small' | 'middle' | 'large';
export type ThemeTokens = typeof tokens;
