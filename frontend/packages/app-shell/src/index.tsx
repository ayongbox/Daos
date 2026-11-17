import type { ReactNode } from 'react';
import { ConfigProvider, Layout, Menu, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { tokens } from '@daos/ui';

const { Header, Sider, Content } = Layout;

export interface AppShellProps {
  /** 应用标题，渲染在头部左侧。 */
  title: string;
  /** 侧边菜单项。缺省或为空时不渲染侧边栏。 */
  menuItems?: MenuProps['items'];
  defaultSelectedKey?: string;
  children?: ReactNode;
}

/**
 * 控制台与运行时共用的外壳：统一主题令牌、头部与侧边导航。
 *
 * <p>只负责布局。认证、请求拦截与错误边界由各应用自行装配，
 * 避免外壳承担与路由相关的隐式行为。</p>
 */
export function AppShell({ title, menuItems, defaultSelectedKey, children }: AppShellProps) {
  const hasSider = Boolean(menuItems && menuItems.length > 0);

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: tokens.colorPrimary,
          colorBgLayout: tokens.colorBgLayout,
          borderRadius: tokens.radiusMd
        }
      }}
    >
      <Layout style={{ minHeight: '100vh' }}>
        <Header
          style={{
            background: tokens.colorBgContainer,
            display: 'flex',
            alignItems: 'center'
          }}
        >
          <Typography.Title level={4} style={{ margin: 0, color: tokens.colorTextPrimary }}>
            {title}
          </Typography.Title>
        </Header>
        <Layout>
          {hasSider ? (
            <Sider theme="light" width={220}>
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
    </ConfigProvider>
  );
}
