import { test } from 'node:test';
import assert from 'node:assert/strict';
import { needsReview, pendingNotes } from '../web/note-status.ts';

test('work list keeps ready and unresolved deliveries, not synced history', () => {
  const notes = [
    { id: 'a', status: 'sent' }, { id: 'b', status: 'ready' },
    { id: 'c', status: 'uncertain' }, { id: 'd', status: 'sending' },
  ];
  assert.deepEqual(pendingNotes(notes).map(n => n.id), ['b', 'c', 'd']);
  assert.equal(notes.length, 4); // Filtering does not delete history.
  assert.equal(needsReview('uncertain'), true);
  assert.equal(needsReview('sending'), true);
  assert.equal(needsReview('ready'), false);
});
test('successful sync removes a card; uncertain delivery remains for manual resolution', () => {
  assert.deepEqual(pendingNotes([{ id: 'a', status: 'sent' }]), []);
  assert.equal(pendingNotes([{ id: 'a', status: 'uncertain' }]).length, 1);
  assert.equal(pendingNotes([{ id: 'a', status: 'ready' }]).length, 1);
});
