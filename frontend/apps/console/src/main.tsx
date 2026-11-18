import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { App as AntApp, Spin } from 'antd';
import { ThemeProvider } from '@daos/app-shell';
import './app.css';
import { LoginPage } from './LoginPage';
import { ModelerPage } from './ModelerPage';
import { AUTH_EXPIRED_EVENT, api, tokenStore } from './api';

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
        {auth === 'checking' ? (
          <div
            style={{
              minHeight: '100vh',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}
          >
            <Spin />
          </div>
        ) : auth === 'authenticated' ? (
          <ModelerPage onLogout={logout} />
        ) : (
          <LoginPage onLoggedIn={() => setAuth('authenticated')} />
        )}
      </AntApp>
    </ThemeProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Console />
  </StrictMode>
);
