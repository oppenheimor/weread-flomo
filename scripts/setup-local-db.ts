// 仅初始化本项目独立的本地测试库；不会连接或修改其他数据库。
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, renameSync, unlinkSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const name = 'weread-flomo-postgres';
const volume = 'weread-flomo-pgdata';
const url = 'jdbc:postgresql://127.0.0.1:25432/weread_flomo';
const docker = (args: string[], env = process.env) => spawnSync('docker', args, { env, stdio: 'pipe', encoding: 'utf8' });

if (docker(['info', '--format', '{{.ServerVersion}}']).status !== 0) throw new Error('请先启动 Docker');
if (docker(['container', 'inspect', name]).status === 0) throw new Error('本项目数据库容器已存在，未重置密码或数据');
if (docker(['volume', 'inspect', volume]).status === 0) throw new Error('本项目数据库卷已存在，未重置密码或数据；请先核对已有配置');

const original = readFileSync('.env', 'utf8');
const lines = original.split(/\r?\n/);
const oldUrl = lines.find(line => /^DATABASE_URL\s*=/.test(line))?.split('=').slice(1).join('=').trim();
if (oldUrl && oldUrl !== url) throw new Error('已有其他 DATABASE_URL 配置，停止以免覆盖其他数据库连接');

const password = randomBytes(32).toString('hex');
const values: Record<string, string> = {
  DATABASE_URL: url,
  DATABASE_USERNAME: 'weread_flomo',
  DATABASE_PASSWORD: password,
};
for (const [key, value] of Object.entries(values)) {
  const positions = lines.flatMap((line, i) => new RegExp(`^${key}\\s*=`).test(line) ? [i] : []);
  if (positions.length > 1) throw new Error(`配置项 ${key} 重复，停止以免误覆盖`);
  if (positions.length === 1) lines[positions[0]] = `${key}=${value}`;
  else lines.push(`${key}=${value}`);
}
const temp = `.env.setup-${randomBytes(8).toString('hex')}`;
try {
  writeFileSync(temp, lines.join('\n').replace(/\n*$/, '\n'), { mode: 0o600, flag: 'wx' });
  renameSync(temp, '.env');
} finally { try { unlinkSync(temp); } catch { /* 已原子替换 */ } }

const result = docker(['run', '-d', '--name', name,
  '-p', '127.0.0.1:25432:5432',
  '-e', 'POSTGRES_DB=weread_flomo', '-e', 'POSTGRES_USER=weread_flomo', '-e', 'POSTGRES_PASSWORD',
  '-v', `${volume}:/var/lib/postgresql/data`, 'postgres:17-alpine'],
  { ...process.env, POSTGRES_PASSWORD: password });
if (result.status !== 0) throw new Error('数据库启动失败；配置已保留在 .env，请检查端口或 Docker 状态。未输出含密钥的错误正文');
let ready = false;
for (let i = 0; i < 30; i++) {
  // 经 TCP 验证实际密码，而不仅仅检查 PostgreSQL 进程存活。
  const check = docker(['exec', '-e', 'PGPASSWORD', name, 'psql', '-h', '127.0.0.1', '-U', 'weread_flomo', '-d', 'weread_flomo', '-tAc', 'SELECT 1'], { ...process.env, PGPASSWORD: password });
  if (check.status === 0 && check.stdout.trim() === '1') { ready = true; break; }
  await new Promise(resolve => setTimeout(resolve, 1000));
}
if (!ready) throw new Error('数据库已创建但尚未通过连接验证，请检查容器状态；不要重复初始化');
console.log('随机数据库密码已写入 .env（权限 0600），未显示密码。');
console.log('独立 PostgreSQL 已启动并通过密码连接验证：127.0.0.1:25432 / weread_flomo。');
