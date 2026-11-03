import test from 'node:test';
import assert from 'node:assert/strict';
import { chapterWorklist, completionTitle } from '../web/chapter-worklist.ts';

const chapters = [1, 2, 3, 4].map(order => ({ id: String(order), title: `第${order}章`, order }));
const note = (index: number, status: string) => ({ chapter: chapters[index], status });
const state = (...statuses: string[]) => ({ state: 'ready', notes: statuses.map(status => ({ status })) });

test('entry hides sent chapters and preserves source order', () => {
  const result = chapterWorklist([note(2, 'ready'), note(0, 'sent'), note(1, 'uncertain')], [], {});
  assert.deepEqual(result.chapters.map(c => c.id), ['2', '3']);
  assert.equal(result.pendingIds.has('1'), false);
});
test('completed current chapter stays between pending chapters until leaving', () => {
  const notes = [note(0, 'ready'), note(1, 'sent'), note(2, 'ready'), note(3, 'sent')];
  assert.deepEqual(chapterWorklist(notes, [], {}, '2').chapters.map(c => c.id), ['1', '2', '3']);
  assert.deepEqual(chapterWorklist(notes, [], {}, '1').chapters.map(c => c.id), ['1', '3']);
});
test('AI-only pending and uncertain results remain reachable after originals sent', () => {
  const result = chapterWorklist([note(0, 'sent')], chapters.slice(0, 3), { '1': state('ready'), '2': state('uncertain'), '3': state('sent') });
  assert.deepEqual(result.chapters.map(c => c.id), ['1', '2']);
});
test('newly organized results count even before stored chapter index is refreshed', () => {
  assert.deepEqual([...chapterWorklist([note(0, 'sent')], [], { '1': state('ready') }).pendingIds], ['1']);
});
test('running, failed, unknown and sending work never disappears', () => {
  const result = chapterWorklist([], chapters, {
    '1': { state: 'running', notes: [] }, '2': { state: 'failed', notes: [] }, '4': state('sending'),
  });
  assert.equal(result.chapters.length, 4);
});
test('last completion has no pending navigation; reopening has no current chapter', () => {
  const notes = [note(0, 'sent')];
  const result = chapterWorklist(notes, chapters.slice(0, 1), { '1': state('sent') }, '1');
  assert.equal(result.pendingIds.size, 0);
  assert.equal(result.chapters.length, 1);
  assert.equal(chapterWorklist(notes, chapters.slice(0, 1), { '1': state('sent') }).chapters.length, 0);
  assert.equal(completionTitle(true, true), '本章已全部同步到 flomo');
  assert.equal(completionTitle(true, false), '这本书的笔记已全部同步');
  assert.equal(completionTitle(false, false), '本书暂无待同步笔记');
});
