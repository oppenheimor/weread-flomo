import { test } from 'node:test';
import assert from 'node:assert/strict';
import { boldSegments } from '../web/markdown.ts';

test('renders bold phrases while leaving stored Markdown untouched', () => {
  const text = '先**提前起床**，再用**输出倒逼输入**。';
  assert.deepEqual(boldSegments(text), [
    { text: '先', bold: false }, { text: '提前起床', bold: true },
    { text: '，再用', bold: false }, { text: '输出倒逼输入', bold: true }, { text: '。', bold: false },
  ]);
  assert.equal(text, '先**提前起床**，再用**输出倒逼输入**。');
});
test('keeps HTML, incomplete markers and line breaks as inert text', () => {
  const text = '<script>alert(1)</script>\n**未闭合';
  assert.deepEqual(boldSegments(text), [{ text, bold: false }]);
  assert.deepEqual(boldSegments('**<img src=x>**'), [{ text: '<img src=x>', bold: true }]);
});
