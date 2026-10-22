import type { Session } from './types.ts';
import { clearMagpieKey } from './magpie-transport.ts';

let csrfToken = '';
export async function loadSession(): Promise<Session> {
  const session = await request<Session>('/api/auth/session');
  csrfToken = session.csrfToken;
  return session;
}
export async function login(username: string, password: string): Promise<Session> {
  clearMagpieKey();
  const response = await fetch(`${import.meta.env.BASE_URL}api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': csrfToken },
    body: new URLSearchParams({ username, password }),
  });
  if (!response.ok) {
    const result = await response.json().catch(() => ({})) as { error?: string };
    throw new Error(result.error ?? '登录失败，请刷新后重试');
  }
  return loadSession();
}
export async function register(username: string, password: string): Promise<void> {
  await request('/api/auth/register', { username, password });
}
export async function logout(): Promise<void> {
  await request('/api/auth/logout', {});
  csrfToken = '';
  clearMagpieKey();
}
let queue: Promise<unknown> = Promise.resolve();
/** 后端读取、发送、凭证变更共用一把任务锁；前端排队执行，避免快速切书时收到 409。 */
export function exclusive<T>(task: () => Promise<T>): Promise<T> {
  const run = queue.then(task, task);
  queue = run.catch(() => undefined);
  return run;
}
export async function request<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${import.meta.env.BASE_URL}${path.replace(/^\//, '')}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers: body === undefined ? {} : { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
      body: body === undefined ? undefined : JSON.stringify(body), signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new Error('无法连接服务。若刚才正在发送，请重新拉取核对状态，不要直接重发。');
  }
  if (response.status === 401) { clearMagpieKey(); window.dispatchEvent(new Event('auth-expired')); }
  if (response.status === 204) return undefined as T;
  let data: unknown;
  try { data = await response.json(); } catch { throw new Error('后端未正常响应，请检查 Java 服务状态。'); }
  if (!response.ok) {
    const message = data && typeof data === 'object' && 'error' in data && typeof data.error === 'string' ? data.error : '请求失败，请稍后重试。';
    throw new Error(message);
  }
  return data as T;
}
