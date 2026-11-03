import test from 'node:test';
import assert from 'node:assert/strict';
import { loadChapterSummaries } from '../web/chapter-summaries.ts';
import { chapterWorklist } from '../web/chapter-worklist.ts';

test('100 organized chapters require one summary request, never per-chapter detail fan-out', async () => {
  const calls: string[] = [];
  const summaries = Array.from({ length: 100 }, (_, i) => ({ chapter: { id: String(i), title: `章${i}`, order: i }, state: 'ready', pending: i % 2 === 0 }));
  const result = await loadChapterSummaries('book/id', async <T>(path: string) => { calls.push(path); return summaries as T; });
  assert.deepEqual(calls, ['/api/ai/chapter-summaries?bookId=book%2Fid']);
  assert.equal(chapterWorklist([], result.chapters, result.states).chapters.length, 50);
  assert.equal(Object.keys(result.states).length, 100);
  assert.ok(Object.values(result.states).every(state => !('notes' in state)));
});
test('summary failures are explicit, never an empty/successful book or detail request fallback', async () => {
  let calls = 0;
  await assert.rejects(() => loadChapterSummaries('book', async () => { calls++; throw new Error('unavailable'); }), /unavailable/);
  assert.equal(calls, 1);
});
