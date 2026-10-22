import { request } from './api.ts';

// No browser persistence or shared key cache; invalidate pending reads on account changes.
let accountVersion = 0;
export function clearMagpieKey() { accountVersion++; }
export function magpieUrl(base: string) {
  const url = new URL(base);
  if (!['http:', 'https:'].includes(url.protocol) || !['localhost', '127.0.0.1'].includes(url.hostname) || url.username || url.password || url.search || url.hash || url.pathname.replace(/\/+$/, '') !== '/v1') {
    throw new Error('Magpie 请使用本机地址 http://localhost:3425/v1 或 http://127.0.0.1:3425/v1');
  }
  return url.href.replace(/\/+$/, '');
}
async function call(base: string, path: string, body?: unknown, signal?: AbortSignal) {
  const url = magpieUrl(base);
  const version = accountVersion;
  const { key: gatewayKey } = await request<{ key: string }>('/api/ai/magpie/key', {}, signal);
  if (version !== accountVersion) throw new Error('账号状态已变化，请重新操作');
  if (!gatewayKey) throw new Error('请先在模型设置中填写并保存 Magpie 网关密钥');
  let response: Response;
  try {
    response = await fetch(url + path, { method: body === undefined ? 'GET' : 'POST', headers: { ...(gatewayKey ? { Authorization: `Bearer ${gatewayKey}` } : {}), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) }, body: body === undefined ? undefined : JSON.stringify(body), credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer', signal });
  } catch {
    throw new Error(signal?.aborted ? '本地模型请求已取消或超时' : `无法连接本机 Magpie。请启动网关，在 Settings → Network and sharing → Web pages 中允许 ${location.origin}，并允许浏览器访问本地网络。`);
  }
  if (!response.ok) throw new Error(response.status === 401 ? '当前 Magpie 网关要求认证，请填写有效 Gateway key' : response.status === 403 ? 'Magpie 拒绝访问：请检查网页来源授权、模型权限或网关认证设置' : `Magpie 请求失败（HTTP ${response.status}）`);
  return response;
}
export async function magpieModels(base: string) {
  const response = await call(base, '/models', undefined, AbortSignal.timeout(15000));
  const data = await response.json() as { data?: { id?: unknown }[] };
  if (!Array.isArray(data.data)) throw new Error('Magpie 返回的模型列表无效');
  return [...new Set(data.data.map(m => m.id).filter((id): id is string => typeof id === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}$/.test(id) && !id.includes('..')))].slice(0, 500);
}
// Strict SSE completion: a disconnected/truncated response is never committed as success.
export async function readMagpieStream(response: Response, onText: (text: string) => void) {
  if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream')) throw new Error('Magpie 未返回流式响应');
  const reader = response.body.getReader(); const decoder = new TextDecoder();
  let pending = '', text = '', bytes = 0, done = false, finished = false;
  function line(value: string) {
    if (!value.startsWith('data:')) return;
    const data = value.slice(5).trim();
    if (data === '[DONE]') { done = true; return; }
    if (!data) return;
    const event = JSON.parse(data);
    if (event.error) throw new Error('Magpie 生成失败');
    const choice = event.choices?.[0];
    if (choice?.finish_reason) {
      if (choice.finish_reason !== 'stop') throw new Error('模型输出未完整结束，请重试或更换模型');
      finished = true;
    }
    const delta = choice?.delta?.content;
    if (typeof delta === 'string') { text += delta; if (text.length > 200000) throw new Error('模型输出过长'); onText(text); }
  }
  try {
    while (!done) {
      const chunk = await reader.read();
      if (chunk.done) { pending += decoder.decode(); if (pending.trim()) line(pending.trim()); break; }
      bytes += chunk.value.byteLength;
      if (bytes > 8 * 1024 * 1024) throw new Error('模型响应过大');
      pending += decoder.decode(chunk.value, { stream: true });
      let index: number;
      while ((index = pending.indexOf('\n')) >= 0) { const value = pending.slice(0, index).replace(/\r$/, ''); pending = pending.slice(index + 1); line(value); if (done) break; }
    }
    if (!done || !finished || !text.trim()) throw new Error('模型响应中断或为空，旧结果未改动');
    return text;
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
}
export async function magpieGenerate(base: string, model: string, system: string, prompt: string, onText: (text: string) => void = () => {}, signal?: AbortSignal, maxTokens = 16000) {
  const timeout = AbortSignal.timeout(240000);
  const response = await call(base, '/chat/completions', { model, stream: true, max_tokens: maxTokens, messages: [{ role: 'system', content: system }, { role: 'user', content: prompt }] }, signal ? AbortSignal.any([signal, timeout]) : timeout);
  return readMagpieStream(response, onText);
}
