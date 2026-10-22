import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ArrowLeft, Circle, ChevronLeft, ChevronRight, RefreshCw, Trash2, Pencil, Sparkles, ArrowDown } from 'lucide-react';
import type { Book, PreviewNote, PreviewResponse } from './types.ts';
import { exclusive, request } from './api.ts';
import { BookCover, progressText } from './BookCover.tsx';
import { Sheet } from './Sheet.tsx';
import { EmptyState } from './EmptyState.tsx';
import { ChapterMenu } from './ChapterMenu.tsx';
import { Tooltip } from './Tooltip.tsx';
import { useSyncProgress } from './useSyncProgress.ts';
import { AiChapterNotes } from './AiCompanion.tsx';
import { AiWriting } from './AiWriting.tsx';
import { startChapterAi } from './ai-request.ts';
import type { AiChapter } from './ai-types.ts';
import './chapter-tools.css';

type Tab = 'ready' | 'sent' | 'uncertain';
interface Outcome { sent: number; uncertain: number; remaining: string[]; error?: string }
interface Props {
  book: Book; sendingEnabled: boolean;
  aiThinkingControl: boolean; aiConfigured: boolean; onOpenModels: () => void;
  onNavigationStateChange: (state: { busy: boolean; selected: number; dirty: boolean }) => void;
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

export function NotesPage({ book, sendingEnabled, aiThinkingControl, aiConfigured, onOpenModels, onNavigationStateChange, onOpenSettings, onSynced, onCounted, onClose }: Props) {
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [tab, setTab] = useState<Tab>('ready');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [phase, setPhase] = useState<'idle' | 'sending'>('idle');
  const [syncIds, setSyncIds] = useState<string[] | null>(null);
  const syncProgress = useSyncProgress(syncIds);
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
  const [aiView, setAiView] = useState(false);
  const [deepThinking, setDeepThinking] = useState(false);
  useEffect(() => { setDeepThinking(false); }, [chapterId, aiThinkingControl]);
  const [aiChapter, setAiChapter] = useState<AiChapter | null>(null);
  const [savedChapters, setSavedChapters] = useState<PreviewNote['chapter'][]>([]);
  const positionWrites = useRef<Promise<unknown>>(Promise.resolve());
  function rememberChapter(id: string) {
    positionWrites.current = positionWrites.current.catch(() => {}).then(() => request('/api/book-position', { bookId: book.bookId, chapterId: id })).catch(() => {
      if (alive.current) setActionError('章节位置保存失败，下次打开可能无法恢复。');
    });
  }
  const [aiSelected, setAiSelected] = useState<string[]>([]);
  const [aiDirty, setAiDirty] = useState(false);
  const [aiBusy, setAiBusy] = useState(false);
  const browserAi = useRef<AbortController | null>(null);
  useEffect(() => () => browserAi.current?.abort(), []);
  const [aiError, setAiError] = useState('');
  const [aiRefresh, setAiRefresh] = useState(0);
  const [aiEditorKey, setAiEditorKey] = useState(0);
  const activeChapter = useRef(chapterId); activeChapter.current = chapterId;
  const aiRunning = aiChapter?.state === 'running';
  useEffect(() => { setAiView(false); setAiChapter(null); setAiSelected([]); setAiError(''); setAiDirty(false); }, [chapterId]);
  useEffect(() => {
    if (chapterId === null) return;
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const value = await request<AiChapter>(`/api/ai/chapter?bookId=${encodeURIComponent(book.bookId)}&chapterId=${encodeURIComponent(chapterId!)}`, undefined, controller.signal);
        if (controller.signal.aborted) return;
        setAiChapter(value); setAiError('');
        if (value.state === 'running') timer = setTimeout(poll, value.progress?.phase === 'receiving' || value.progress?.phase === 'validating' ? 80 : 350);
      } catch (e) { if (!controller.signal.aborted) setAiError(message(e)); }
    }
    void poll(); return () => { controller.abort(); clearTimeout(timer); };
  }, [chapterId, book.bookId, aiRefresh]);
  function discardAiEdit() {
    if (aiDirty && !window.confirm('有未保存的修改，放弃修改并继续？')) return false;
    if (aiDirty) { setAiDirty(false); setAiEditorKey(v => v + 1); }
    return true;
  }
  async function startAi(replace = false) {
    if (!preview || chapterId === null || !discardAiEdit()) return;
    if (!aiConfigured) { onOpenModels(); return; }
    if (replace && aiChapter?.notes.some(n => n.edited && n.status === 'ready') && !window.confirm('重新整理会替换未同步的整理结果及手动修改。成功后才替换，失败保留旧结果。继续？')) return;
    const target = chapterId;
    setAiBusy(true); setAiError('');
    const controller = new AbortController(); browserAi.current = controller;
    try {
      const result = await exclusive(() => startChapterAi({ previewId: preview.previewId, bookId: book.bookId, chapterId: target, replaceConfirmed: true, deepThinking: aiThinkingControl && deepThinking }, request, fresh => {
        if (alive.current && activeChapter.current === target) {
          setPreview(fresh);
          setSelected(old => new Set([...old].filter(id => fresh.notes.some(n => n.id === id && n.status === 'ready'))));
        }
      }, state => {
        if (alive.current && activeChapter.current === target) {
          setAiChapter(state); setAiView(true); setTab('ready'); setAiSelected([]);
        }
      }, controller.signal));
      if (!alive.current || activeChapter.current !== target) return;
      followWriting.current = true; setFollowing(true);
      setAiChapter(result); setAiView(true); setTab('ready'); setAiSelected([]); setAiRefresh(v => v + 1);
    } catch(e) { if (alive.current && activeChapter.current === target) setAiError(message(e)); }
    finally { if (alive.current) setAiBusy(false); }
  }
  function changeAiView(showOrganized: boolean) {
    if (aiView === showOrganized || !discardAiEdit()) return;
    setAiView(showOrganized);
  }
  async function refreshOriginals() {
    if (!preview) return;
    try {
      const result = await exclusive(() => request<{ notes: PreviewNote[] }>('/api/ai/refresh-preview', { previewId: preview.previewId }));
      if (alive.current) { setPreview(p => p ? { ...p, notes: result.notes } : p); setSelected(old => new Set([...old].filter(id => result.notes.some(n => n.id === id && n.status === 'ready')))); }
    } catch(e) { if (alive.current) setActionError(message(e)); }
    onSynced();
  }
  function aiChanged(value: AiChapter) { setAiChapter(value); void refreshOriginals(); }
  async function sendAi() {
    if (chapterId === null || !aiSelected.length || aiRunning) return;
    setAiBusy(true); setActionError(''); setSyncIds([...aiSelected]);
    try {
      const result = await exclusive(() => request<{ chapter: AiChapter; error: string }>('/api/ai/send', { bookId: book.bookId, chapterId, ids: aiSelected, confirmed: true }));
      if (!alive.current) return;
      setAiChapter(result.chapter); setAiSelected(old => old.filter(id => result.chapter.notes.some(n => n.id === id && n.status === 'ready'))); setActionError(result.error);
      await refreshOriginals();
    } catch(e) { if (alive.current) { setActionError(message(e)); setAiRefresh(v => v + 1); } }
    finally { if (alive.current) { setAiBusy(false); setSyncIds(null); } }
  }
  const heading = useRef<HTMLHeadingElement>(null);
  const content = useRef<HTMLElement>(null);
  const followWriting = useRef(true);
  const previousScroll = useRef(0);
  const wasWriting = useRef(false);
  const scrollFrame = useRef<number | null>(null);
  function cancelFollowAnimation() {
    if (scrollFrame.current !== null) cancelAnimationFrame(scrollFrame.current);
    scrollFrame.current = null;
  }
  function animateToWriting() {
    cancelFollowAnimation();
    const step = () => {
      const node = content.current;
      if (!node || !followWriting.current) { scrollFrame.current = null; return; }
      const gap = node.scrollHeight - node.clientHeight - node.scrollTop;
      if (node.scrollTop < previousScroll.current - 2 && gap > 2) {
        followWriting.current = false; setFollowing(false); scrollFrame.current = null; return;
      }
      const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      node.scrollTop += reduce || gap <= 2 ? gap : Math.min(80, Math.max(1, gap * .25));
      previousScroll.current = node.scrollTop;
      scrollFrame.current = reduce || gap <= 2 ? null : requestAnimationFrame(step);
    };
    scrollFrame.current = requestAnimationFrame(step);
  }
  useEffect(() => () => cancelFollowAnimation(), []);
  const [following, setFollowing] = useState(true);
  const writingRevision = JSON.stringify(aiChapter?.drafts ?? []);
  useLayoutEffect(() => { cancelFollowAnimation(); followWriting.current = true; setFollowing(true); previousScroll.current = content.current?.scrollTop ?? 0; }, [chapterId, aiView]);
  useLayoutEffect(() => {
    const writing = aiView && aiChapter?.state === 'running';
    const justCompleted = aiView && wasWriting.current && aiChapter?.state === 'ready';
    if ((writing || justCompleted) && followWriting.current && content.current) {
      const node = content.current;
      const gap = node.scrollHeight - node.clientHeight - node.scrollTop;
      // Respect an upward gesture even when its queued scroll event has not fired yet.
      if (node.scrollTop < previousScroll.current - 2 && gap > 2) {
        followWriting.current = false; setFollowing(false);
      } else animateToWriting();
      previousScroll.current = node.scrollTop;
    }
    wasWriting.current = writing;
  }, [writingRevision, aiChapter?.state, aiView]);
  function stopFollowing() {
    if (!aiView) return;
    cancelFollowAnimation(); followWriting.current = false; setFollowing(false);
  }
  function handleScroll() {
    showScrollbar();
    const node = content.current;
    if (!node) return;
    if ((aiRunning || scrollFrame.current !== null) && aiView) {
      const gap = node.scrollHeight - node.scrollTop - node.clientHeight;
      // Removing the sticky return button can clamp scrollTop downward. That is not a user scroll.
      if (gap <= 2) { followWriting.current = true; setFollowing(true); }
      else if (node.scrollTop < previousScroll.current - 2) stopFollowing();
      else if (gap < 28) { followWriting.current = true; setFollowing(true); }
    }
    previousScroll.current = node.scrollTop;
  }
  function returnToWriting() {
    followWriting.current = true; setFollowing(true);
    if (content.current) { previousScroll.current = content.current.scrollTop; animateToWriting(); }
  }
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
      const [result, position, storedChapters] = await Promise.all([
        exclusive(() => request<PreviewResponse>('/api/preview', { bookId: book.bookId })),
        positionWrites.current.then(() => request<{ chapterId: string }>(`/api/book-position?bookId=${encodeURIComponent(book.bookId)}`)).catch(() => null),
        request<PreviewNote['chapter'][]>(`/api/ai/chapters?bookId=${encodeURIComponent(book.bookId)}`).catch(() => []),
      ]);
      if (!alive.current) return;
      setPreview(result); setSavedChapters(storedChapters);
      const ready = result.notes.filter(n => n.status === 'ready');
      setSelected(new Set());
      const initialTab: Tab = result.notes.some(n => n.status === 'uncertain' || n.status === 'sending') ? 'uncertain' : ready.length || !result.notes.length ? 'ready' : 'sent';
      setTab(initialTab);
      const remembered = position?.chapterId;
      const exists = remembered && (result.notes.some(n => n.chapter.id === remembered) || storedChapters.some(c => c.id === remembered));
      setChapterId(exists ? remembered : result.notes.find(n => initialTab === 'uncertain' ? n.status === 'uncertain' || n.status === 'sending' : n.status === initialTab)?.chapter.id ?? null);
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
  const chapters = [...new Map([...savedChapters.map(c => [c.id, c] as const), ...notes.map(n => [n.chapter.id, n.chapter] as const)]).values()].sort((a, b) => a.order - b.order);
  useEffect(() => { if (chapterId === null && chapters.length) setChapterId(chapters[0].id); }, [chapterId, chapters]);
  const chapterIndex = Math.max(0, chapters.findIndex(c => c.id === chapterId));
  const chapter = chapters[chapterIndex];
  const visible = groups[tab].filter(n => n.chapter.id === chapter?.id);
  useEffect(() => { if (preview) onCounted(book.bookId, preview.notes.filter(n => n.status === 'ready').reduce((sum, n) => sum + n.sources, 0)); }, [preview]);
  const nextReadyChapterIndex = chapters.findIndex((c, index) => index > chapterIndex && groups.ready.some(n => n.chapter.id === c.id));
  const otherReadyChapterIndex = nextReadyChapterIndex >= 0 ? nextReadyChapterIndex : chapters.findIndex(c => c.id !== chapter?.id && groups.ready.some(n => n.chapter.id === c.id));
  const sending = phase === 'sending';
  const busy = loading || sending || resolveBusy || deleteBusy || editing !== null || aiBusy;
  const aiVisible = aiChapter?.notes.filter(n => tab === 'uncertain' ? n.status === 'uncertain' || n.status === 'sending' : n.status === tab) ?? [];
  useEffect(() => { onNavigationStateChange({ busy, selected: selected.size + aiSelected.length, dirty: aiDirty }); }, [busy, selected.size, aiSelected.length, aiDirty, onNavigationStateChange]);
  useEffect(() => {
    if (!selected.size && !sending && !resolveBusy && !editing && !aiDirty && !aiBusy && !aiSelected.length) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [selected.size, sending, resolveBusy, editing, aiDirty, aiBusy, aiSelected.length]);
  useEffect(() => { content.current?.scrollTo(0, 0); heading.current?.focus({ preventScroll: true }); }, [chapterId, tab, loading]);

  function close() {
    if (!busy && discardAiEdit() && (!(selected.size + aiSelected.length) || window.confirm('离开后不会保留已选笔记，确定返回书架？'))) onClose();
  }
  function changeTab(next: Tab) {
    if (!discardAiEdit()) return;
    setTab(next); setResolving(null);
    if (!aiView && !groups[next].some(n => n.chapter.id === chapter?.id)) {
      const id = groups[next][0]?.chapter.id;
      if (id) { setChapterId(id); rememberChapter(id); }
    }
  }
  function changeChapter(index: number) {
    if (!discardAiEdit()) return;
    setChapterId(chapters[index].id); rememberChapter(chapters[index].id); setResolving(null);
  }
  function reload() {
    if (discardAiEdit() && (!selected.size || window.confirm('重新拉取会清空已选笔记，继续？'))) { void load(); setAiRefresh(v => v + 1); }
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
    setPhase('sending'); setActionError(''); setOutcome(null); setSyncIds(ids);
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
      onSynced(); setAiRefresh(v => v + 1);
    } catch (e) { if (alive.current) setActionError(message(e)); }
    finally { if (alive.current) { setPhase('idle'); setSyncIds(null); } }
  }
  async function resolve() {
    if (!preview || !resolving) return;
    setResolveBusy(true); setActionError('');
    try {
      const result = await exclusive(() => request<{ notes: PreviewNote[] }>('/api/resolve', { previewId: preview.previewId, ...resolving }));
      if (!alive.current) return;
      setPreview({ ...preview, notes: result.notes }); setResolving(null); onSynced(); setAiRefresh(v => v + 1);
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
        {(['ready', 'sent', 'uncertain'] as Tab[]).filter(t => t !== 'uncertain' || (aiView ? aiChapter?.notes.some(n => n.status === 'uncertain' || n.status === 'sending') : groups.uncertain.length)).map(t =>
          <button key={t} type="button" aria-pressed={tab === t} disabled={busy} className={`tab tab-${t}`} onClick={() => changeTab(t)}>
            {{ ready: '未同步', sent: '已同步', uncertain: '需核对' }[t]}<span>{aiView ? aiChapter?.notes.filter(n => t === 'uncertain' ? n.status === 'uncertain' || n.status === 'sending' : n.status === t).length ?? 0 : groups[t].length}</span>
          </button>)}
      </div>

      <section ref={content} className="review-content" onScroll={handleScroll} onWheel={e => { if (e.deltaY < 0) stopFollowing(); }} onKeyDown={e => { if (['ArrowUp', 'PageUp', 'Home'].includes(e.key)) stopFollowing(); }} aria-busy={loading}>
        {chapter && <header className="chapter-heading">
          <div className="chapter-identity"><h2 ref={heading} tabIndex={-1}><ChapterMenu chapters={chapters} currentId={chapter.id} disabled={busy} onChange={changeChapter} /></h2></div>
          <div className="chapter-tools">
            {aiChapter && (aiChapter.notes.length > 0 || aiRunning || aiChapter.state === 'failed') ? <div className="chapter-view-switch" role="group" aria-label="笔记视图">
              <button type="button" aria-pressed={!aiView} disabled={busy} onClick={() => changeAiView(false)}>原始笔记</button>
              <div className={`chapter-ai-view${aiView ? ' is-active' : ''}`}>
                <button type="button" aria-pressed={aiView} disabled={busy} onClick={() => changeAiView(true)}><Sparkles size={14} aria-hidden="true" />AI 整理{aiRunning && <span className="chapter-running-dot" aria-label="正在整理" />}</button>
                {aiView && aiChapter.state === 'ready' && <Tooltip label="重新生成"><button type="button" className="chapter-regenerate" aria-label="重新生成" disabled={busy} onClick={() => void startAi(true)}><RefreshCw size={14} aria-hidden="true" /></button></Tooltip>}
              </div>
            </div> : <>
              {aiThinkingControl && <select className="ai-mode-select" aria-label="整理模式" value={deepThinking ? 'deep' : 'fast'} disabled={busy} onChange={e => setDeepThinking(e.target.value === 'deep')}><option value="fast">快速整理</option><option value="deep">深度思考</option></select>}
              <button type="button" className="chapter-organize-button" disabled={busy || !aiChapter} onClick={() => void startAi()}><Sparkles size={14} aria-hidden="true" />AI 整理本章</button>
            </>}
            {aiView && aiChapter?.state === 'ready' && aiThinkingControl && <select className="ai-mode-select" aria-label="下次整理模式" value={deepThinking ? 'deep' : 'fast'} disabled={busy} onChange={e => setDeepThinking(e.target.value === 'deep')}><option value="fast">快速整理</option><option value="deep">深度思考</option></select>}
          </div>
        </header>}
        {aiView && aiRunning && <AiWriting drafts={aiChapter?.drafts ?? []} phase={aiChapter?.progress?.phase} bookTitle={book.title} chapterTitle={chapter?.title ?? ''} />}
        {aiView && aiRunning && !following && <button type="button" className="ai-follow-button" onClick={returnToWriting}><ArrowDown size={14} />回到生成位置</button>}
        {(aiChapter?.state === 'failed' || aiError) && <div className="ai-task-state" role="status"><span>{aiError || aiChapter?.error}</span>{(aiError || !aiRunning) && <button type="button" className="text-button" disabled={busy} onClick={() => aiError ? setAiRefresh(v => v + 1) : void startAi(true)}>{aiError ? '刷新状态' : '重试'}</button>}</div>}
        {aiView && !aiRunning && <AiChapterNotes key={`${chapterId}:${aiEditorKey}`} cards={aiVisible} selected={aiSelected} locked={busy || aiRunning} bookId={book.bookId} chapterId={chapter?.id ?? ''} onToggle={id => setAiSelected(old => old.includes(id) ? old.filter(n => n !== id) : old.length < LIMIT ? [...old, id] : old)} onChanged={aiChanged} onEditing={setAiDirty} onBusy={setAiBusy} />}
        {!aiView && <>
        {preview.warnings.length > 0 && <div className="notice" role="status">{preview.warnings.join('；')}。数量以下方列表为准。</div>}
        {tab === 'uncertain' && <p className="hint uncertain-hint">这些笔记发送时没有收到 flomo 的成功回执，可能已经写入。请到 flomo 搜索确认后选择处理方式，系统不会自动重发。</p>}
        {!loading && !loadError && !visible.length && <EmptyState
          title={{
            ready: notes.length ? '本章没有未同步笔记' : '暂无划线或想法',
            sent: '本章暂无已同步笔记',
            uncertain: '本章没有需要核对的笔记',
          }[tab]}
          description={{
            ready: !notes.length
              ? '在微信读书中留下划线或想法，再回来重新拉取。'
              : otherReadyChapterIndex >= 0
                ? '其他章节还有摘录待同步，去看看下一处收获。'
                : '可以查看已同步的摘录；有新划线时，再重新拉取。',
            sent: '在「未同步」中选择笔记，同步 flomo 成功后会显示在这里。',
            uncertain: '发送结果不确定的笔记会显示在这里，供你手动核对。',
          }[tab]}
          action={tab === 'ready' && otherReadyChapterIndex >= 0
            ? <button type="button" className="text-button" disabled={busy} onClick={() => changeChapter(otherReadyChapterIndex)}>查看待同步章节<ChevronRight size={14} aria-hidden="true" /></button>
            : tab === 'ready' && groups.sent.some(n => n.chapter.id === chapter?.id)
              ? <button type="button" className="text-button" disabled={busy} onClick={() => changeTab('sent')}>查看本章已同步笔记<ChevronRight size={14} aria-hidden="true" /></button>
              : undefined}
        />}

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
        </>}
      </section>

      <footer className="action-bar review-footer" aria-live="polite">
        {actionError && <p className="bar-message error-text" role="alert">{actionError}</p>}
        {!aiView && outcome && <p className={`bar-message ${outcome.uncertain || outcome.error ? 'error-text' : 'success-text'}`} role="status">
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
              {syncIds ? <div className="sync-progress">
                <p role="status">已同步 {syncProgress.sent} / {syncIds.length} 条</p>
                <progress value={syncProgress.sent} max={syncIds.length} aria-label="同步到 flomo 的进度" />
                <span>{syncProgress.delayed ? '进度暂时无法更新，请勿重复同步' : '请勿关闭页面或重复操作'}</span>
              </div> : <p>{aiView ? aiBusy ? '正在处理，请勿重复操作…' : <>已选 <strong>{aiSelected.length}</strong> 条{!sendingEnabled && <> · <button type="button" className="link-button" onClick={() => { if (discardAiEdit()) onOpenSettings(); }}>配置 flomo</button></>}</> : sending ? `正在发送 ${selected.size} 条，请勿关闭页面…`
                : !sendingEnabled ? <>还没连接 flomo，<button type="button" className="link-button" onClick={() => { if (!selected.size || window.confirm('前往设置会清空已选笔记，继续？')) onOpenSettings(); }}>去设置</button></>
                : tab === 'ready' ? <>已选 <strong>{selected.size}</strong> 条{selected.size >= LIMIT && '（单次上限 100 条）'}</>
                : null}</p>}
              <div className="bar-buttons">
                <button type="button" className="primary" disabled={busy || !sendingEnabled || (aiView ? aiRunning || aiDirty || !aiSelected.length : !selected.size)} onClick={aiView ? sendAi : send}>{syncIds ? '同步中…' : aiView ? `同步到 flomo${aiSelected.length ? ` · ${aiSelected.length}` : ''}` : sending ? '同步中…' : `同步到 flomo${selected.size ? ` · ${selected.size}` : ''}`}</button>
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
