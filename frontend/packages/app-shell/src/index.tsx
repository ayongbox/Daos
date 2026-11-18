import type { ReactNode } from 'react';
import { ConfigProvider, Layout, Menu, Typography } from 'antd';
import type { MenuProps } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { tokens } from '@daos/ui';

const { Header, Sider, Content } = Layout;

export interface ThemeProviderProps {
  children?: ReactNode;
}

/**
 * 全局主题：把 UI 规范的令牌一次性灌入 Ant Design，业务组件不再重复配置颜色。
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

export interface AppShellProps {
  /** 应用标题，渲染在头部左侧。 */
  title: string;
  /** 侧边菜单项。缺省或为空时不渲染侧边栏。 */
  menuItems?: MenuProps['items'];
  defaultSelectedKey?: string;
  /** 头部右侧附加内容。 */
  headerExtra?: ReactNode;
  children?: ReactNode;
}

/**
 * 控制台与运行时共用的外壳：统一主题令牌、头部与侧边导航。
 *
 * <p>只负责布局。认证、请求拦截与错误边界由各应用自行装配，
 * 避免外壳承担与路由相关的隐式行为。</p>
 */
export function AppShell({ title, menuItems, defaultSelectedKey, headerExtra, children }: AppShellProps) {
  const hasSider = Boolean(menuItems && menuItems.length > 0);

  return (
    <ThemeProvider>
      <Layout style={{ minHeight: '100vh' }}>
        <Header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: `1px solid ${tokens.colorBorder}`
          }}
        >
          <Typography.Title level={4} style={{ margin: 0, fontSize: tokens.fontSizeLg }}>
            {title}
          </Typography.Title>
          {headerExtra}
        </Header>
        <Layout>
          {hasSider ? (
            <Sider theme="light" width={tokens.siderWidth} style={{ borderRight: `1px solid ${tokens.colorBorder}` }}>
              <Menu
                mode="inline"
                defaultSelectedKeys={defaultSelectedKey ? [defaultSelectedKey] : []}
                items={menuItems ?? []}
              />
            </Sider>
          ) : null}
          <Content style={{ padding: 24 }}>{children}</Content>
        </Layout>
      </Layout>
    </ThemeProvider>
  );
}
