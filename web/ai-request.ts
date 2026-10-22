import type { AiChapter } from './ai-types.ts';
import type { PreviewResponse } from './types.ts';
import { magpieGenerate } from './magpie-transport.ts';

export interface BrowserJob {
  mode: 'browser'; jobId: string; bookId: string; chapterId: string;
  baseUrl: string; model: string; system: string; prompt: string; chapter: AiChapter;
}
type Invoke = (path: string, body: unknown) => Promise<unknown>;
export async function runBrowserJob(job: BrowserJob, invoke: Invoke, onState: (state: AiChapter) => void, signal?: AbortSignal, generate = magpieGenerate): Promise<AiChapter> {
  const identity = { jobId: job.jobId, bookId: job.bookId, chapterId: job.chapterId };
  let last = 0, pending: Promise<unknown> | undefined;
  onState(job.chapter);
  let text: string;
  try {
    text = await generate(job.baseUrl, job.model, job.system, job.prompt, partial => {
      if (pending || Date.now() - last < 750) return;
      last = Date.now();
      pending = invoke('/api/ai/browser/progress', { ...identity, text: partial, failed: false })
        .then(value => onState(value as AiChapter)).catch(() => {}).finally(() => { pending = undefined; });
    }, signal);
  } catch (error) {
    await pending;
    await invoke('/api/ai/browser/complete', { ...identity, text: '', failed: true }).catch(() => {});
    throw error;
  }
  await pending;
  // Reconcile through a read only request; never regenerate or fail a possibly committed job.
  try {
    return await invoke('/api/ai/browser/complete', { ...identity, text, failed: false }) as AiChapter;
  } catch (error) {
    let current: AiChapter | undefined;
    try {
      current = await invoke(`/api/ai/chapter?bookId=${encodeURIComponent(job.bookId)}&chapterId=${encodeURIComponent(job.chapterId)}`, undefined) as AiChapter;
    } catch { /* Preserve the original error if status is also unavailable. */ }
    if (current && ['running', 'ready', 'failed', 'idle'].includes(current.state)) {
      onState(current);
      if (current.state === 'ready' || current.state === 'failed') return current;
    }
    throw error;
  }
}
export async function startChapterAi(
  input: { previewId: string; bookId: string; chapterId: string; replaceConfirmed: boolean; deepThinking?: boolean },
  invoke: Invoke,
  onPreview: (preview: PreviewResponse) => void,
  onState: (state: AiChapter) => void = () => {},
  signal?: AbortSignal,
): Promise<AiChapter> {
  const payload = { previewId: input.previewId, chapterId: input.chapterId, replaceConfirmed: input.replaceConfirmed, deepThinking: input.deepThinking ?? false };
  let result: AiChapter | BrowserJob;
  try { result = await invoke('/api/ai/start', payload) as AiChapter | BrowserJob; }
  catch (error) {
    // Only retry an expired preview, before a task was created. Never retry generation.
    if (!(error instanceof Error) || error.message !== '预览已过期，请重新拉取') throw error;
    await invoke('/api/books', {});
    const fresh = await invoke('/api/preview', { bookId: input.bookId }) as PreviewResponse;
    onPreview(fresh);
    result = await invoke('/api/ai/start', { ...payload, previewId: fresh.previewId }) as AiChapter | BrowserJob;
  }
  return 'mode' in result && result.mode === 'browser' ? runBrowserJob(result, invoke, onState, signal) : result as AiChapter;
}
