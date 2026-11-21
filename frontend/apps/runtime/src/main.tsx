import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Empty } from 'antd';
import { AppShell, ThemeProvider } from '@daos/app-shell';
import type { RenderTree } from '@daos/renderer';

const placeholderTree: RenderTree = {
  formId: '',
  scene: 'VIEW',
  schemaVersion: 0,
  nodes: [],
  props: {}
};

function App() {
  return (
    <ThemeProvider>
      <AppShell title="道生 · 运行时">
        {placeholderTree.nodes.length === 0 ? (
          <Empty description="等待渲染树：运行时只消费后端编译产物" />
        ) : null}
      </AppShell>
    </ThemeProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
