import type { ReactNode } from 'react';
import { ConfigProvider, Layout, Typography } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { tokens } from '@daos/ui';

const { Header, Sider, Content } = Layout;

export interface ThemeProviderProps {
  children?: ReactNode;
}

/**
 * 全局主题：把 UI 规范的令牌一次性灌入 Ant Design，业务组件不再重复配置颜色。
 *
 * <p>主题在应用根装配一次（登录页与页面壳共用），AppShell 只负责布局壳，不再内嵌主题，
 * 避免 ConfigProvider 双层嵌套。</p>
 */
export function ThemeProvider({ children }: ThemeProviderProps) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: tokens.colorPrimary,
          colorPrimaryHover: tokens.colorPrimaryHover,
          colorPrimaryActive: tokens.colorPrimaryActive,
          colorSuccess: tokens.colorSuccess,
          colorWarning: tokens.colorWarning,
          colorError: tokens.colorError,
          colorBgLayout: tokens.colorBgLayout,
          colorBgContainer: tokens.colorBgContainer,
          colorFillTertiary: tokens.colorFillTertiary,
          colorText: tokens.colorTextPrimary,
          colorTextSecondary: tokens.colorTextSecondary,
          colorTextTertiary: tokens.colorTextTertiary,
          colorTextDisabled: tokens.colorTextDisabled,
          colorBorder: tokens.colorBorder,
          colorBorderSecondary: tokens.colorBorder,
          borderRadius: tokens.radiusSm,
          borderRadiusLG: tokens.radiusMd,
          borderRadiusSM: 4,
          colorTextLightSolid: tokens.colorTextInverse,
          fontSize: tokens.fontSizeMd,
          fontSizeSM: tokens.fontSizeSm,
          fontSizeHeading3: tokens.fontSizeHeading,
          controlHeight: tokens.controlHeight,
          fontFamily: tokens.fontFamily,
          boxShadowSecondary: tokens.shadowSecondary
        },
        components: {
          Layout: {
            headerBg: tokens.colorBgContainer,
            headerHeight: tokens.headerHeight,
            headerPadding: '0 16px',
            bodyBg: tokens.colorBgLayout,
            siderBg: tokens.colorBgContainer
          },
          Menu: {
            itemHeight: 36,
            itemBorderRadius: tokens.radiusSm,
            itemSelectedBg: tokens.colorPrimaryBg,
            itemSelectedColor: tokens.colorPrimary,
            itemHoverBg: tokens.colorFillTertiary,
            itemColor: tokens.colorTextPrimary,
            subMenuItemBg: 'transparent'
          },
          Table: {
            headerBg: tokens.colorFillTertiary,
            headerColor: tokens.colorTextSecondary,
            headerSplitColor: 'transparent',
            rowHoverBg: tokens.colorFillTertiary,
            borderColor: tokens.colorBorder,
            cellPaddingBlock: 12,
            cellPaddingInline: 12
          },
          Card: {
            borderRadiusLG: tokens.radiusMd,
            paddingLG: 20
          },
          Button: {
            fontWeight: 500
          },
          Tabs: {
            itemSelectedColor: tokens.colorPrimary,
            inkBarColor: tokens.colorPrimary
          },
          Modal: {
            borderRadiusLG: tokens.radiusLg
          },
          Drawer: {
            borderRadiusLG: tokens.radiusLg
          }
        }
      }}
    >
      {children}
    </ConfigProvider>
  );
}

/** 导航树节点。顶层节点展示在顶部全局栏，完整树展示在左侧应用导航。 */
export interface AppNavNode {
  key: string;
  label: string;
  /** false 表示「规划中」：置灰并带占位标记。 */
  ready?: boolean;
  /** 额外禁用（如未选择应用）：只置灰不显示「规划中」。 */
  disabled?: boolean;
  children?: AppNavNode[];
}

export interface AppShellProps {
  /** 左上角产品标识。 */
  title: ReactNode;
  /** 导航树。缺省或为空时不渲染导航与侧边栏。 */
  nav?: AppNavNode[];
  selectedKey?: string;
  onSelect?: (key: string) => void;
  /** 顶部全局栏右侧内容（应用切换、用户操作等）。 */
  headerExtra?: ReactNode;
  /** 工具栏（48px）内容；缺省不渲染。 */
  toolbar?: ReactNode;
  /** 底部状态区内容；缺省不渲染。 */
  status?: ReactNode;
  children?: ReactNode;
}

function nodeDisabled(node: AppNavNode): boolean {
  return node.ready === false || node.disabled === true;
}

function collectEnabledKeys(node: AppNavNode, out: string[]): void {
  if (node.children && node.children.length > 0) {
    for (const child of node.children) collectEnabledKeys(child, out);
    return;
  }
  if (!nodeDisabled(node)) out.push(node.key);
}

function findTopKey(nav: AppNavNode[], selectedKey?: string): string | undefined {
  for (const node of nav) {
    const keys: string[] = [];
    collectEnabledKeys(node, keys);
    if (node.key === selectedKey || keys.includes(selectedKey ?? '')) return node.key;
  }
  return undefined;
}

/**
 * 控制台与运行时共用的四层布局外壳：
 * 顶部全局栏（56px）→ 左侧应用导航（220~240px）+ 工具栏（48px）→ 底部状态区。
 *
 * <p>顶部全局栏渲染导航树顶层模块，左侧渲染完整分组树，两处联动切换；
 * 认证、请求拦截与错误边界由各应用自行装配，外壳不承担与路由相关的隐式行为；
 * 主题由 ThemeProvider 在应用根装配一次，外壳不内嵌主题，保证壳与主题各只有单一来源。</p>
 */
