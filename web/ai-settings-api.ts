import { request } from './api.ts';
import type { AiProvider, AiSettings } from './ai-types.ts';
import { clearMagpieKey, magpieGenerate, magpieModels, magpieUrl, setMagpieKey } from './magpie-transport.ts';

export async function modelRequest<T>(path: string, body?: unknown, signal?: AbortSignal): Promise<T> {
  const input = body as { id?: string; providerId?: string; apiKey?: string | null; baseUrl?: string; model?: string } | undefined;
  const local = input?.id === 'magpie' || input?.providerId === 'magpie';
  if (!local) return request<T>(path, body, signal);
  if (path === '/api/ai/providers') {
    magpieUrl(input!.baseUrl!);
    // Strip secrets even when adding models or selecting the default.
    const result = await request<T>(path, { ...input, apiKey: null }, signal);
    if (input?.apiKey) setMagpieKey(input.apiKey);
    return result;
  }
  if (path === '/api/ai/providers/delete') {
    const result = await request<T>(path, body, signal); clearMagpieKey(); return result;
  }
  if (path === '/api/ai/models' || path === '/api/ai/test') {
    const settings = await request<AiSettings>('/api/ai/settings');
    const provider: AiProvider | undefined = settings.providers.find(p => p.id === 'magpie');
    if (!provider) throw new Error('请先保存 Magpie 配置');
    if (path === '/api/ai/models') return { models: await magpieModels(provider.baseUrl) } as T;
    await magpieGenerate(provider.baseUrl, input!.model!, 'Respond briefly.', 'Reply OK', undefined, signal, 1024);
    return { ok: true } as T;
  }
  return request<T>(path, body, signal);
}
