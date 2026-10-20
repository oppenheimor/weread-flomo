import { useEffect, useRef, useState } from 'react';
import { ArrowUpRight, Eye, EyeOff, RefreshCw, Trash2, X } from 'lucide-react';
import type { FormEvent } from 'react';
import type { ConfigStatus } from './types.ts';
import { exclusive, request } from './api.ts';
import { Sheet } from './Sheet.tsx';

type Provider = 'weread' | 'flomo';
interface Props { config: ConfigStatus | null; onChanged: (status: ConfigStatus) => void; onClose: () => void }
const emptyValues = () => ({ weread: '', flomo: '' });
export function CredentialSettings({ config, onChanged, onClose }: Props) {
  const [values, setValues] = useState(emptyValues);
  const [revealed, setRevealed] = useState(emptyValues);
  const [draftVisible, setDraftVisible] = useState({ weread: false, flomo: false });
  const [revealing, setRevealing] = useState<Provider | null>(null);
  const revealRequest = useRef<AbortController | null>(null);
  const [editing, setEditing] = useState<Provider | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [removing, setRemoving] = useState<Provider | null>(null);
  const [firstRun] = useState(Boolean(config && !config.wereadConfigured && !config.flomoConfigured));
  const configured = (provider: Provider) => Boolean(provider === 'weread' ? config?.wereadConfigured : config?.flomoConfigured);
  const name = (provider: Provider) => provider === 'weread' ? '微信读书' : 'flomo';

  function conceal() {
    revealRequest.current?.abort(); revealRequest.current = null;
    setRevealed(emptyValues()); setRevealing(null);
    setDraftVisible({ weread: false, flomo: false });
  }
  useEffect(() => {
    const hide = () => conceal();
    const visibility = () => { if (document.hidden) hide(); };
    window.addEventListener('blur', hide);
    document.addEventListener('visibilitychange', visibility);
    return () => {
      revealRequest.current?.abort();
      window.removeEventListener('blur', hide);
      document.removeEventListener('visibilitychange', visibility);
    };
  }, []);

  async function toggleVisibility(provider: Provider, editable: boolean) {
    if (editable) {
      setDraftVisible(previous => ({ ...previous, [provider]: !previous[provider] }));
      return;
    }
    if (revealed[provider] || revealing === provider) { conceal(); return; }
    conceal(); setError('');
    const controller = new AbortController(); revealRequest.current = controller;
    setRevealing(provider);
    try {
      const result = await request<{ value: string }>('/api/account/credentials/reveal', { provider }, controller.signal);
      if (!controller.signal.aborted) setRevealed(previous => ({ ...previous, [provider]: result.value }));
    } catch (e) {
      if (!controller.signal.aborted) setError(e instanceof Error ? e.message : '读取凭证失败，请重试。');
    } finally {
      if (revealRequest.current === controller) { revealRequest.current = null; setRevealing(null); }
    }
  }

  function startEditing(provider: Provider) {
    conceal(); setValues(previous => ({ ...previous, [provider]: '' })); setEditing(provider); setRemoving(null); setError(''); setSaved('');
  }
  async function save(event: FormEvent, provider?: Provider) {
    event.preventDefault();
    if (busy || (provider && !values[provider].trim())) return;
    conceal(); setBusy(true); setError(''); setSaved('');
    try {
      const result = await exclusive(() => request<ConfigStatus>('/api/account/credentials', {
        ...((!provider || provider === 'weread') && values.weread.trim() ? { wereadApiKey: values.weread.trim() } : {}),
        ...((!provider || provider === 'flomo') && values.flomo.trim() ? { flomoApiUrl: values.flomo.trim() } : {}),
      }));
      setValues(previous => provider ? { ...previous, [provider]: '' } : emptyValues());
      setEditing(null); setRemoving(null); onChanged(result);
      if (firstRun && result.wereadConfigured) onClose();
      else setSaved(`${provider ? name(provider) : '凭证'}已保存。`);
    } catch (e) { setError(e instanceof Error ? e.message : '保存失败'); }
    finally { setBusy(false); }
  }
  async function remove(provider: Provider) {
    if (busy) return;
    conceal(); setBusy(true); setError(''); setSaved('');
    try {
      const result = await exclusive(() => request<ConfigStatus>('/api/account/credentials/delete', { provider }));
      setValues(previous => ({ ...previous, [provider]: '' }));
      setRemoving(null); setEditing(provider); onChanged(result);
      setSaved(`${name(provider)}凭证已移除，已同步记录保留。`);
    } catch (e) { setError(e instanceof Error ? e.message : '移除失败'); }
    finally { setBusy(false); }
  }
  const field = (provider: Provider) => {
    const isWeread = provider === 'weread';
    const isConfigured = configured(provider);
    const editable = firstRun || !isConfigured || editing === provider;
    const visible = editable ? draftVisible[provider] : Boolean(revealed[provider]);
    const id = isWeread ? 'weread-key' : 'flomo-url';
    return <section className="credential-field" aria-labelledby={`${id}-title`}>
      <div className="credential-summary">
        <div>
          <div className="credential-title-row">
            <h3 id={`${id}-title`}>{isWeread && <><span className="required-mark" aria-hidden="true">* </span><span className="sr-only">必填：</span></>}{name(provider)}</h3>
            <span className={`credential-status ${isConfigured ? 'is-on' : ''}`}>{isConfigured ? '已保存' : '未配置'}</span>
          </div>
          <p className="credential-kind">{isWeread ? 'API Key · 用于读取书籍' : 'API 地址 · 用于发送笔记'}</p>
        </div>
        {!editable && <div className="credential-summary-actions">
          <button type="button" className="text-button credential-change" disabled={busy} onClick={() => startEditing(provider)}>
            <RefreshCw size={14} strokeWidth={1.75} aria-hidden="true" />更换凭证
          </button>
          <button type="button" className="text-button credential-change danger" disabled={busy} onClick={() => {
            conceal(); setRemoving(provider); setError(''); setSaved('');
          }}><Trash2 size={14} strokeWidth={1.75} aria-hidden="true" />移除凭证</button>
        </div>}
      </div>
      <div className="credential-editor">
        <label className="sr-only" htmlFor={id}>{name(provider)}{isWeread ? ' API Key' : ' API 地址'}</label>
        <div className="credential-input-row">
        <div className="credential-input-wrap">
          <input id={id} type={visible ? 'text' : 'password'} autoComplete="off" spellCheck={false}
            autoCapitalize="none" maxLength={isWeread ? 4096 : 2048} disabled={busy} readOnly={!editable} required={firstRun && isWeread}
            placeholder={isWeread ? '粘贴以 wrk- 开头的 Key' : '粘贴 https://flomoapp.com/iwh/… 地址'}
            value={editable ? values[provider] : revealed[provider] || '••••••••••••'}
            onChange={e => { if (editable) setValues(previous => ({ ...previous, [provider]: e.target.value })); }} />
          <button type="button" className="icon-button credential-eye" disabled={busy || (editable && !values[provider])}
            aria-label={`${visible || revealing === provider ? '隐藏' : '显示'}${name(provider)}凭证`}
            aria-pressed={visible} aria-controls={id} aria-busy={revealing === provider}
            onClick={() => void toggleVisibility(provider, editable)}>
            {visible ? <EyeOff size={17} aria-hidden="true" /> : <Eye size={17} aria-hidden="true" />}
          </button>
        </div>
        {editable && !firstRun && <button type="submit" className="primary credential-save" disabled={busy || !values[provider].trim()}>{busy ? '保存中…' : '保存'}</button>}
        </div>
        <span className="sr-only" role="status">{revealing === provider ? '正在读取凭证…' : ''}</span>
        {editable && <>
          <div className="field-links">
            <a href={isWeread ? 'https://weread.qq.com/r/weread-skills' : 'https://flomoapp.com/mine?source=incoming_webhook'} target="_blank" rel="noreferrer">
              {isWeread ? '获取 Key' : '获取 API 地址'}<ArrowUpRight size={14} strokeWidth={2.25} aria-hidden="true" />
            </a>
          </div>
        </>}
      </div>
    </section>;
  };
  return <><Sheet className="modal" labelledBy="settings-title" locked={busy} onClose={onClose}>
    <header className="sheet-header">
      <h2 id="settings-title" tabIndex={-1}>{firstRun ? '连接你的账号' : '账号设置'}</h2>
      <button type="button" className="icon-button" aria-label="关闭" disabled={busy} onClick={onClose}><X size={18} strokeWidth={2.25} aria-hidden="true" /></button>
    </header>
    <p className="hint">{firstRun ? '先连接微信读书，flomo 可稍后配置。' : '管理微信读书和 flomo 的连接凭证。'}</p>
    {!config && <p className="notice" role="status">正在读取配置，请稍候。</p>}
    {error && !removing && <p className="notice error" role="alert">{error}</p>}
    {saved && <p className="notice" role="status">{saved}</p>}
    {config && (firstRun ? <form className="credential-list" onSubmit={event => save(event)}>
      {field('weread')}{field('flomo')}
      <p className="hint credential-security">凭证加密保存在服务器，默认隐藏。</p>
      <div className="modal-actions">
        <button type="submit" className="primary" disabled={busy || !values.weread.trim()}>{busy ? '保存中…' : '保存并开始'}</button>
      </div>
    </form> : <>
      <div className="credential-list">
        {(['weread', 'flomo'] as const).map(provider => <form key={provider} onSubmit={event => save(event, provider)}>{field(provider)}</form>)}
      </div>
      <p className="hint credential-security">凭证加密保存，点击眼睛可查看；关闭窗口后隐藏。</p>
    </>)}
  </Sheet>
  {removing && <Sheet className="modal credential-confirm" labelledBy="remove-credential-title" describedBy="remove-credential-description"
    role="alertdialog" locked={busy} onClose={() => { setRemoving(null); setError(''); }}>
    <h2 id="remove-credential-title" tabIndex={-1}>移除{name(removing)}凭证？</h2>
    <p id="remove-credential-description" className="hint">
      移除后将无法{removing === 'weread' ? '读取微信读书书籍' : '发送到 flomo'}，重新填写即可恢复。已同步记录不会删除。
    </p>
    {error && <p className="notice error" role="alert">{error}</p>}
    <div className="modal-actions">
      <button type="button" disabled={busy} onClick={() => { setRemoving(null); setError(''); }}>保留凭证</button>
      <button type="button" className="danger-fill" disabled={busy} onClick={() => void remove(removing)}>{busy ? '移除中…' : '确认移除'}</button>
    </div>
  </Sheet>}
  </>;
}
