import { test } from 'node:test';
import assert from 'node:assert/strict';
import { startChapterAi } from '../web/ai-request.ts';
import type { PreviewResponse } from '../web/types.ts';
const input = { previewId: 'expired', bookId: 'book', chapterId: 'chapter', replaceConfirmed: true };

test('restores an expired preview once before starting the paid task', async () => {
  const calls: { path: string; body: unknown }[] = [];
  let observed: PreviewResponse | undefined;
  const fresh = { previewId: 'fresh', book: { bookId: 'book', title: '书', author: '' }, notes: [], warnings: [] };
  const state = { state: 'running', notes: [], error: '' };
  const value = await startChapterAi(input, async (path, body) => {
    calls.push({ path, body });
    if (calls.length === 1) throw new Error('预览已过期，请重新拉取');
    if (path === '/api/preview') return fresh;
    return state;
  }, preview => { observed = preview; });
  assert.equal(value, state); assert.equal(observed, fresh);
  assert.deepEqual(calls.map(c => c.path), ['/api/ai/start', '/api/books', '/api/preview', '/api/ai/start']);
  assert.equal((calls[3].body as { previewId: string }).previewId, 'fresh');
  assert.equal((calls[3].body as { deepThinking: boolean }).deepThinking, false);
});
test('preserves an explicit thinking choice when renewing the preview', async () => {
  const starts: unknown[] = [];
  await startChapterAi({ ...input, deepThinking: true }, async (path, body) => {
    if (path === '/api/ai/start') { starts.push(body); if (starts.length === 1) throw new Error('预览已过期，请重新拉取'); }
    return { previewId: 'fresh', state: 'running', notes: [] };
  }, () => {});
  assert.equal(starts.length, 2);
  assert.ok(starts.every(s => (s as { deepThinking: boolean }).deepThinking));
});
test('does not retry uncertain network failures or model errors', async () => {
  let attempts = 0;
  await assert.rejects(startChapterAi(input, async () => { attempts++; throw new Error('网络超时'); }, () => {}), /网络超时/);
  assert.equal(attempts, 1);
});
test('does not loop if the replacement preview is also rejected', async () => {
  let starts = 0;
  await assert.rejects(startChapterAi(input, async path => {
    if (path === '/api/ai/start') { starts++; throw new Error('预览已过期，请重新拉取'); }
    return { previewId: 'fresh' };
  }, () => {}), /预览已过期/);
  assert.equal(starts, 2);
});
