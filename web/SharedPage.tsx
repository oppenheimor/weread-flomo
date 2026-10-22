import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { ArrowLeft, ArrowUpRight, BookOpen, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react';
import type { Book, PreviewNote } from './types.ts';
import { BookCover } from './BookCover.tsx';
import { ThemeToggle } from './ThemeToggle.tsx';
import { ChapterMenu } from './ChapterMenu.tsx';
import './share.css';

type SharedNote = Pick<PreviewNote, 'id' | 'kind' | 'quote' | 'thought' | 'chapter' | 'createTime'>;
interface SharedView { username: string | null; scope: 'shelf' | 'book'; books: Book[]; detail: { book: Book; notes: SharedNote[] } | null }
const selection = () => new URLSearchParams(window.location.search).get('bookId');

export function SharedPage() {
  const tail = window.location.pathname.slice(`${import.meta.env.BASE_URL}share/`.length).replace(/\/$/, '');
  const token = /^[A-Za-z0-9_-]{43}$/.test(tail) ? tail : null;
  const [bookId, setBookId] = useState(selection);
  const [view, setView] = useState<SharedView | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState<'missing' | 'temporary' | null>(null);
  const [retry, setRetry] = useState(0);
  const [query, setQuery] = useState('');
  const [chapterId, setChapterId] = useState('');
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    const changed = () => setBookId(selection());
    window.addEventListener('popstate', changed);
    const restored = (event: PageTransitionEvent) => { if (event.persisted) setRetry(value => value + 1); };
    window.addEventListener('pageshow', restored);
    const meta = document.createElement('meta'); meta.name = 'robots'; meta.content = 'noindex, nofollow, noarchive'; document.head.append(meta);
    return () => { window.removeEventListener('popstate', changed); window.removeEventListener('pageshow', restored); meta.remove(); };
  }, []);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(null); setView(null);
    async function load() {
      if (!token) { setFailed('missing'); setLoading(false); return; }
      try {
        // Anonymous transport intentionally does not call /auth/session or redirect on credential errors.
        const response = await fetch(`${import.meta.env.BASE_URL}api/public/shares/${token}${bookId !== null ? `?bookId=${encodeURIComponent(bookId)}` : ''}`, { signal: controller.signal, credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer' });
        if (controller.signal.aborted) return;
        if (!response.ok) { if (response.status === 404) { setFailed('missing'); return; } throw new Error('load'); }
        const data: SharedView = await response.json();
        if (controller.signal.aborted) return;
        setView(data); setChapterId(data.detail?.notes[0]?.chapter.id ?? '');
      } catch { if (!controller.signal.aborted) setFailed('temporary'); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }
    void load(); return () => controller.abort();
  }, [token, bookId, retry]);
  const shelfTitle = view?.username ? `${view.username} 的书架` : '书架';
  useEffect(() => {
    document.title = failed === 'missing' ? '分享已关闭或不存在 · 拾墨' : view?.detail ? `${view.detail.book.title} · 拾墨分享` : `${shelfTitle} · 拾墨`;
  }, [view, failed, shelfTitle]);
  function navigate(id: string | null) {
    const url = new URL(window.location.href); url.search = ''; if (id !== null) url.searchParams.set('bookId', id);
    window.history.pushState(null, '', url); setBookId(id); window.scrollTo(0, 0);
  }
  const detail = view?.detail;
  const chapters = detail ? [...new Map(detail.notes.map(n => [n.chapter.id, n.chapter])).values()].sort((a,b) => a.order - b.order) : [];
  const chapterIndex = Math.max(0, chapters.findIndex(c => c.id === chapterId));
  const chapter = chapters[chapterIndex];
  const notes = detail?.notes.filter(n => n.chapter.id === chapter?.id) ?? [];
  // Reset after the new chapter has committed; focusing must not scroll it back into view.
  useLayoutEffect(() => {
    heading.current?.focus({ preventScroll: true });
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' });
  }, [chapterId]);
  function changeChapter(id: string) { setChapterId(id); }
  const keyword = query.trim().toLocaleLowerCase();
  const books = view?.books.filter(b => `${b.title} ${b.author}`.toLocaleLowerCase().includes(keyword)) ?? [];
  return <div className="app-shell shared-page">
    <header className="topbar"><div className="shared-brand"><img src={`${import.meta.env.BASE_URL}assets/brand/shimo-logo.png`} width="36" height="36" alt="" /><span>拾墨</span><span className="shared-label">阅读分享</span></div><div className="shared-header-actions">{view && <a className="shared-product-link" href={`${import.meta.env.BASE_URL}introduction`} target="_blank" rel="noopener noreferrer">体验拾墨<ArrowUpRight size={15} aria-hidden="true" /><span className="sr-only">（新标签页打开）</span></a>}<ThemeToggle /></div></header>
    <main className={`shared-main${detail ? ' shared-reading' : ''}`}>
      {loading && <section className="shared-loading" aria-busy="true" aria-label="正在读取分享"><p role="status">正在翻开分享…</p><div aria-hidden="true">{[0,1,2].map(i => <div className="shared-skeleton" key={i}><span className="skeleton-line" /><span className="skeleton-line" /><span className="skeleton-line" /></div>)}</div></section>}
      {!loading && failed === 'missing' && <section className="shared-unavailable"><img src={`${import.meta.env.BASE_URL}assets/share/unavailable.webp`} width="560" height="400" alt="" /><span className="shared-eyebrow">404 · 分享不可用</span><h1>分享已关闭或不存在</h1><p>这份阅读分享暂时无法打开。<br />可以联系分享人，获取新的链接。</p><a href={`${import.meta.env.BASE_URL}introduction`}>了解拾墨</a></section>}
      {!loading && failed === 'temporary' && <section className="shared-empty" role="alert"><BookOpen size={32} aria-hidden="true" /><h1>暂时无法加载</h1><p>请稍后再试，你无需登录或配置任何信息。</p><button type="button" onClick={() => setRetry(v => v + 1)}><RefreshCw size={16} aria-hidden="true" />重新加载</button></section>}
      {!loading && view && !detail && <>
        <section className="shelf-head"><div className="shelf-heading"><h1>{shelfTitle}</h1></div><div className="shelf-tools"><input type="search" aria-label="搜索书名或作者" placeholder="搜索书名或作者" value={query} onChange={e => setQuery(e.target.value)} /></div></section>
        <ul className="shelf" aria-label={shelfTitle}>{books.map(book => <li className="shelf-slot" key={book.bookId}><button type="button" className="shelf-book" onClick={() => navigate(book.bookId)}><span className="cover-wrap"><BookCover book={book} /></span><span className="shelf-title">{book.title}</span></button></li>)}</ul>
        {!books.length && <p className="shelf-empty">{view.books.length ? '没有匹配的书，换个关键词试试。' : '书架上还没有可展示的书。'}</p>}
      </>}
      {!loading && detail && <>
        {view.scope === 'shelf' && <button type="button" className="text-button muted shared-back" onClick={() => navigate(null)}><ArrowLeft size={16} aria-hidden="true" />返回书架</button>}
        <header className="shared-book"><BookCover book={detail.book} className="drawer-cover" /><div><p className="shared-eyebrow">划线与想法</p><h1>{detail.book.title}</h1><p className="muted">{detail.book.author || '作者未知'}</p></div></header>
        {chapter ? <>
          <div className="shared-chapter"><h2 ref={heading} tabIndex={-1}><ChapterMenu chapters={chapters} currentId={chapter.id} disabled={false} onChange={index => changeChapter(chapters[index].id)} /></h2><span>{chapterIndex + 1} / {chapters.length}</span></div>
          <div className="shared-notes">{notes.map(n => <article className="shared-note" key={n.id}>
            {n.quote && <blockquote>{n.quote}</blockquote>}
            {n.thought && <div className="shared-thought"><span className="shared-eyebrow">想法</span><p>{n.thought}</p></div>}
            {n.createTime && n.createTime > 0 ? <time dateTime={new Date(n.createTime * 1000).toISOString()}>{new Date(n.createTime * 1000).toLocaleDateString('zh-CN')}</time> : null}
          </article>)}</div>
          <nav className="shared-chapter-nav" aria-label="章节翻页"><button type="button" className="text-button" disabled={chapterIndex === 0} onClick={() => changeChapter(chapters[chapterIndex - 1].id)}><ChevronLeft size={16} />上一章</button><button type="button" className="text-button" disabled={chapterIndex === chapters.length - 1} onClick={() => changeChapter(chapters[chapterIndex + 1].id)}>下一章<ChevronRight size={16} /></button></nav>
        </> : <section className="shared-empty"><BookOpen size={28} aria-hidden="true" /><h2>这本书还没有划线或想法</h2><p>以后新增的笔记，也会出现在这里。</p></section>}
      </>}
    </main>
  </div>;
}
