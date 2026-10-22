import { useEffect, useState } from 'react';
import { ArrowUpRight, Plus, Star, X, RefreshCw } from 'lucide-react';
import { ProviderLogo } from './ProviderLogo.tsx';
import { providerMeta } from './provider-meta.ts';
import { modelRequest as request } from './ai-settings-api.ts';
import { Sheet } from './Sheet.tsx';
import type { AiProvider, AiSettings } from './ai-types.ts';
import './model-settings.css';

// Provider catalog adapted from frametell/packages/models/src/providers.ts.
const catalog = [
  ['anthropic', 'Claude', 'anthropic', 'https://api.anthropic.com'],
  ['openai', 'OpenAI', 'openai', 'https://api.openai.com/v1'],
  ['google', 'Gemini', 'google', 'https://generativelanguage.googleapis.com/v1beta'],
  ['xai', 'Grok', 'openai', 'https://api.x.ai/v1'],
  ['openrouter', 'OpenRouter', 'openai', 'https://openrouter.ai/api/v1'],
  ['deepseek', 'DeepSeek', 'openai', 'https://api.deepseek.com'],
  ['moonshotai-cn', 'Kimi', 'openai', 'https://api.moonshot.cn/v1'],
  ['zhipu', '智谱 GLM', 'openai', 'https://open.bigmodel.cn/api/paas/v4'],
  ['qwen', '通义千问', 'openai', 'https://dashscope.aliyuncs.com/compatible-mode/v1'],
  ['minimax-cn', 'MiniMax', 'anthropic', 'https://api.minimaxi.com/anthropic'],
  ['siliconflow', '硅基流动', 'openai', 'https://api.siliconflow.cn/v1'],
  ['volcengine-ark', '豆包', 'openai', 'https://ark.cn-beijing.volces.com/api/v3'],
  ['hunyuan', '腾讯混元', 'openai', 'https://api.hunyuan.cloud.tencent.com/v1'],
  ['xiaomi', '小米 MiMo', 'openai', 'https://api.xiaomimimo.com/v1'],
  ['ollama', 'Ollama', 'openai', 'http://localhost:11434/v1'],
  ['magpie', 'Magpie', 'openai', 'http://localhost:3425/v1'],
].map(([id, name, protocol, baseUrl]): AiProvider => ({ id, name, protocol, baseUrl, hasKey: false, models: [] }));
const errorText = (e: unknown) => e instanceof Error ? e.message : '操作失败，请重试';

