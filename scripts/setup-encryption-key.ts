// 只生成服务端加密主密钥；已经存在时绝不覆盖，避免数据库凭证无法解密。
import { randomBytes } from 'node:crypto';
import { readFileSync, writeFileSync, renameSync, unlinkSync } from 'node:fs';
const content = readFileSync('.env', 'utf8');
const matches = content.split(/\r?\n/).filter(line => /^CREDENTIAL_ENCRYPTION_KEY\s*=/.test(line));
if (matches.length > 1) throw new Error('加密主密钥配置重复，请先检查');
const existing = matches[0]?.slice(matches[0].indexOf('=') + 1).trim();
if (existing) {
  if (Buffer.from(existing, 'base64').length !== 32) throw new Error('已有主密钥格式异常，未覆盖');
  console.log('加密主密钥已经存在，保持不变。');
} else {
  const line = `CREDENTIAL_ENCRYPTION_KEY=${randomBytes(32).toString('base64')}`;
  const updated = matches.length ? content.replace(/^CREDENTIAL_ENCRYPTION_KEY\s*=.*$/m, line) : `${content.replace(/\n*$/, '\n')}${line}\n`;
  const temporary = `.env.setup-${randomBytes(8).toString('hex')}`;
  try {
    writeFileSync(temporary, updated, { mode: 0o600, flag: 'wx' }); renameSync(temporary, '.env');
  } finally { try { unlinkSync(temporary); } catch { /* 已完成原子替换 */ } }
  console.log('服务端随机加密主密钥已写入 .env，权限 0600；未输出密钥。请单独安全备份。');
}
