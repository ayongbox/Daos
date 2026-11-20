import { useState } from 'react';
import { Button, Card, Form, Input, Typography } from 'antd';
import { tokens } from '@daos/ui';
import { api, tokenStore } from './api';

interface LoginValues {
  tenantId: string;
  username: string;
  password: string;
}

export function LoginPage({ onLoggedIn }: { onLoggedIn: () => void }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (values: LoginValues) => {
    setLoading(true);
    setError(null);
    try {
      const result = await api.login(values.tenantId, values.username, values.password);
      tokenStore.set(result.token);
      onLoggedIn();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        background: tokens.colorBgLayout,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24
      }}
    >
      <div style={{ width: 400 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
          <div
            style={{
              width: 40,
              height: 40,
              borderRadius: tokens.radiusMd,
              background: tokens.colorPrimary,
              color: tokens.colorTextInverse,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 20,
              fontWeight: 600
            }}
          >
            道
          </div>
          <div>
            <Typography.Text strong style={{ fontSize: tokens.fontSizeHeading }}>
              道生
            </Typography.Text>
            <div style={{ color: tokens.colorTextSecondary, fontSize: tokens.fontSizeSm }}>低代码应用平台</div>
          </div>
        </div>

        <Card style={{ boxShadow: tokens.shadowSecondary }} styles={{ body: { padding: 32 } }}>
          <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 4, fontSize: tokens.fontSizeHeading }}>
            登录控制台
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 24 }}>
            使用组织租户与账号登录
          </Typography.Paragraph>

          <Form<LoginValues> layout="vertical" onFinish={submit} requiredMark={false}>
            <Form.Item name="tenantId" label="租户" rules={[{ required: true, message: '请输入租户' }]}>
              <Input placeholder="tenant code" autoComplete="organization" />
            </Form.Item>
            <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
              <Input placeholder="username" autoComplete="username" />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            {error ? (
              <Typography.Paragraph style={{ color: tokens.colorError, marginBottom: 16 }}>{error}</Typography.Paragraph>
            ) : null}
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form>
        </Card>

        <div
          style={{
            marginTop: 16,
            textAlign: 'center',
            color: tokens.colorTextTertiary,
            fontSize: tokens.fontSizeSm
          }}
        >
          账号由组织管理员分配，遇到问题请联系管理员
        </div>
      </div>
    </div>
  );
}
