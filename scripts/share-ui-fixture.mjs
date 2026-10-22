/** Local visual QA fixture only. Run after npm run build; never connects to WeRead/flomo.
 * node scripts/share-ui-fixture.mjs (http://127.0.0.1:4189)
 * Public samples: /share/ + a.repeat(43) (shelf), b.repeat(43) (single), e.repeat(43) (503).
 */
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { randomBytes } from 'node:crypto';
const root = process.env.SHARE_UI_DIST ? resolve(process.env.SHARE_UI_DIST) : resolve(import.meta.dirname, '../dist');
// Deterministic illustration data only; never writes to a user's statistics.
const days = {};
for (let i = 0; i < 295; i++) {
  const date = new Date(Date.UTC(2026, 0, 1 + i));
  const sample = (i * 37 + Math.floor(i / 7) * 13) % 100;
  if (sample < 33 || (i > 100 && i < 114)) continue;
  days[date.toISOString().slice(0, 10)] = sample < 62 ? 1 + sample % 5 : sample < 84 ? 6 + sample % 10 : sample < 96 ? 16 + sample % 15 : 31 + sample % 12;
}
const statistics = { today: '2026-10-22', notes: Object.values(days).reduce((sum, n) => sum + n, 0), books: 28, activeDays: Object.keys(days).length, days };
const books = [
  { bookId: 'book-1', title: '也许你该找个人聊聊', author: '洛莉·戈特利布', noteCount: 3, reviewCount: 1 },
  { bookId: 'book-2', title: '山茶文具店', author: '小川糸', noteCount: 0, reviewCount: 0 },
  { bookId: 'book-3', title: '置身事内：中国政府与经济发展', author: '兰小欢', noteCount: 1, reviewCount: 0 },
  { bookId: 'book-4', title: '人生海海', author: '麦家', noteCount: 2, reviewCount: 0 },
];
const chapters = [{ id: 'c1', title: '第一章 · 从倾听开始', order: 1 }, { id: 'c2', title: '第二章 · 变化的可能', order: 2 }];
const notes = [
  { id: 'n1', kind: 'highlight', quote: '有时候，我们需要的不是一个答案，而是有人愿意坐下来，听我们把话说完。', thought: '读到这里，想起了和朋友散步的那个晚上。很多事情在说出口的那一刻，就已经开始变得不一样。', chapter: chapters[0], createTime: 1750200000 },
  { id: 'n2', kind: 'highlight', quote: '真正的改变往往不是突然发生的。它藏在每一次细小的选择里，也藏在我们重新看待自己的方式里。\n\n慢一点，也没有关系。', thought: '', chapter: chapters[0], createTime: 1750200100 },
  { id: 'n3', kind: 'review', quote: '', thought: '这一章提醒我：倾听不需要急着给建议。', chapter: chapters[1], createTime: 1750200200 },
];
const introduction = process.env.SHARE_UI_INTRODUCTION === '1';
if (introduction) {
  Object.assign(books[0], { title: '狂人日记：鲁迅小说集', author: '鲁迅', noteCount: 2, reviewCount: 0 });
  chapters.splice(0, chapters.length, { id: 'c1', title: '故乡', order: 1 });
  notes.splice(0, notes.length, ...[
    '我想：希望是本无所谓有，无所谓无的。',
    '这正如地上的路；其实地上本没有路，走的人多了，也便成了路。',
  ].map((quote, i) => ({ id: `n${i + 1}`, kind: 'highlight', quote, thought: '', chapter: chapters[0], createTime: 1792627200 })));
}
const aiExample = { state: 'ready', error: '', drafts: [], notes: [{
  id: 'example-ai', title: '希望与路',
  body: '鲁迅在《故乡》结尾，把希望比作地上的路：希望本无所谓有无，路也不是一开始就有的，而是在许多人行走之后形成。\n\n把两段摘录连起来看，重点落在了**“走的人多了，也便成了路”**。对希望的思考，与人们的行动联系在一起。',
  snapshot: { book: books[0], chapter: chapters[0], notes }, status: 'ready', edited: false, version: 1,
}] };
const links = new Map([['', 'a'.repeat(43)], ['book-1', 'b'.repeat(43)]]);
const requests = [];
let failedStateReads = Number(process.env.SHARE_UI_FAIL_STATE || 0);
const state = key => ({ enabled: links.has(key), token: links.get(key) ?? null });
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1:4189');
  const path = url.pathname;
  const json = (data, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); };
  if (path === '/__requests') return json(requests);
  if (path.startsWith('/api/')) {
    requests.push({ method: req.method, path: req.url });
    let raw = ''; for await (const chunk of req) raw += chunk;
    const body = raw ? JSON.parse(raw) : {};
    if (path === '/api/auth/session') return json({ authenticated: true, username: '设计验收账号', csrfToken: 'fixture' });
    if (path === '/api/status') return json({ wereadConfigured: true, sendingEnabled: false });
    if (path === '/api/ai/settings') return json({ providers: [], defaultModel: null });
    if (path === '/api/books') return json({ books, sync: {} });
    if (path === '/api/statistics') return json(statistics);
    if (path === '/api/shares') {
      if (req.method === 'GET' && failedStateReads-- > 0) return json({ error: 'Fixture: state unavailable' }, 503);
      const key = req.method === 'POST' ? body.bookId ?? '' : url.searchParams.get('bookId') ?? '';
      if (req.method === 'POST') { if (body.enabled && !links.has(key)) links.set(key, randomBytes(32).toString('base64url')); else if (!body.enabled) links.delete(key); }
      return json(state(key));
    }
    if (path.startsWith('/api/public/shares/')) {
      const token = path.split('/').at(-1);
      if (token === 'e'.repeat(43)) return json({ error: '暂时无法加载' }, 503);
      const grant = [...links].find(([, value]) => value === token);
      if (!grant) return json({ error: '分享已关闭或不存在' }, 404);
      const selected = url.searchParams.get('bookId') ?? grant[0];
      if (grant[0] && selected !== grant[0]) return json({ error: '分享已关闭或不存在' }, 404);
      const book = books.find(b => b.bookId === selected);
      if (selected && !book) return json({ error: '分享已关闭或不存在' }, 404);
      return json({ username: selected ? null : '设计验收账号', scope: grant[0] ? 'book' : 'shelf', books: selected ? [] : books, detail: book ? { book, notes: book.bookId === 'book-2' ? [] : process.env.SHARE_UI_LONG_CHAPTERS ? notes.flatMap(n => Array.from({ length: 12 }, (_, i) => ({ ...n, id: `${n.id}-${i}` }))) : notes } : null });
    }
    if (path === '/api/preview') return json({ previewId: 'fixture', book: books.find(b => b.bookId === body.bookId), warnings: [], notes: notes.map(n => ({ ...n, status: 'ready', sources: 1, bookId: body.bookId, content: n.quote })) });
    if (path === '/api/book-position') return json({ chapterId: '' });
    if (path === '/api/ai/chapters') return json(introduction ? chapters : []);
    if (path === '/api/ai/chapter') return json(introduction ? aiExample : { notes: [], drafts: [], state: 'idle' });
    return json({ error: 'Not implemented in visual fixture' }, 404);
  }
  const relative = path === '/' || path.startsWith('/share/') ? '/index.html' : path;
  const file = resolve(root, '.' + relative);
  if (!file.startsWith(root + '/')) { res.writeHead(404); return res.end(); }
  try {
    const content = await readFile(file);
    const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.webp': 'image/webp', '.woff2': 'font/woff2', '.svg': 'image/svg+xml' };
    res.writeHead(200, { 'Content-Type': mime[extname(file)] ?? 'application/octet-stream' }); res.end(content);
  } catch { res.writeHead(404); res.end(); }
});
server.listen(4189, '127.0.0.1', () => console.log('Visual fixture: http://127.0.0.1:4189 (synthetic content, no upstream requests)'));
