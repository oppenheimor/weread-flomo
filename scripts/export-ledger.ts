// 一次性离线迁移工具，不是服务端。只读旧 SQLite，不改变状态，不发送笔记。
import { DatabaseSync } from 'node:sqlite';
import { writeFileSync } from 'node:fs';
const output = process.argv[2];
if (!output) throw new Error('用法：node scripts/export-ledger.ts data/ledger-export.json');
const db = new DatabaseSync('data/sync.sqlite', { readOnly: true });
try {
  const rows = db.prepare('SELECT id,content,status FROM deliveries').all();
  if (rows.some(row => row.status === 'sending')) throw new Error('仍有发送中记录，请停止旧服务并核对后再迁移');
  const deliveries = rows.map(row => ({
    noteId: row.id, content: row.content, status: row.status,
    sourceKeys: db.prepare('SELECT source_key FROM sources WHERE delivery_id=? ORDER BY source_key').all(row.id).map(s => s.source_key),
  }));
  writeFileSync(output, JSON.stringify({ version: 1, deliveries }), { mode: 0o600, flag: 'wx' });
  console.log(`已导出 ${deliveries.length} 条同步记录。文件含私人笔记，请安全传输并在迁移后清理。`);
} finally { db.close(); }