export function AppShell({
  title,
  nav,
  selectedKey,
  onSelect,
  headerExtra,
  toolbar,
  status,
  children
}: AppShellProps) {
  const hasNav = Boolean(nav && nav.length > 0);
  const activeTopKey = hasNav ? findTopKey(nav ?? [], selectedKey) : undefined;

  function selectNode(node: AppNavNode) {
    if (nodeDisabled(node)) return;
    const keys: string[] = [];
    collectEnabledKeys(node, keys);
    const firstKey = keys[0];
    if (firstKey !== undefined) onSelect?.(firstKey);
  }

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          borderBottom: `1px solid ${tokens.colorBorder}`
        }}
      >
        <Typography.Title level={4} style={{ margin: 0, fontSize: tokens.fontSizeLg, whiteSpace: 'nowrap' }}>
          {title}
        </Typography.Title>
        {hasNav ? (
          <nav style={{ display: 'flex', alignItems: 'center', gap: 4, flex: 1, minWidth: 0, overflow: 'hidden' }}>
            {(nav ?? []).map((node) => {
              const keys: string[] = [];
              collectEnabledKeys(node, keys);
              const disabled = keys.length === 0;
              const active = node.key === activeTopKey;
              return (
                <button
                  key={node.key}
                  type="button"
                  disabled={disabled}
                  onClick={() => selectNode(node)}
                  style={{
                    border: 'none',
                    cursor: disabled ? 'not-allowed' : 'pointer',
                    whiteSpace: 'nowrap',
                    padding: '0 16px',
                    height: 32,
                    borderRadius: tokens.radiusSm,
                    fontFamily: tokens.fontFamily,
                    fontSize: tokens.fontSizeMd,
                    fontWeight: active ? 500 : 400,
                    color: disabled
                      ? tokens.colorTextDisabled
                      : active
                        ? tokens.colorPrimary
                        : tokens.colorTextPrimary,
                    background: active ? tokens.colorPrimaryBg : 'transparent'
                  }}
                >
                  {node.label}
                </button>
              );
            })}
          </nav>
        ) : (
          <div style={{ flex: 1 }} />
        )}
        {headerExtra}
      </Header>
      <Layout style={{ minHeight: 0 }}>
        {hasNav ? (
          <Sider
            theme="light"
            width={tokens.siderWidth}
            style={{ borderRight: `1px solid ${tokens.colorBorder}`, overflow: 'auto', padding: '8px 0' }}
          >
            {(nav ?? []).map((group) =>
              group.children && group.children.length > 0 ? (
                <div key={group.key} style={{ marginBottom: 4 }}>
                  <div
                    style={{
                      padding: '8px 16px 4px',
                      fontSize: tokens.fontSizeSm,
                      color: tokens.colorTextTertiary
                    }}
                  >
                    {group.label}
                  </div>
                  {group.children.map((item) => (
                    <SiderNavItem key={item.key} node={item} active={item.key === selectedKey} onSelect={onSelect} />
                  ))}
                </div>
              ) : (
                <SiderNavItem key={group.key} node={group} active={group.key === selectedKey} onSelect={onSelect} />
              )
            )}
          </Sider>
        ) : null}
        <Layout style={{ minHeight: 0 }}>
          {toolbar ? (
            <div
              style={{
                height: tokens.toolbarHeight,
                display: 'flex',
                alignItems: 'center',
                padding: '0 16px',
                background: tokens.colorBgContainer,
                borderBottom: `1px solid ${tokens.colorBorder}`
              }}
            >
              {toolbar}
            </div>
          ) : null}
          <Content style={{ padding: 16, overflow: 'auto' }}>{children}</Content>
        </Layout>
      </Layout>
      {/* 底部状态区常驻四层壳体：无状态内容时保留 32px 空条，维持布局稳定 */}
      <div
        style={{
          height: tokens.statusBarHeight,
          display: 'flex',
          alignItems: 'center',
          gap: 16,
          padding: '0 16px',
          background: tokens.colorBgContainer,
          borderTop: `1px solid ${tokens.colorBorder}`,
          fontSize: tokens.fontSizeSm,
          color: tokens.colorTextSecondary
        }}
      >
        {status}
      </div>
    </Layout>
  );
}

function SiderNavItem({
  node,
  active,
  onSelect
}: {
  node: AppNavNode;
  active: boolean;
  onSelect?: (key: string) => void;
}) {
  const disabled = nodeDisabled(node);
  return (
    <div
      onClick={() => {
        if (!disabled) onSelect?.(node.key);
      }}
      style={{
        margin: '2px 8px',
        padding: '0 10px',
        height: 36,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        borderRadius: tokens.radiusSm,
        cursor: disabled ? 'not-allowed' : 'pointer',
        fontSize: tokens.fontSizeMd,
        color: disabled ? tokens.colorTextDisabled : active ? tokens.colorPrimary : tokens.colorTextPrimary,
        background: active ? tokens.colorPrimaryBg : 'transparent'
      }}
    >
      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{node.label}</span>
      {node.ready === false ? (
        <span style={{ fontSize: tokens.fontSizeSm, color: tokens.colorTextTertiary }}>规划中</span>
      ) : null}
    </div>
  );
}
