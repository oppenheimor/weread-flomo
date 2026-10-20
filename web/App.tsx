import { useEffect, useState } from 'react';
import { RefreshCw } from 'lucide-react';
import type { Book, ConfigStatus, SyncStats } from './types.ts';
import { exclusive, request } from './api.ts';
import { CredentialSettings } from './CredentialSettings.tsx';
import { BookCover, progressText } from './BookCover.tsx';
import { NotesPage } from './NotesPage.tsx';
import { ThemeToggle } from './ThemeToggle.tsx';
import { AccountMenu } from './AccountMenu.tsx';

const message = (error: unknown): string => error instanceof Error ? error.message : '操作失败，请重试。';

/** 概览数量减去账本已记录的条数；概览缺数量时返回 null，不猜测。 */
function pendingOf(book: Book, stats?: SyncStats): number | null {
  if (book.noteCount == null || book.reviewCount == null) return null;
  return Math.max(0, book.noteCount + book.reviewCount - (stats?.synced ?? 0) - (stats?.uncertain ?? 0) - (stats?.deleted ?? 0));
}

interface Props { username: string; onLogout: () => void; loggingOut: boolean }

export function App({ username, onLogout, loggingOut }: Props) {
  const [config, setConfig] = useState<ConfigStatus | null>(null);
  const [books, setBooks] = useState<Book[] | null>(null);
  const [sync, setSync] = useState<Record<string, SyncStats>>({});
  const [loadingBooks, setLoadingBooks] = useState(false);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [openBook, setOpenBook] = useState<Book | null>(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [noteNavigation, setNoteNavigation] = useState({ busy: false, selected: 0 });
  function canLeaveNotes() {
    return !openBook || (!noteNavigation.busy && (!noteNavigation.selected || window.confirm('离开后不会保留已选笔记，确定继续？')));
  }
  // 书架计数单位是微信读书条目（划线、想法各算一条）；打开过的书以预览实际结果覆盖概览估算。
  const [exact, setExact] = useState<Record<string, number>>({});

  async function loadBooks() {
    setLoadingBooks(true); setError('');
    try {
      const result = await exclusive(() => request<{ books: Book[]; sync: Record<string, SyncStats> }>('/api/books', {}));
      setBooks(result.books); setSync(result.sync); setExact({});
    } catch (e) { setError(message(e)); }
    finally { setLoadingBooks(false); }
  }
  async function refreshSync() {
    try { setSync((await request<{ sync: Record<string, SyncStats> }>('/api/sync')).sync); }
    catch { /* 角标刷新失败不影响主流程，下次加载书架时更新 */ }
  }
  useEffect(() => {
    const controller = new AbortController();
    request<ConfigStatus>('/api/status', undefined, controller.signal).then(status => {
      setConfig(status);
      if (status.wereadConfigured) void loadBooks();
      if (!status.wereadConfigured) setSettingsOpen(true);
    }).catch(e => { if (!controller.signal.aborted) setError(message(e)); });
    return () => controller.abort();
  }, []);

  // 服务端在任何凭证变更后都会清空书目缓存，需要重新加载才能拉取笔记。
  function credentialsChanged(status: ConfigStatus) {
    setConfig(status); setOpenBook(null); setExact({});
    if (status.wereadConfigured) void loadBooks();
    else { setBooks(null); setSync({}); }
  }

  const all = books ?? [];
  const pending = new Map(all.map(b => [b.bookId, exact[b.bookId] ?? pendingOf(b, sync[b.bookId])]));
  const keyword = query.trim().toLocaleLowerCase();
  const visible = all.filter(b => !keyword || `${b.title} ${b.author}`.toLocaleLowerCase().includes(keyword));

  return <div className={`app-shell${openBook ? ' app-shell-review' : ''}`}>
    <header className="topbar">
      <h1 className="brand"><button type="button" className="brand-home" aria-label="拾墨，返回书架" disabled={Boolean(openBook && noteNavigation.busy)} onClick={() => { if (canLeaveNotes()) setOpenBook(null); }}><img src={`${import.meta.env.BASE_URL}assets/brand/shimo-logo.png`} width="48" height="48" alt="" draggable={false} /><span>拾墨</span></button></h1>
      <nav className="topbar-actions" aria-label="账号">
        <AccountMenu username={username} disabled={loggingOut || Boolean(openBook && noteNavigation.busy)} needsSetup={Boolean(config && !config.wereadConfigured)}
          onSettings={() => { if (canLeaveNotes()) { setOpenBook(null); setSettingsOpen(true); } }}
          onLogout={() => { if (canLeaveNotes()) onLogout(); }} />
        <ThemeToggle />
      </nav>
    </header>

    {!openBook && <main className="app-main">
      {error && <div className="notice error banner" role="alert">
        <p>{error}</p>
        {config?.wereadConfigured && <button type="button" className="text-button" onClick={loadBooks}>重试</button>}
        <button type="button" className="text-button" onClick={() => setSettingsOpen(true)}>检查设置</button>
      </div>}

      {config && !config.wereadConfigured && <section className="welcome">
        <h2>先连接微信读书</h2>
        <p>连接后，有笔记的书会出现在这里；点开一本书即可挑选划线发送到 flomo。</p>
        <button type="button" className="primary" onClick={() => setSettingsOpen(true)}>连接账号</button>
      </section>}

      {config?.wereadConfigured && <>
        <section className="shelf-head" aria-label="书架工具">
          <span className="sr-only" role="status">{loadingBooks ? '正在读取书架…' : ''}</span>
          <div className="shelf-heading">
            <h2>我的书架</h2>
            <button type="button" className="text-button muted shelf-refresh" disabled={loadingBooks} onClick={loadBooks}
              aria-label={loadingBooks ? '正在刷新书架' : '刷新书架'} title="刷新书架">
              <RefreshCw size={18} strokeWidth={2.25} aria-hidden="true" />
            </button>
            {loadingBooks && <span className="shelf-refresh-status" aria-hidden="true">刷新中…</span>}
          </div>
          <div className="shelf-tools">
            <input type="search" aria-label="搜索书名或作者" placeholder="搜索书名或作者" value={query} onChange={e => setQuery(e.target.value)} />
          </div>
        </section>

        {!books && loadingBooks && <ul className="shelf" aria-hidden="true">
          {Array.from({ length: 12 }, (_, i) => <li key={i} className="shelf-slot"><span className="book-cover skeleton" /><span className="skeleton-line" /></li>)}
        </ul>}

        {books && <ul className="shelf" aria-label={`书架，共 ${books.length} 本`}>
          {visible.map(book => {
            const count = pending.get(book.bookId);
            const uncertain = sync[book.bookId]?.uncertain ?? 0;
            const progress = progressText(book);
            const empty = book.noteCount === 0 && book.reviewCount === 0;
            const meta = uncertain ? `${uncertain} 条需核对` : count ? `${count} 条未同步` : empty ? '无划线或想法' : count === 0 ? (sync[book.bookId]?.deleted ? '已整理' : '已同步') : progress;
            return <li key={book.bookId} className="shelf-slot">
              <button type="button" className="shelf-book" onClick={() => setOpenBook(book)}
                aria-label={`${book.title}，${book.author || '作者未知'}，${meta || '状态未知'}`}>
                <span className="cover-wrap">
                  <BookCover book={book} />
                  {uncertain > 0 ? <span className="badge badge-warn" aria-hidden="true">!</span>
                    : count ? <span className="badge" aria-hidden="true">{count > 99 ? '99+' : count}</span> : null}
                </span>
                <span className="shelf-title">{book.title}</span>
                {(!count || uncertain > 0) && meta && <span className={`shelf-meta ${uncertain ? 'warn' : ''}`}>{meta}</span>}
              </button>
            </li>;
          })}
        </ul>}
        {books && !visible.length && <p className="shelf-empty">{
          !books.length ? '微信读书里还没有带笔记的书。'
          : '没有匹配的书，换个关键词试试。'}</p>}
      </>}
    </main>}

    {openBook && <NotesPage onNavigationStateChange={setNoteNavigation} key={openBook.bookId} book={openBook} sendingEnabled={Boolean(config?.sendingEnabled)}
      onOpenSettings={() => { setOpenBook(null); setSettingsOpen(true); }} onSynced={refreshSync} onCounted={(id, ready) => setExact(previous => ({ ...previous, [id]: ready }))} onClose={() => setOpenBook(null)} />}
    {settingsOpen && <CredentialSettings config={config} onChanged={credentialsChanged} onClose={() => setSettingsOpen(false)} />}
  </div>;
}
