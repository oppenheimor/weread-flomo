// 生成初始本地登录配置；不会重置 PostgreSQL 中已经创建的账号。
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, renameSync, unlinkSync } from 'node:fs';

const lines = readFileSync('.env', 'utf8').split(/\r?\n/);
function existing(key: string): string | undefined {
  const found = lines.filter(line => new RegExp(`^${key}\\s*=`).test(line));
  if (found.length > 1) throw new Error(`配置项 ${key} 重复，未修改配置`);
  return found[0]?.slice(found[0].indexOf('=') + 1).trim();
}
if (existing('ADMIN_USERNAME') || existing('ADMIN_PASSWORD')) throw new Error('初始账号配置已存在，未覆盖；此脚本不能重置数据库中的密码');
const port = existing('FRONTEND_PORT') || '5175';
if (!/^\d+$/.test(port) || Number(port) < 1024 || Number(port) > 65535) throw new Error('前端端口无效，未修改配置');
const values: Record<string, string> = {
  ADMIN_USERNAME: 'admin',
  ADMIN_PASSWORD: randomBytes(24).toString('base64url'),
  FRONTEND_PORT: port,
  APP_PUBLIC_ORIGIN: existing('APP_PUBLIC_ORIGIN') || `http://127.0.0.1:${port}`,
  COOKIE_SECURE: existing('COOKIE_SECURE') || 'false',
};
for (const [key, value] of Object.entries(values)) {
  existing(key);
  const i = lines.findIndex(line => new RegExp(`^${key}\\s*=`).test(line));
  if (i >= 0) lines[i] = `${key}=${value}`;
  else lines.push(`${key}=${value}`);
}
const temp = `.env.setup-${randomBytes(8).toString('hex')}`;
try {
  writeFileSync(temp, lines.join('\n').replace(/\n*$/, '\n'), { mode: 0o600, flag: 'wx' });
  renameSync(temp, '.env');
} finally { try { unlinkSync(temp); } catch { /* 已完成原子替换 */ } }
console.log('初始登录账号 admin 和随机密码已写入 .env，权限为 0600。密码未输出。');
console.log('数据库连接和 API 密钥保持不变。启动 Java 后端后才会创建数据库账号。');
