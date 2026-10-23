import { test } from 'node:test';
import assert from 'node:assert/strict';
import { startChapterAi } from '../web/ai-request.ts';
const input = { previewId: 'old', bookId: 'book', chapterId: 'chapter', replaceConfirmed: false };
const fresh = { previewId: 'fresh', notes: [{ id: 'new', status: 'ready', chapter: { id: 'chapter' } }] };
const state = { state: 'ready', notes: [], error: '' };
test('refreshes even a valid preview before organizing new annotations', async () => {
  const calls: { path: string; body: any }[] = [];
  let observed: unknown;
  await startChapterAi(input, async (path, body) => {
    calls.push({ path, body });
    return path === '/api/preview' ? fresh : state;
  }, p => { observed = p; });
  assert.deepEqual(calls.map(c => c.path), ['/api/books', '/api/preview', '/api/ai/start']);
  assert.equal(observed, fresh);
  assert.equal(calls[2].body.previewId, 'fresh');
  assert.equal(calls[2].body.replaceConfirmed, false);
});
test('shows existing results without starting a model when there are no ready sources', async () => {
  const calls: string[] = [];
  const value = await startChapterAi(input, async path => {
    calls.push(path);
    return path === '/api/preview' ? { previewId: 'fresh', notes: [] } : state;
  }, () => {});
  assert.equal(value, state);
  assert.ok(calls[2].startsWith('/api/ai/chapter?'));
  assert.ok(!calls.includes('/api/ai/start'));
});
test('preserves regeneration and thinking options', async () => {
  await startChapterAi({ ...input, replaceConfirmed: true, deepThinking: true }, async (path, body: any) => {
    if (path === '/api/preview') return fresh;
    if (path === '/api/ai/start') { assert.equal(body.replaceConfirmed, true); assert.equal(body.deepThinking, true); }
    return state;
  }, () => {});
});
test('does not start after refresh failure', async () => {
  const calls: string[] = [];
  await assert.rejects(startChapterAi(input, async path => { calls.push(path); throw new Error('网络超时'); }, () => {}), /网络超时/);
  assert.deepEqual(calls, ['/api/books']);
});
test('never retries a possibly started paid task', async () => {
  let starts = 0;
  await assert.rejects(startChapterAi(input, async path => {
    if (path === '/api/preview') return fresh;
    if (path === '/api/ai/start') { starts++; throw new Error('网络超时'); }
    return state;
  }, () => {}), /网络超时/);
  assert.equal(starts, 1);
});
