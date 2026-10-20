import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Circle, ChevronLeft, ChevronRight, RefreshCw, Trash2, Pencil } from 'lucide-react';
import type { Book, PreviewNote, PreviewResponse } from './types.ts';
import { exclusive, request } from './api.ts';
import { BookCover, progressText } from './BookCover.tsx';
import { Sheet } from './Sheet.tsx';

type Tab = 'ready' | 'sent' | 'uncertain';
interface Outcome { sent: number; uncertain: number; remaining: string[]; error?: string }
interface Props {
  book: Book; sendingEnabled: boolean;
  onNavigationStateChange: (state: { busy: boolean; selected: number }) => void;
  onOpenSettings: () => void; onSynced: () => void; onCounted: (bookId: string, ready: number) => void; onClose: () => void;
}

const message = (error: unknown): string => error instanceof Error ? error.message : '操作失败，请重试。';
const LIMIT = 100;

function addedDate(timestamp?: number | null): string | null {
  if (!timestamp || !Number.isFinite(timestamp) || timestamp <= 0) return null;
  const date = new Date(timestamp * 1000);
  if (Number.isNaN(date.getTime())) return null;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function NotesPage({ book, sendingEnabled, onNavigationStateChange, onOpenSettings, onSynced, onCounted, onClose }: Props) {
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [tab, setTab] = useState<Tab>('ready');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [phase, setPhase] = useState<'idle' | 'sending'>('idle');
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const [actionError, setActionError] = useState('');
  const [resolving, setResolving] = useState<{ id: string; delivered: boolean } | null>(null);
  const [resolveBusy, setResolveBusy] = useState(false);
  const [deleting, setDeleting] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [saveBusy, setSaveBusy] = useState(false);
  const [saveError, setSaveError] = useState('');
  const editTrigger = useRef<HTMLButtonElement | null>(null);
  function finishEditing() {
    setEditing(null); setSaveError('');
    requestAnimationFrame(() => editTrigger.current?.focus());
  }
  const [chapterId, setChapterId] = useState<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const content = useRef<HTMLElement>(null);
  const scrollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => () => { if (scrollTimer.current) clearTimeout(scrollTimer.current); }, []);
  function showScrollbar() {
    content.current?.classList.add('is-scrolling');
    if (scrollTimer.current) clearTimeout(scrollTimer.current);
    scrollTimer.current = setTimeout(() => content.current?.classList.remove('is-scrolling'), 800);
  }
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);

  async function load() {
    setLoading(true); setLoadError(''); setOutcome(null); setActionError(''); setPhase('idle');
    try {
      const result = await exclusive(() => request<PreviewResponse>('/api/preview', { bookId: book.bookId }));
      if (!alive.current) return;
      setPreview(result);
      const ready = result.notes.filter(n => n.status === 'ready');
      setSelected(new Set());
      const initialTab: Tab = result.notes.some(n => n.status === 'uncertain' || n.status === 'sending') ? 'uncertain' : ready.length || !result.notes.length ? 'ready' : 'sent';
      setTab(initialTab);
      setChapterId(result.notes.find(n => initialTab === 'uncertain' ? n.status === 'uncertain' || n.status === 'sending' : n.status === initialTab)?.chapter.id ?? null);
    } catch (e) { if (alive.current) setLoadError(message(e)); }
    finally { if (alive.current) setLoading(false); }
  }
  useEffect(() => { void load(); }, [book.bookId]);

  const notes = preview?.notes ?? [];
  const groups: Record<Tab, PreviewNote[]> = {
    ready: notes.filter(n => n.status === 'ready'),
    sent: notes.filter(n => n.status === 'sent'),
    uncertain: notes.filter(n => n.status === 'uncertain' || n.status === 'sending'),
  };
  const chapters = [...new Map(notes.map(n => [n.chapter.id, n.chapter])).values()];
  const chapterIndex = Math.max(0, chapters.findIndex(c => c.id === chapterId));
  const chapter = chapters[chapterIndex];
  const visible = groups[tab].filter(n => n.chapter.id === chapter?.id);
  useEffect(() => { if (preview) onCounted(book.bookId, preview.notes.filter(n => n.status === 'ready').reduce((sum, n) => sum + n.sources, 0)); }, [preview]);
  const sending = phase === 'sending';
  const busy = loading || sending || resolveBusy || deleteBusy || editing !== null;
  useEffect(() => { onNavigationStateChange({ busy, selected: selected.size }); }, [busy, selected.size, onNavigationStateChange]);
  useEffect(() => {
    if (!selected.size && !sending && !resolveBusy && !editing) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [selected.size, sending, resolveBusy, editing]);
  useEffect(() => { content.current?.scrollTo(0, 0); heading.current?.focus({ preventScroll: true }); }, [chapterId, tab, loading]);

  function close() {
    if (!busy && (!selected.size || window.confirm('离开后不会保留已选笔记，确定返回书架？'))) onClose();
  }
  function changeTab(next: Tab) {
    setTab(next); setResolving(null);
    if (!groups[next].some(n => n.chapter.id === chapter?.id)) setChapterId(groups[next][0]?.chapter.id ?? null);
  }
  function changeChapter(index: number) {
    setChapterId(chapters[index].id); setResolving(null);
  }
  function reload() {
    if (!selected.size || window.confirm('重新拉取会清空已选笔记，继续？')) void load();
  }

  function toggle(id: string) {
    setPhase('idle'); setOutcome(null);
    setSelected(previous => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id); else if (next.size < LIMIT) next.add(id);
      return next;
    });
  }
  async function send() {
    if (!preview || !selected.size) return;
    const ids = groups.ready.filter(n => selected.has(n.id)).map(n => n.id);
    setPhase('sending'); setActionError(''); setOutcome(null);
    try {
      const result = await exclusive(() => request<{ notes: PreviewNote[]; deliveryError?: string }>('/api/send', { previewId: preview.previewId, ids, confirmed: true }));
      if (!alive.current) return;
      const status = new Map(result.notes.map(n => [n.id, n.status]));
      setPreview({ ...preview, notes: result.notes });
      const remaining = ids.filter(id => status.get(id) === 'ready');
      setSelected(new Set(remaining));
      setOutcome({
        sent: ids.filter(id => status.get(id) === 'sent').length,
        uncertain: ids.filter(id => status.get(id) === 'uncertain').length,
        remaining, error: result.deliveryError,
      });
      onSynced();
    } catch (e) { if (alive.current) setActionError(message(e)); }
    finally { if (alive.current) setPhase('idle'); }
  }
  async function resolve() {
    if (!preview || !resolving) return;
    setResolveBusy(true); setActionError('');
    try {
      const result = await exclusive(() => request<{ notes: PreviewNote[] }>('/api/resolve', { previewId: preview.previewId, ...resolving }));
      if (!alive.current) return;
      setPreview({ ...preview, notes: result.notes }); setResolving(null); onSynced();
      if (!result.notes.some(n => n.status === 'uncertain')) setTab(resolving.delivered ? 'sent' : 'ready');
    } catch (e) { if (alive.current) setActionError(message(e)); }
    finally { if (alive.current) setResolveBusy(false); }
  }

  async function saveThought() {
    if (!preview || !editing || saveBusy) return;
    setSaveBusy(true); setSaveError('');
    try {
      const result = await exclusive(() => request<{ notes: PreviewNote[] }>('/api/notes/thought', { previewId: preview.previewId, id: editing, thought: draft }));
      if (!alive.current) return;
      setPreview({ ...preview, notes: result.notes }); setOutcome(null); finishEditing();
    } catch (e) { if (alive.current) setSaveError(message(e)); }
    finally { if (alive.current) setSaveBusy(false); }
  }

  async function deleteNote(id: string) {
    if (!preview || busy) return;
    setDeleteBusy(true); setDeleteError('');
    try {
      const result = await exclusive(() => request<{ notes: PreviewNote[] }>('/api/notes/delete', { previewId: preview.previewId, id, confirmed: true }));
      if (!alive.current) return;
      setPreview({ ...preview, notes: result.notes });
      setSelected(previous => { const next = new Set(previous); next.delete(id); return next; });
      setDeleting(null); setOutcome(null); onSynced();
    } catch (e) { if (alive.current) setDeleteError(message(e)); }
    finally { if (alive.current) setDeleteBusy(false); }
  }

  const progress = progressText(book);
  return <main className="review-page">
    <div className="review-topline">
      <button type="button" className="text-button muted" disabled={busy} onClick={close}><ArrowLeft size={16} aria-hidden="true" />书架</button>
      <button type="button" className="text-button muted" disabled={busy} onClick={reload} aria-label="重新拉取" title="重新拉取"><RefreshCw size={16} aria-hidden="true" /></button>
    </div>
    <header className="review-book">
      <BookCover book={book} className="drawer-cover" />
      <div className="drawer-meta">
        <h1>{book.title}</h1>
        <p>{[book.author, progress].filter(Boolean).join(' · ')}</p>
      </div>
    </header>

    {loading && !preview && <section className="notes-loading" aria-label="正在读取笔记" aria-busy="true">
      <p className="notes-loading-status" role="status"><span aria-hidden="true" />正在整理划线与想法</p>
      <div className="notes-loading-preview" aria-hidden="true">
        <div className="notes-loading-heading"><span className="placeholder-line" /><span className="placeholder-line" /></div>
        {[0, 1, 2].map(index => <div className="notes-loading-card" key={index}>
          <div className="notes-loading-meta"><span className="placeholder-line" /><Circle size={20} strokeWidth={1.5} /></div>
          <span className="placeholder-line" />
          <span className="placeholder-line" />
          {index === 1 && <span className="placeholder-line notes-loading-thought" />}
          <span className="placeholder-line notes-loading-date" />
        </div>)}
      </div>
    </section>}
    {loadError && <div className="drawer-empty" role="alert">
      <p className="error-text">{loadError}</p>
      <button type="button" onClick={load}>重试</button>
    </div>}

    {preview && <>
      <div className="tabs" role="group" aria-label="笔记状态">
        {(['ready', 'sent', 'uncertain'] as Tab[]).filter(t => t !== 'uncertain' || groups.uncertain.length).map(t =>
          <button key={t} type="button" aria-pressed={tab === t} disabled={busy} className={`tab tab-${t}`} onClick={() => changeTab(t)}>
            {{ ready: '未同步', sent: '已同步', uncertain: '需核对' }[t]}<span>{groups[t].length}</span>
          </button>)}
      </div>

      <section ref={content} className="review-content" onScroll={showScrollbar} aria-busy={loading}>
        {chapter && <header className="chapter-heading">
          <span className="chapter-progress">{chapterIndex + 1} / {chapters.length}</span>
          <h2 ref={heading} tabIndex={-1}>{chapter.title}</h2>
          <span className="chapter-count">{visible.length} 条</span>
        </header>}
        {preview.warnings.length > 0 && <div className="notice" role="status">{preview.warnings.join('；')}。数量以下方列表为准。</div>}
        {tab === 'uncertain' && <p className="hint uncertain-hint">这些笔记发送时没有收到 flomo 的成功回执，可能已经写入。请到 flomo 搜索确认后选择处理方式，系统不会自动重发。</p>}
        {!visible.length && <p className="drawer-empty-small">{{
          ready: notes.length ? '本章没有未同步笔记' : '暂无划线或想法',
          sent: '本章暂无已同步笔记',
          uncertain: '本章没有需要核对的笔记',
        }[tab]}</p>}

        <ol className="notes">
          {visible.map((note, index) => {
            const checked = selected.has(note.id);
            const selectable = tab === 'ready';
            const date = addedDate(note.createTime);
            const body = <>
              <div className="note-kind">
                <span className="note-actions note-selection">
                  {selectable
                    ? <label className="note-icon-action note-checkbox-action" title={checked ? '取消选择' : '选择笔记'}><input type="checkbox" aria-label={`选择笔记 ${index + 1}`} checked={checked} disabled={busy || (!checked && selected.size >= LIMIT)} onChange={() => toggle(note.id)} /></label>
                    : <span className={`status-dot status-${note.status}`} aria-label={note.status === 'sent' ? '已同步' : '需核对'} />}
                </span>
                <span className="note-number" aria-hidden="true">摘录 {String(index + 1).padStart(2, '0')}</span>{note.kind === 'review' && <span>想法 / 书评</span>}
              </div>
              {note.quote && <blockquote>{note.quote}</blockquote>}
              {editing === note.id ? <form className="thought-editor" onSubmit={e => { e.preventDefault(); void saveThought(); }}>
                <textarea id="thought-draft" aria-label={note.thought ? '编辑想法' : '添加想法'} autoFocus value={draft} maxLength={10000} rows={4} disabled={saveBusy} onChange={e => setDraft(e.target.value)} placeholder="写下你的想法…" />
                {saveError && <p className="error-text" role="alert">{saveError}</p>}
                <div className="thought-editor-actions"><button type="button" disabled={saveBusy} onClick={() => { if (draft === note.thought || window.confirm('放弃未保存的想法？')) finishEditing(); }}>取消</button><button type="submit" className="primary" disabled={saveBusy || (!note.quote && !draft.trim())}>{saveBusy ? '保存中…' : '保存'}</button></div>
              </form> : note.thought && <p className="thought">{note.thought}</p>}
              {date && <time className="note-date" dateTime={date}>{date} 添加</time>}
            </>;
            return <li key={note.id} className={`note ${checked ? 'is-selected' : ''} ${selectable ? '' : 'is-static'}`}>
              <div className="note-select"><div className="note-body">{body}</div></div>
              <div className="note-actions" role="group" aria-label={`笔记 ${index + 1} 操作`}>
                <button type="button" className="note-icon-action" disabled={busy} title={note.thought ? '编辑想法' : '添加想法'} aria-label={`${note.thought ? '编辑' : '添加'}笔记 ${index + 1} 的想法`} aria-expanded={editing === note.id} onClick={e => { editTrigger.current = e.currentTarget; setEditing(note.id); setDraft(note.thought); setSaveError(''); }}><Pencil size={21} strokeWidth={1.5} aria-hidden="true" /></button>
                <button type="button" className="note-icon-action note-delete-action" disabled={busy} aria-label={`删除笔记 ${index + 1}`} title="删除笔记" aria-haspopup="dialog" onClick={() => { setDeleting(deleting === note.id ? null : note.id); setDeleteError(''); }}><Trash2 size={21} strokeWidth={1.5} aria-hidden="true" /></button>
              </div>
              {tab === 'uncertain' && <div className="resolve">
                {resolving?.id === note.id
                  ? <div className="inline-confirm" role="group" aria-label="确认核对结果">
                      <p>{resolving.delivered ? '确认 flomo 里已有这条？将标记为已同步。' : '确认 flomo 里没有这条？将放回“未同步”，需要你再次手动发送。'}</p>
                      <div><button type="button" disabled={resolveBusy} onClick={() => setResolving(null)}>取消</button><button type="button" className="primary" disabled={resolveBusy} onClick={resolve}>{resolveBusy ? '处理中…' : '确认'}</button></div>
                    </div>
                  : <><button type="button" disabled={busy} onClick={() => setResolving({ id: note.id, delivered: true })}>flomo 里已有</button><button type="button" disabled={busy} onClick={() => setResolving({ id: note.id, delivered: false })}>flomo 里没有</button></>}
              </div>}
            </li>;
          })}
        </ol>
      </section>

      <footer className="action-bar review-footer" aria-live="polite">
        {actionError && <p className="bar-message error-text" role="alert">{actionError}</p>}
        {outcome && <p className={`bar-message ${outcome.uncertain || outcome.error ? 'error-text' : 'success-text'}`} role="status">
          {outcome.sent > 0 && `已发送 ${outcome.sent} 条。`}
          {outcome.uncertain > 0 && `${outcome.uncertain} 条结果不确定，已停止；请在“需核对”中处理。`}
          {outcome.error && !outcome.uncertain && outcome.error}
          {outcome.remaining.length > 0 && ` 剩余 ${outcome.remaining.length} 条未发送，仍保持勾选。`}
        </p>}
        <div className="bar-row">
              <nav className="chapter-nav" aria-label="章节翻页">
                <button type="button" className="text-button" disabled={busy || chapterIndex === 0} onClick={() => changeChapter(chapterIndex - 1)}><ChevronLeft size={16} aria-hidden="true" />上一章</button>
                <button type="button" className="text-button" disabled={busy || chapterIndex >= chapters.length - 1} onClick={() => changeChapter(chapterIndex + 1)}>下一章<ChevronRight size={16} aria-hidden="true" /></button>
              </nav>
              <p>{sending ? `正在发送 ${selected.size} 条，请勿关闭页面…`
                : !sendingEnabled ? <>还没连接 flomo，<button type="button" className="link-button" onClick={() => { if (!selected.size || window.confirm('前往设置会清空已选笔记，继续？')) onOpenSettings(); }}>去设置</button></>
                : tab === 'ready' ? <>已选 <strong>{selected.size}</strong> 条{selected.size >= LIMIT && '（单次上限 100 条）'}</>
                : `未同步 ${groups.ready.length} 条`}</p>
              <div className="bar-buttons">
                <button type="button" className="primary" disabled={busy || !selected.size || !sendingEnabled} onClick={send}>{sending ? '发送中…' : `发送到 flomo${selected.size ? ` · ${selected.size}` : ''}`}</button>
              </div>
            </div>
      </footer>
    </>}
    {deleting && <Sheet className="modal note-delete-confirm" role="alertdialog" labelledBy="delete-note-title" describedBy="delete-note-description" locked={deleteBusy} onClose={() => { setDeleting(null); setDeleteError(''); }}>
      <h2 id="delete-note-title" tabIndex={-1}>删除这条笔记？</h2>
      <p id="delete-note-description" className="hint">笔记及附带的想法将从拾墨移除，重新拉取也不会显示，暂不支持恢复。微信读书原文和 flomo 已有内容不受影响。</p>
      {deleteError && <p className="error-text" role="alert">{deleteError}</p>}
      <div className="modal-actions">
        <button type="button" disabled={deleteBusy} onClick={() => { setDeleting(null); setDeleteError(''); }}>取消</button>
        <button type="button" className="danger-button" disabled={deleteBusy} onClick={() => deleteNote(deleting)}>{deleteBusy ? '删除中…' : '确认删除'}</button>
      </div>
    </Sheet>}
  </main>;
}