export function ModelSettings({ onClose, onChanged }: { onClose: () => void; onChanged: (s: AiSettings) => void }) {
  const [settings, setSettings] = useState<AiSettings | null>(null);
  const [current, setCurrent] = useState('anthropic');
  const [name, setName] = useState(''); const [protocol, setProtocol] = useState('openai');
  const [baseUrl, setBaseUrl] = useState(''); const [key, setKey] = useState('');
  const [model, setModel] = useState(''); const [listed, setListed] = useState<string[]>([]);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [notice, setNotice] = useState('');
  const [dirty, setDirty] = useState(false);
  const all = [...catalog, ...(settings?.providers.filter(p => !catalog.some(c => c.id === p.id)) ?? [])];
  const saved = settings?.providers.find(p => p.id === current);
  function accept(next: AiSettings) { setSettings(next); onChanged(next); }
  useEffect(() => { const controller = new AbortController(); request<AiSettings>('/api/ai/settings', undefined, controller.signal).then(accept).catch(e => { if (!controller.signal.aborted) setError(errorText(e)); }); return () => controller.abort(); }, []);
  useEffect(() => {
    const p = saved ?? all.find(p => p.id === current);
    setName(p?.name ?? '自定义服务商'); setProtocol(p?.protocol ?? 'openai'); setBaseUrl(p?.baseUrl ?? ''); setKey(''); setListed(p?.models ?? []); setModel(''); setDirty(false);
  }, [current, settings]);
  function changeProvider(id: string) { if (dirty && !window.confirm('放弃未保存的模型配置？')) return; setError(''); setNotice(''); setCurrent(id); }
  function close() { if (!busy && (!dirty || window.confirm('放弃未保存的模型配置？'))) { setKey(''); onClose(); } }
  const [busyText, setBusyText] = useState('处理中…');
  async function run(work: () => Promise<void>, loadingText = '处理中…') { setBusyText(loadingText); setBusy(true); setError(''); setNotice(''); try { await work(); } catch (e) { setError(errorText(e)); } finally { setBusy(false); } }
  const defaultModel = settings?.defaultModel;
  const meta = providerMeta[current];
  const displayName = name || all.find(p => p.id === current)?.name || '自定义服务商';
  const isMagpie = current === 'magpie';
  const localKeySaved = !!saved?.hasKey;
  return <Sheet className="modal model-settings" labelledBy="model-settings-title" locked={busy} onClose={close}>
    <header className="sheet-header model-settings-header">
      <h2 id="model-settings-title" tabIndex={-1}>模型设置</h2>
      <button className="icon-button model-close" type="button" disabled={busy} onClick={close} aria-label="关闭模型设置"><X size={18} strokeWidth={2.25} aria-hidden="true" /></button>
    </header>
    {!settings ? <p role="status">{error || '正在读取配置…'}</p> : <div className="model-settings-layout">
      <nav className="model-providers" aria-label="模型服务商">{['海外', '国内', '本地', '自定义'].map(group => <div className="model-provider-group" key={group}><h4>{group}</h4>{all.filter(p => (providerMeta[p.id]?.group ?? '自定义') === group).map(p => <button type="button" key={p.id} disabled={busy} aria-current={current === p.id ? 'true' : undefined} onClick={() => changeProvider(p.id)}><ProviderLogo id={p.id} name={p.name} /><span>{p.name}</span>{defaultModel?.providerId === p.id && <small>当前使用</small>}{settings.providers.some(s => s.id === p.id) && <i aria-label="已配置" />}</button>)}{group === '自定义' && <button type="button" className="model-add-provider" disabled={busy} onClick={() => changeProvider(`custom-${crypto.randomUUID().slice(0, 8)}`)}><span className="model-add-mark"><Plus size={11} /></span>添加兼容接口</button>}</div>)}</nav>
      <div className="model-detail">
        <header className="model-provider-header"><span className="model-provider-mark"><ProviderLogo id={current} name={displayName} size={24} /></span><div><h3>{displayName}<span className={`model-connection${saved ? ' is-configured' : ''}`}><i />{saved ? '已配置' : '未连接'}</span></h3><p><span>{meta?.blurb ?? '自定义兼容接口'}</span>{meta && <a href={meta.site} target="_blank" rel="noreferrer">{new URL(meta.site).host.replace(/^www\./, '')}<ArrowUpRight size={12} /></a>}</p></div></header>
        {(busy || error || notice) && <div className="model-feedback-bar">
          {busy && <p role="status" className="model-feedback hint">{busyText}</p>}
          {error && <p className="model-feedback error-text" role="alert">{error}</p>}
          {notice && <p className="model-feedback success-text" role="status">{notice}</p>}
        </div>}
        <div className="model-detail-body">
        <form onSubmit={e => { e.preventDefault(); void run(async () => { const next = await request<AiSettings>('/api/ai/providers', { id: current, name, protocol, baseUrl, apiKey: key || null, models: listed }); accept(next); setKey(''); setDirty(false); setNotice('配置已保存'); }); }}>
          <fieldset disabled={busy}>{!meta && <><label>服务商名称<input value={name} onChange={e => { setName(e.target.value); setDirty(true); }} required maxLength={120} /></label>
          <label>接口协议<select value={protocol} onChange={e => { setProtocol(e.target.value); setDirty(true); }}><option value="openai">OpenAI 兼容</option><option value="anthropic">Anthropic Messages</option><option value="google">Google Gemini</option></select></label></>}
          {isMagpie ? <div className="model-gateway-key">
            <div className="model-key-heading"><label htmlFor="magpie-gateway-key">网关密钥 <span>Gateway key</span></label>{localKeySaved && <span className="model-key-status">已加密保存</span>}</div>
            <input id="magpie-gateway-key" type="password" autoComplete="off" spellCheck={false} required={!localKeySaved} maxLength={4096} aria-describedby="magpie-key-help" value={key} onChange={e => { setKey(e.target.value); setDirty(true); }} placeholder={localKeySaved ? '输入新密钥以替换' : '粘贴 Magpie 网关密钥'} />
            <div className="model-key-footer"><p id="magpie-key-help">密钥按账号加密保存，刷新后无需重填。调用时由浏览器读取并连接本机网关；移除配置会同时删除密钥。</p></div>
          </div> : <label>API Key<input type="password" autoComplete="off" value={key} onChange={e => { setKey(e.target.value); setDirty(true); }} placeholder={saved?.hasKey ? '输入新密钥以替换' : meta?.keyHint ?? '输入服务商提供的 API Key'} /></label>}
          {meta?.keyUrl && <a className="model-key-link" href={meta.keyUrl} target="_blank" rel="noreferrer">去 {displayName} 获取 key <ArrowUpRight size={13} /></a>}
          <label>{isMagpie ? '本地网关地址' : '请求地址'}<input type="url" spellCheck={false} value={baseUrl} onChange={e => { setBaseUrl(e.target.value); setDirty(true); }} required placeholder={isMagpie ? 'http://localhost:3425/v1' : 'https://api.example.com/v1'} /><span className="model-field-hint">{current === 'magpie' ? '连接这台电脑上的 Magpie，通常无需修改。' : '默认使用官方地址；用自己的中转或代理网关时填这里。'}</span></label>
          {current === 'ollama' && <p className="hint">localhost 指服务端机器。私网服务需管理员配置 app.ai-trusted-hosts；不能访问用户电脑上的 localhost。</p>}
          {(dirty || saved || !meta || current === 'ollama' || current === 'magpie') && <div className="model-actions"><button type="submit" className="primary">保存配置</button>{saved && <button type="button" className="text-button" onClick={() => { if (window.confirm('移除此服务商配置？默认模型可能需要重新选择。')) void run(async () => { accept(await request<AiSettings>('/api/ai/providers/delete', { id: current })); }); }}>移除配置</button>}</div>}</fieldset>
        </form>
        <section className="model-list"><header><h3>模型</h3><button type="button" className="text-button" hidden={!saved} disabled={busy || !saved || dirty} onClick={() => void run(async () => { const r = await request<{ models: string[] }>('/api/ai/models', { id: current }); accept(await request<AiSettings>('/api/ai/providers', { id: current, name, protocol, baseUrl, apiKey: null, models: [...new Set([...listed, ...r.models])].slice(0, 500) })); setNotice(`已获取 ${r.models.length} 个模型，可选择默认模型`); })}><RefreshCw size={14} />获取模型</button></header>
          {saved && <div className="model-manual"><input aria-label="模型 ID" value={model} onChange={e => setModel(e.target.value)} placeholder={current === 'magpie' ? '模型 ID：provider/model 或 group/分组名' : '搜索或手动填写模型 ID'} maxLength={200} /><button type="button" disabled={busy || !saved || dirty || !model.trim()} onClick={() => void run(async () => { accept(await request<AiSettings>('/api/ai/providers', { id: current, name, protocol, baseUrl, apiKey: null, models: [...new Set([...listed, model.trim()])] })); setNotice('模型已添加'); })}>添加</button></div>}
          {listed.filter(id => !model || id.toLowerCase().includes(model.toLowerCase())).map(id => <div className="model-row" key={id}><span title={id}>{id}</span><button type="button" className="text-button" disabled={busy || !saved || dirty} onClick={() => void run(async () => { await request('/api/ai/test', { providerId: current, model: id }); setNotice('模型连接测试成功'); }, '测试连接中…')}>测试</button><button type="button" className="text-button" disabled={busy || !saved || dirty} aria-label={`设 ${id} 为默认模型`} onClick={() => void run(async () => { const withModels = await request<AiSettings>('/api/ai/providers', { id: current, name, protocol, baseUrl, apiKey: null, models: listed }); setSettings(withModels); accept(await request<AiSettings>('/api/ai/default', { providerId: current, model: id })); setNotice('默认模型已更新'); })}><Star size={15} fill={defaultModel?.providerId === current && defaultModel.model === id ? 'currentColor' : 'none'} />{defaultModel?.providerId === current && defaultModel.model === id ? '默认' : '设为默认'}</button></div>)}
          {!listed.length && <p className="model-empty">{saved ? '获取模型列表，或手动输入模型 ID。' : current === 'magpie' ? '保存网关配置后，点击「获取模型」，或填写 Magpie 中的完整模型 ID。' : current === 'ollama' ? '保存配置后，这里会列出 Ollama 的模型。' : `填好 key 并保存后，这里会列出 ${displayName} 的模型。`}</p>}
        </section>
        </div>
      </div>
    </div>}
  </Sheet>;
}
