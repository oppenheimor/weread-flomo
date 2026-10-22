import type { AiDraft } from './ai-types.ts';
import { boldSegments } from './markdown.ts';
import { EmptyState } from './EmptyState.tsx';
import notesIllustration from './assets/empty-notes.png';
import './ai-writing.css';

/** Draft cards look like their final form, but deliberately have no controls or sources. */
export function AiWriting({ drafts, bookTitle, chapterTitle, phase }: { drafts: AiDraft[]; bookTitle: string; chapterTitle: string; phase?: string }) {
  const status = drafts.length ? '正在整理' : phase === 'thinking' ? '模型正在思考，尚未输出正文' : phase === 'buffered' ? '接口未提供流式输出，等待完整结果' : phase === 'requesting' ? '等待模型响应' : '正在整理';
  return <div className="ai-writing" aria-label="正在生成的笔记" aria-busy="true">
    {!drafts.length && <div className="ai-waiting" role="status">
      <EmptyState title={status} description={phase === 'buffered' ? '本次接口会一次返回结果，请保持页面打开。' : '本章摘录与想法正在整理中，请保持页面打开。'}
        illustration={<div className="ai-waiting-art">
          <img src={notesIllustration} alt="" width={600} height={400} />
          <span className="ai-waiting-slip ai-waiting-slip-back"><i /><i /><i /></span>
          <span className="ai-waiting-slip ai-waiting-slip-front"><i /><i /><i /></span>
        </div>} />
    </div>}
    {drafts.map((draft, index) => <article className="ai-inline-note ai-writing-note" key={draft.id}>
      <div className="ai-note-toolbar"><div className="note-kind"><span className="ai-writing-selection" aria-hidden="true" /><span className="note-number">{draft.kind === 'excerpt' ? '摘录' : '观点'} {String(index + 1).padStart(2, '0')}</span></div></div>
      <p className="ai-source-tag">#微信读书/《{bookTitle}》 · {chapterTitle}</p>
      {draft.kind === 'excerpt' ? <>{draft.quote && <p className="ai-body">{draft.quote}</p>}{draft.thought && <p className="ai-source-thought">我的想法：{draft.thought}</p>}</> : <>
        {draft.title && <h3>{draft.title}</h3>}
        {draft.body && <p className="ai-body">{boldSegments(draft.body).map((part, i) => part.bold ? <strong key={i}>{part.text}</strong> : part.text)}</p>}
      </>}
    </article>)}
    {drafts.length > 0 && <div className="ai-writing-indicator" role="status">
      <span>{status}<span className="ai-writing-dots" aria-hidden="true">…</span></span>
    </div>}
  </div>;
}
