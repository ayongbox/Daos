import { StrictMode, Suspense, lazy, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { App as AntApp } from 'antd';
import { ThemeProvider } from '@daos/app-shell';
import { SkeletonCard } from '@daos/ui';
import './app.css';
import { AUTH_EXPIRED_EVENT, api, tokenStore } from './api';

const LoginPage = lazy(() =>
  import('./LoginPage').then((module) => ({ default: module.LoginPage }))
);
const ModelerPage = lazy(() =>
  import('./ModelerPage').then((module) => ({ default: module.ModelerPage }))
);

/** 应用启动/懒加载占位：骨架屏保持页面版式稳定，替代全屏 Spin。 */
function loadingShell() {
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24
      }}
    >
      <div style={{ width: 'min(720px, 100%)' }}>
        <SkeletonCard avatar rows={4} />
      </div>
    </div>
  );
}

type AuthState = 'checking' | 'authenticated' | 'anonymous';

function Console() {
  const [auth, setAuth] = useState<AuthState>(() =>
    tokenStore.get() ? 'checking' : 'anonymous'
  );

  useEffect(() => {
    const expire = () => {
      tokenStore.clear();
      setAuth('anonymous');
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, expire);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, expire);
  }, []);

  useEffect(() => {
    if (auth !== 'checking') return;
    let cancelled = false;
    api
      .me()
      .then(() => {
        if (!cancelled) setAuth('authenticated');
      })
      .catch(() => {
        if (cancelled) return;
        tokenStore.clear();
        setAuth('anonymous');
      });
    return () => {
      cancelled = true;
    };
  }, [auth]);

  const logout = async () => {
    try {
      await api.logout();
    } catch {
      // 会话可能已过期，本地令牌仍需清除
    } finally {
      tokenStore.clear();
      setAuth('anonymous');
    }
  };

  return (
    <ThemeProvider>
      <AntApp>
        <Suspense fallback={loadingShell()}>
          {auth === 'checking' ? (
            loadingShell()
          ) : auth === 'authenticated' ? (
            <ModelerPage onLogout={logout} />
          ) : (
            <LoginPage onLoggedIn={() => setAuth('authenticated')} />
          )}
        </Suspense>
      </AntApp>
    </ThemeProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Console />
  </StrictMode>
);
