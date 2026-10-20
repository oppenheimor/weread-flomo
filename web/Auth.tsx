import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { loadSession, login, logout, register } from './api.ts';
import type { Session } from './types.ts';
import { App } from './App.tsx';
import { ThemeToggle } from './ThemeToggle.tsx';

export function Auth() {
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [confirmation, setConfirmation] = useState('');
  const [notice, setNotice] = useState('');
  function switchMode() {
    setRegistering(value => !value); setPassword(''); setConfirmation(''); setError(''); setNotice('');
  }
  async function check() {
    setError('');
    try { setSession(await loadSession()); }
    catch { setError('无法连接登录服务，请检查 Java 后端是否启动。'); }
  }
  useEffect(() => {
    void check();
    const expired = () => { setSession(null); setPassword(''); void check(); };
    window.addEventListener('auth-expired', expired);
    return () => window.removeEventListener('auth-expired', expired);
  }, []);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setNotice('');
    if (registering && password !== confirmation) { setError('两次密码不一致'); return; }
    if (registering && new TextEncoder().encode(password).length > 72) { setError('密码最多 72 个 UTF-8 字节，请缩短密码'); return; }
    setBusy(true);
    try {
      if (registering) {
        await register(username, password);
        setRegistering(false); setConfirmation(''); setNotice('注册成功，请登录');
      } else setSession(await login(username, password));
      setPassword('');
    }
    catch (e) { setError(e instanceof Error ? e.message : '操作失败'); }
    finally { setBusy(false); }
  }
  async function exit() {
    setBusy(true); setError('');
    try { await logout(); setSession(null); await check(); }
    catch (e) { setError(e instanceof Error ? e.message : '退出失败'); }
    finally { setBusy(false); }
  }
  if (session?.authenticated) return <>
    {error && <div className="notice error banner" role="alert">{error}</div>}
    <App username={session.username} loggingOut={busy} onLogout={exit} />
  </>;
  return <main className="login-scene">
    <div className="login-theme"><ThemeToggle /></div>
    <div className="login-corner" aria-hidden="true" />
    <section className="login-page" aria-labelledby="login-title">
    <h1 id="login-title" className="brand"><img src={`${import.meta.env.BASE_URL}assets/brand/shimo-logo.png`} width="64" height="64" alt="" draggable={false} /><span>拾墨</span></h1>
    <p className="hint">拾起阅读片段，把微信读书笔记整理进 flomo。</p>
    {error && <div className="notice error" role="alert">{error}</div>}
    {notice && <p className="hint success-text" role="status">{notice}</p>}
    {!session ? error ? <p className="hint">服务恢复后，请刷新页面。</p> : <div className="login-loading" role="status">
      <span className="loading-book" aria-hidden="true" />
      <span aria-hidden="true">翻开书页，拾起微光…</span>
      <span className="sr-only">正在加载，请稍候。</span>
    </div> : <form onSubmit={submit}>
      <label htmlFor="username">账号</label><input id="username" autoComplete="username" required maxLength={64} pattern={registering ? '[A-Za-z0-9_.\\-]{3,64}' : undefined} aria-describedby={registering ? 'username-rule' : undefined} value={username} onChange={e => setUsername(e.target.value)} disabled={busy} />
      {registering && <p id="username-rule" className="hint">3–64 位字母、数字、下划线、点或短横线</p>}
      <label htmlFor="password">密码</label><input id="password" type="password" autoComplete={registering ? 'new-password' : 'current-password'} required maxLength={72} value={password} onChange={e => setPassword(e.target.value)} disabled={busy} />
      {registering && <><label htmlFor="confirm-password">确认密码</label><input id="confirm-password" type="password" autoComplete="new-password" required maxLength={72} value={confirmation} onChange={e => setConfirmation(e.target.value)} disabled={busy} /></>}
      <button className="primary" disabled={busy}>{busy ? (registering ? '正在注册…' : '正在登录…') : registering ? '注册' : '登录'}</button>
    </form>}
    {session && <button type="button" className="text-button auth-switch" disabled={busy} onClick={switchMode}>{registering ? '已有账号，去登录' : '注册新账号'}</button>}
    </section>
  </main>;
}
