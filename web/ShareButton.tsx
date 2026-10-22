import { useEffect, useId, useRef, useState } from 'react';
import { Check, Copy, ExternalLink, Link2, RefreshCw, Share2, X } from 'lucide-react';
import { request } from './api.ts';
import { Sheet } from './Sheet.tsx';
import { Tooltip } from './Tooltip.tsx';
import type { Book } from './types.ts';
import './share.css';

interface State { enabled: boolean; token: string | null }
export function ShareButton({ book, disabled = false }: { book?: Book; disabled?: boolean }) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  function close() { setOpen(false); requestAnimationFrame(() => trigger.current?.focus()); }
  const button = <button ref={trigger} type="button" className="icon-button" aria-label={book ? '分享本书' : '分享我的书架'} disabled={disabled} onClick={() => setOpen(true)}>
    <Share2 size={18} aria-hidden="true" />
  </button>;
  return <><Tooltip label={book ? '分享本书' : '分享我的书架'}>{button}</Tooltip>{open && <SharePanel book={book} onClose={close} />}</>;
}
function SharePanel({ book, onClose }: { book?: Book; onClose: () => void }) {
  const id = useId();
  const [state, setState] = useState<State | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [retry, setRetry] = useState(0);
  const url = state?.token ? new URL(`${import.meta.env.BASE_URL}share/${state.token}`, window.location.origin).href : '';
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError('');
    request<State>(`/api/shares${book ? `?bookId=${encodeURIComponent(book.bookId)}` : ''}`, undefined, controller.signal)
      .then(setState).catch(() => { if (!controller.signal.aborted) setError('暂时无法读取分享状态'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [book?.bookId, retry]);
  async function copy(link: string) {
    try { await navigator.clipboard.writeText(link); setFeedback('已复制链接'); }
    catch { setFeedback('分享已开启，请选中下方链接手动复制。'); }
  }
  async function change(enabled: boolean) {
    setBusy(true); setError(''); setFeedback('');
    try {
      const next = await request<State>('/api/shares', { bookId: book?.bookId ?? null, enabled });
      setState(next); setConfirm(false);
      if (enabled && next.token) await copy(new URL(`${import.meta.env.BASE_URL}share/${next.token}`, window.location.origin).href);
    } catch (e) { setError(e instanceof Error ? e.message : '操作失败，请重试'); }
    finally { setBusy(false); }
  }
  return <Sheet className="modal share-panel" labelledBy={id} locked={busy} onClose={onClose}>
    <header className="sheet-header"><h2 id={id} tabIndex={-1}>{confirm ? '关闭分享？' : book ? '分享这本书' : '分享我的书架'}</h2>
      <button type="button" className="icon-button" aria-label="关闭分享面板" disabled={busy} onClick={onClose}><X size={19} /></button></header>
    {book && <p className="share-book-name">《{book.title}》</p>}
    {loading ? <p className="share-description" role="status">正在读取分享状态…</p> : confirm ? <>
      <p className="share-description">关闭后，当前链接将永久失效。再次开启会生成新链接。</p>
      <div className="modal-actions"><button type="button" disabled={busy} onClick={() => { setConfirm(false); setError(''); }}>保留分享</button><button type="button" className="danger-fill" disabled={busy} onClick={() => void change(false)}>{busy ? '正在关闭…' : '确认关闭'}</button></div>
    </> : state ? <>
      <p className="share-description">{book ? '把这本书分享给朋友，一起聊聊阅读中的收获。' : '把书架分享给朋友，一起看看阅读中的收获。'}<span>{book ? '打开链接即可浏览本书的划线和想法，新内容也会随之更新。' : '打开链接即可浏览书籍、划线和想法，新内容也会随之更新。'}</span></p>
      {state.enabled ? <>
        <div className="share-status"><Link2 size={17} aria-hidden="true" /><span>分享已开启</span><button type="button" className="text-button danger share-disable" onClick={() => { setConfirm(true); setFeedback(''); }}>关闭分享</button></div>
        <label className="sr-only" htmlFor={`${id}-url`}>分享链接</label>
        <div className="share-link-row"><input id={`${id}-url`} readOnly value={url} onFocus={e => e.target.select()} /></div>
        <div className="share-panel-footer"><a href={url} target="_blank" rel="noopener noreferrer">预览分享页<ExternalLink size={14} aria-hidden="true" /><span className="sr-only">（新窗口）</span></a><button type="button" className="primary" onClick={() => void copy(url)}>{feedback === '已复制链接' ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}复制链接</button></div>
      </> : <><button type="button" className="primary share-enable" disabled={busy} onClick={() => void change(true)}><Link2 size={17} aria-hidden="true" />{busy ? '正在开启…' : '开启分享并复制链接'}</button><p className="share-enable-hint">获得链接的人均可查看，请留意分享范围。</p></>}
    </> : <div className="share-load-error">
      <div role="alert"><p className="share-error-title">{error || '暂时无法读取分享状态'}</p><p className="share-error-help">服务可能暂时不可用，请稍后重试。<br />已有分享的设置不会因此改变。</p></div>
      <div className="share-retry-actions"><button type="button" onClick={() => setRetry(v => v + 1)}><RefreshCw size={15} aria-hidden="true" />重新读取</button></div>
    </div>}
    <p className={feedback === '已复制链接' ? 'sr-only' : 'share-feedback'} role="status">{feedback}</p>
    {error && state && <p className="share-action-error" role="alert">{error}</p>}
  </Sheet>;
}
