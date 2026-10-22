import { useEffect, useState } from 'react';
import { Pencil } from 'lucide-react';
import { request } from './api.ts';
import type { AiCard, AiChapter } from './ai-types.ts';
import './ai-companion.css';
import { boldSegments } from './markdown.ts';
import { EmptyState } from './EmptyState.tsx';
import { needsReview } from './note-status.ts';

interface Props {
  cards: AiCard[]; selected: string[]; locked: boolean;
  showEmpty: boolean;
  bookId: string; chapterId: string;
  onToggle: (id: string) => void;
  onChanged: (chapter: AiChapter) => void;
  onEditing: (editing: boolean) => void;
  onBusy: (busy: boolean) => void;
}
export function AiChapterNotes({ cards, selected, locked, showEmpty, bookId, chapterId, onToggle, onChanged, onEditing, onBusy }: Props) {
  const [editing, setEditing] = useState<string | null>(null);
  const [title, setTitle] = useState(''); const [body, setBody] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  useEffect(() => { onEditing(editing !== null); return () => onEditing(false); }, [editing, onEditing]);
  useEffect(() => { onBusy(busy); return () => onBusy(false); }, [busy, onBusy]);
  async function save(card: AiCard) {
    setBusy(true); setError('');
    try { onChanged(await request<AiChapter>('/api/ai/edit', { bookId, chapterId, id: card.id, title, body, version: card.version })); setEditing(null); }
    catch(e) { setError(e instanceof Error ? e.message : '保存失败，输入已保留'); }
    finally { setBusy(false); }
  }
  async function resolve(card: AiCard, delivered: boolean) {
    if (!window.confirm(delivered ? '确认 flomo 已有这条？关联的未同步原始笔记也将标为已同步。' : '确认 flomo 没有这条？将恢复可发送，不会自动重发。')) return;
    setBusy(true); setError('');
    try { onChanged(await request<AiChapter>('/api/ai/resolve', { bookId, chapterId, id: card.id, delivered })); }
    catch(e) { setError(e instanceof Error ? e.message : '操作失败'); }
    finally { setBusy(false); }
  }
  return <div className="ai-inline-results">
    {error && <p className="error-text" role="alert">{error}</p>}
    {!cards.length && showEmpty && <EmptyState
      title="本章暂无待同步笔记"
      description="可以切换章节继续整理；有新划线时，再回来重新拉取。"
    />}
    {cards.some(card => needsReview(card.status)) && <p className="hint uncertain-hint">标有「需核对」的笔记可能已写入 flomo，请先核对再处理，系统不会自动重发。</p>}
    {cards.map((card, index) => {
      const single = card.snapshot.notes.length === 1;
      const editingThis = editing === card.id;
      return <article key={card.id} className={`ai-inline-note ${selected.includes(card.id) ? 'is-selected' : ''}`}>
        <div className="ai-note-toolbar"><div className="note-kind"><span className="note-actions note-selection">{card.status === 'ready' ? <label className="note-icon-action note-checkbox-action" title={selected.includes(card.id) ? '取消选择' : '选择笔记'}><input type="checkbox" aria-label={`选择${single ? '摘录' : '观点'} ${index + 1}`} disabled={locked || busy || editing !== null || (!selected.includes(card.id) && selected.length >= 100)} checked={selected.includes(card.id)} onChange={() => onToggle(card.id)} /></label> : <span className={`status-dot status-${card.status}`} aria-label={card.status === 'sent' ? '已同步' : '需核对'} />}</span><span className="note-number">{single ? '摘录' : '观点'} {String(index + 1).padStart(2, '0')}</span>{needsReview(card.status) && <span className="uncertain-hint">需核对</span>}</div>
        {!single && card.status === 'ready' && <button type="button" className={editingThis ? 'primary ai-edit-action' : 'text-button ai-edit-action'} disabled={locked || busy || (editing !== null && !editingThis)} aria-label={editingThis ? '完成编辑' : '编辑观点'} title={editingThis ? '完成编辑' : '编辑观点'} onClick={() => { if (editingThis) void save(card); else { setTitle(card.title); setBody(card.body); setEditing(card.id); setError(''); } }}>{editingThis ? (busy ? '保存中…' : '完成') : <Pencil size={18} strokeWidth={1.5} />}</button>}
        </div>
        <p className="ai-source-tag">#微信读书/《{card.snapshot.book.title}》 · {card.snapshot.chapter.title}</p>
        {single ? <><p className="ai-body">{card.snapshot.notes[0].quote}</p>{card.snapshot.notes[0].thought && <p className="ai-source-thought">我的想法：{card.snapshot.notes[0].thought}</p>}</> : <>
          {editingThis ? <div className="ai-paper-editor"><input autoFocus className="ai-paper-title" aria-label="观点标题" disabled={busy} maxLength={120} value={title} onChange={e => setTitle(e.target.value)} /><textarea className="ai-paper-body" aria-label="观点正文" placeholder="可用 **重点内容** 标注加粗" disabled={busy} maxLength={20000} ref={node => { if (node) { node.style.height = 'auto'; node.style.height = `${node.scrollHeight}px`; } }} value={body} onChange={e => setBody(e.target.value)} /></div> : <><h3>{card.title}</h3><p className="ai-body">{boldSegments(card.body).map((part, i) => part.bold ? <strong key={i}>{part.text}</strong> : part.text)}</p></>}
          <details className="ai-inline-sources"><summary>查看来源 · {card.snapshot.notes.length} 条</summary>{card.snapshot.notes.map((s, i) => <section key={s.id}><small>来源 {i + 1}</small>{s.quote && <p>{s.quote}</p>}{s.thought && <p className="ai-source-thought">我的想法：{s.thought}</p>}</section>)}</details>
        </>}
        {card.status === 'uncertain' && <div className="ai-resolve-actions"><button type="button" disabled={locked || busy || editing !== null} onClick={() => void resolve(card, true)}>flomo 里已有</button><button type="button" disabled={locked || busy || editing !== null} onClick={() => void resolve(card, false)}>flomo 里没有</button></div>}
      </article>;
    })}
  </div>;
}
