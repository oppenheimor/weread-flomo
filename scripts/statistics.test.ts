import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { calendarDays, activityLevel } from '../web/statistics.ts';

test('calendar has one cell per date, Sunday-first weeks and leap days', () => {
  for (const year of [2023, 2024, 2026, 2028]) {
    const cells = calendarDays(year);
    assert.equal(cells.length % 7, 0);
    assert.equal(cells.filter(Boolean).length, year % 4 === 0 ? 366 : 365);
    assert.equal(new Set(cells.filter(Boolean)).size, cells.filter(Boolean).length);
    for (const [i, day] of cells.entries()) if (day) assert.equal(new Date(`${day}T00:00:00Z`).getUTCDay(), i % 7);
    assert.equal(cells.find(Boolean), `${year}-01-01`);
    assert.equal(cells.filter(Boolean).at(-1), `${year}-12-31`);
  }
});
test('fixed thresholds remain comparable between years', () => {
  assert.deepEqual([0, 1, 5, 6, 15, 16, 30, 31, 1000].map(activityLevel), [0, 1, 1, 2, 2, 3, 3, 4, 4]);
});
