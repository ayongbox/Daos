import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Typography } from 'antd';
import { AppShell } from '@daos/app-shell';

const menuItems = [
  { key: 'apps', label: '应用管理' },
  { key: 'model', label: '数据建模' },
  { key: 'form', label: '表单设计' },
  { key: 'flow', label: '流程设计' },
  { key: 'permission', label: '权限配置' }
];

function App() {
  return (
    <AppShell title="道生 · 控制台" menuItems={menuItems} defaultSelectedKey="apps">
      <Typography.Paragraph type="secondary">
        元数据 Schema v1 已冻结：表、字段、表单、权限四类契约是设计器与运行时的唯一来源。
      </Typography.Paragraph>
    </AppShell>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
