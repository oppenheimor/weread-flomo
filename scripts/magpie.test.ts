import { test } from 'node:test';
import assert from 'node:assert/strict';
import { magpieUrl, readMagpieStream, magpieModels, magpieGenerate, clearMagpieKey, setMagpieKey } from '../web/magpie-transport.ts';
import { runBrowserJob, type BrowserJob } from '../web/ai-request.ts';

const job: BrowserJob = { mode: 'browser', jobId: 'job', bookId: 'book', chapterId: 'chapter', baseUrl: 'http://localhost:3425/v1', model: 'provider/model', system: 'system', prompt: 'prompt', chapter: { state: 'running', notes: [], error: '' } };
function stream(text: string) {
  const data = new TextEncoder().encode(text); let i = 0;
  return new Response(new ReadableStream({ pull(c) { if (i === data.length) c.close(); else c.enqueue(data.slice(i, ++i)); } }), { headers: { 'Content-Type': 'text/event-stream' } });
}
test('only loopback /v1 endpoints can receive the local key', () => {
  assert.equal(magpieUrl('http://127.0.0.1:3425/v1/'), 'http://127.0.0.1:3425/v1');
  for (const url of ['https://evil.test/v1', 'http://localhost.evil.test/v1', 'http://user:pw@localhost/v1', 'http://localhost/v1?q=1', 'http://localhost/admin', 'file:///v1']) assert.throws(() => magpieUrl(url));
});
test('SSE handles split UTF-8 and CRLF, ignores reasoning and requires complete termination', async () => {
  const text = 'data: {"choices":[{"delta":{"reasoning_content":"secret"}}]}\r\n\r\ndata: {"choices":[{"delta":{"content":"你好"}}]}\r\n\r\ndata: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n';
  assert.equal(await readMagpieStream(stream(text), () => {}), '你好');
  await assert.rejects(readMagpieStream(stream(text.replace('data: [DONE]\n\n', '')), () => {}), /中断/);
  await assert.rejects(readMagpieStream(stream(text.replace('"stop"', '"length"')), () => {}), /完整/);
});
test('browser gateway requests require a key before discovery or generation', async () => {
  const original = globalThis.fetch;
  let requests = 0;
  try {
    clearMagpieKey();
    globalThis.fetch = async (url, options) => {
      requests++;
      assert.equal(new Headers(options?.headers).has('Authorization'), false);
      assert.equal(options?.credentials, 'omit');
      if (String(url).endsWith('/models')) return Response.json({ data: [{ id: 'provider/model' }] });
      return stream('data: {"choices":[{"delta":{"content":"OK"},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n');
    };
    await assert.rejects(magpieModels(job.baseUrl), /填写并保存 Magpie 网关密钥/);
    await assert.rejects(magpieGenerate(job.baseUrl, job.model, 'test', 'test'), /填写并保存 Magpie 网关密钥/);
    assert.equal(requests, 0);
  } finally { clearMagpieKey(); globalThis.fetch = original; }
});
test('models request omits cookies, rejects redirects, and sends the memory-only gateway key', async () => {
  const original = globalThis.fetch;
  try {
    setMagpieKey('test-gateway-key');
    globalThis.fetch = async (url, options) => {
      assert.equal(url, job.baseUrl + '/models');
      assert.equal(options?.credentials, 'omit'); assert.equal(options?.redirect, 'error');
      assert.equal((options?.headers as Record<string, string>).Authorization, 'Bearer test-gateway-key');
      return Response.json({ data: [{ id: 'provider/model' }, { id: '../bad' }, { id: 'provider/model' }] });
    };
    assert.deepEqual(await magpieModels(job.baseUrl), ['provider/model']);
  } finally { clearMagpieKey(); globalThis.fetch = original; }
});
test('local generation sends only identity and model output to the server', async () => {
  const calls: { path: string; body: unknown }[] = [];
  const ready = { state: 'ready' as const, notes: [], error: '' };
  const result = await runBrowserJob(job, async (path, body) => { calls.push({ path, body }); return ready; }, () => {}, undefined, async (_base, _model, _system, _prompt, onText) => { onText?.('partial'); return 'complete'; });
  assert.equal(result, ready);
  assert.deepEqual(calls.map(c => c.path), ['/api/ai/browser/progress', '/api/ai/browser/complete']);
  assert.deepEqual(calls[1].body, { jobId: 'job', bookId: 'book', chapterId: 'chapter', text: 'complete', failed: false });
});
test('generation failure marks the job failed; uncertain commit is never retried or marked failed', async () => {
  const calls: unknown[] = [];
  await assert.rejects(runBrowserJob(job, async (_p, body) => { calls.push(body); return {}; }, () => {}, undefined, async () => { throw new Error('offline'); }), /offline/);
  assert.equal((calls[0] as { failed: boolean }).failed, true);
  let count = 0;
  await assert.rejects(runBrowserJob(job, async () => { count++; throw new Error('lost acknowledgement'); }, () => {}, undefined, async () => 'complete'), /lost acknowledgement/);
  assert.equal(count, 1);
});
