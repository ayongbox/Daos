import { Button, Card, Divider, Layout, Tabs, Typography } from 'antd';
import { tokens } from '@daos/ui';
import { AdminMembers } from './AdminMembers';
import { AdminDepartments } from './AdminDepartments';
import { AdminDictionaries } from './AdminDictionaries';

const { Header, Content } = Layout;

/** 管理后台：用户、组织与数据字典的统一管理入口。 */
export function AdminPage({ onBack }: { onBack: () => void }) {
  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '0 16px',
          background: tokens.colorBgContainer,
          borderBottom: `1px solid ${tokens.colorBorder}`
        }}
      >
        <Typography.Text strong style={{ fontSize: tokens.fontSizeLg }}>
          管理后台
        </Typography.Text>
        <Divider type="vertical" style={{ margin: 0 }} />
        <Typography.Text type="secondary">用户、组织与数据字典</Typography.Text>
        <div style={{ flex: 1 }} />
        <Button type="primary" onClick={onBack}>
          返回控制台
        </Button>
      </Header>
      <Content style={{ padding: 16, overflow: 'auto' }}>
        <Card>
          <Tabs
            defaultActiveKey="members"
            items={[
              { key: 'members', label: '用户管理', children: <AdminMembers /> },
              { key: 'departments', label: '组织管理', children: <AdminDepartments /> },
              { key: 'dictionaries', label: '字典管理', children: <AdminDictionaries /> }
            ]}
          />
        </Card>
      </Content>
    </Layout>
  );
}
