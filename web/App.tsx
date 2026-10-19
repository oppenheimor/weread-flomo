import { useEffect, useState } from 'react';
import type { Book, ConfigStatus, NoteStatus, PreviewNote, PreviewResponse } from './types.ts';
import { request } from './api.ts';
import { CredentialSettings } from './CredentialSettings.tsx';
import { BookCover } from './BookCover.tsx';

const labels: Record<NoteStatus, string> = { ready: '待发送', sent: '✓ 已发送', sending: '发送中', uncertain: '⚠ 结果不确定' };
const message = (error: unknown): string => error instanceof Error ? error.message : '操作失败，请重试。';

export function App() {
  const [config, setConfig] = useState<ConfigStatus | null>(null);
  const [books, setBooks] = useState<Book[]>([]);
  const [booksLoaded, setBooksLoaded] = useState(false);
  const [query, setQuery] = useState('');
  const [bookId, setBookId] = useState('');
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState<'books' | 'preview' | 'send' | 'status' | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [settingsOpen, setSettingsOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    request<ConfigStatus>('/api/status', undefined, controller.signal).then(setConfig).catch(e => {
      if (!controller.signal.aborted) setError(message(e));
    });
    return () => controller.abort();
  }, []);

  async function checkStatus() {
    setBusy('status'); setError('');
    try { setConfig(await request<ConfigStatus>('/api/status')); }
    catch (e) { setError(message(e)); }
    finally { setBusy(null); }
  }
  async function loadBooks() {
    setBusy('books'); setError(''); setNotice(''); setPreview(null); setSelected(new Set()); setConfirming(false);
    try {
      const result = await request<{ books: Book[] }>('/api/books', {});
      setBooks(result.books); setBooksLoaded(true);
      setBookId(result.books.some(b => b.bookId === bookId) ? bookId : '');
    } catch (e) { setError(message(e)); }
    finally { setBusy(null); }
  }
  async function loadPreview() {
    setBusy('preview'); setError(''); setNotice(''); setPreview(null); setSelected(new Set()); setConfirming(false);
    try { setPreview(await request<PreviewResponse>('/api/preview', { bookId })); }
    catch (e) { setError(message(e)); }
    finally { setBusy(null); }
  }
  async function send() {
    if (!preview || !selected.size || !config?.sendingEnabled) return;
    setBusy('send'); setError(''); setNotice('');
    try {
      const result = await request<{ notes: PreviewNote[]; deliveryError?: string }>('/api/send', { previewId: preview.previewId, ids: [...selected], confirmed: true });
      setPreview({ ...preview, notes: result.notes });
      setSelected(new Set());
      if (result.deliveryError) setError(result.deliveryError);
      setNotice(result.notes.some(n => n.status === 'uncertain')
        ? '有笔记的发送结果不确定，已停止本批次。请先到 flomo 核对；这些笔记不会自动重发。'
        : '发送结束，已发送的笔记不会再次发送。');
    } catch (e) { setError(message(e)); }
    finally { setBusy(null); setConfirming(false); }
  }
  function credentialsChanged(status: ConfigStatus) {
    setConfig(status); setBooks([]); setBooksLoaded(false); setBookId(''); setPreview(null);
    setSelected(new Set()); setConfirming(false); setError('');
    setNotice('凭证配置已更新，请重新加载书籍。已有同步记录不变；尚未验证远端权限。');
  }
  function chooseBook(id: string) {
    setBookId(id); setPreview(null); setSelected(new Set()); setConfirming(false); setNotice(''); setError('');
  }
  function toggle(id: string) {
    setConfirming(false);
    setSelected(previous => {
      const next = new Set(previous);
      if (next.has(id)) next.delete(id);
      else if (next.size < 100) next.add(id);
      return next;
    });
  }
  const visibleBooks = books.filter(b => `${b.title} ${b.author}`.toLocaleLowerCase().includes(query.toLocaleLowerCase()));
  const currentBook = books.find(b => b.bookId === bookId);
  const ready = preview?.notes.filter(n => n.status === 'ready') ?? [];
  const locked = Boolean(busy);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div><p className="eyebrow">个人读书笔记</p><h1>微信读书 <span aria-hidden="true">→</span> flomo</h1></div>
        <span className="local-label"><span aria-hidden="true" />私人账号 · 手动同步</span>
      </header>
      <main>
        <section className="intro" aria-labelledby="intro-heading">
          <h2 id="intro-heading">让读过的，留下来。</h2>
          <p>先选一本书，检查笔记内容，再决定哪些值得带到 flomo。</p>
        </section>

        <section className="connection" aria-label="接口配置状态">
          <div><span className="connection-label">微信读书</span><strong className={config?.wereadConfigured ? 'connected' : ''}>{config ? config.wereadConfigured ? '已配置' : '待配置密钥' : '正在检查连接'}</strong></div>
          <div><span className="connection-label">flomo</span><strong>{config?.sendingEnabled ? '已配置，可发送' : config?.flomoError ? '地址需修正' : '待配置 API 地址'}</strong></div>
          <div className="connection-actions"><button className="text-button" disabled={locked} onClick={checkStatus}>{busy === 'status' ? '检查中…' : '重新检查'}</button><button className="text-button" disabled={locked} aria-expanded={settingsOpen || Boolean(config && (!config.wereadConfigured || !config.flomoConfigured))} onClick={() => setSettingsOpen(!settingsOpen)}>账号设置</button></div>
        </section>
        {(settingsOpen || Boolean(config && (!config.wereadConfigured || !config.flomoConfigured))) && <CredentialSettings config={config} disabled={locked} onChanged={credentialsChanged} />}
        {error && <div className="notice error" role="alert"><strong>未能完成操作</strong><p>{error}</p></div>}
        {notice && <div className="notice" role="status">{notice}</div>}

        <div className="workspace">
          <section className="books-panel" aria-labelledby="books-heading" aria-busy={busy === 'books'}>
            <div className="section-heading"><h2 id="books-heading"><span className="step">01</span> 选择书籍</h2><span>{booksLoaded ? `${books.length} 本` : ''}</span></div>
            <p className="hint">列出有笔记的书，不只限于当前书架。</p>
            <button className="load-books primary" onClick={loadBooks} disabled={locked || !config?.wereadConfigured}>{busy === 'books' ? '正在加载全部书籍…' : booksLoaded ? '刷新书籍列表' : '加载我的书籍'}</button>
            {booksLoaded && <>
              <label className="search-label" htmlFor="book-search">查找书名或作者</label>
              <input id="book-search" type="search" placeholder="输入书名或作者" value={query} onChange={e => setQuery(e.target.value)} disabled={locked} />
              <fieldset className="book-list" disabled={locked}>
                <legend className="sr-only">选择一本书</legend>
                {visibleBooks.map(b => <label key={b.bookId} className={`book-row ${bookId === b.bookId ? 'is-selected' : ''}`}>
                  <input type="radio" name="book" value={b.bookId} checked={bookId === b.bookId} onChange={() => chooseBook(b.bookId)} />
                  <BookCover book={b} />
                  <span className="book-copy"><strong>{b.title}</strong><small>{b.author || '作者信息未提供'}</small><small>{b.noteCount ?? '未知'} 条划线 · {b.reviewCount ?? '未知'} 条想法 / 书评</small><small className="book-progress"><span title="微信读书接口返回的阅读进度；文档未注明单位，按原始数值展示">{b.readingProgress != null ? `阅读进度 ${b.readingProgress}` : '进度未提供'}</span>{b.markedStatus === 1 && <span className="finished-label">已读完</span>}{b.markedStatus === 0 && <span>在读</span>}</small></span>
                </label>)}
              </fieldset>
              {!visibleBooks.length && <p className="empty-small">{books.length ? '没有匹配的书籍，换个关键词试试。' : '没有找到有笔记的书籍。'}</p>}
            </>}
            {!booksLoaded && <p className="empty-small">{config?.wereadConfigured ? '点击上方按钮，读取你的笔记书目。' : '配置微信读书密钥后，即可加载书目。'}</p>}
          </section>

          <section className="preview-panel" aria-labelledby="preview-heading" aria-busy={busy === 'preview' || busy === 'send'}>
            <div className="section-heading"><h2 id="preview-heading"><span className="step">02</span> 检查笔记</h2><button onClick={loadPreview} disabled={locked || !currentBook}>{busy === 'preview' ? '正在拉取…' : preview ? '重新拉取' : '拉取笔记'}</button></div>
            {currentBook && <p className="chosen-book">《{currentBook.title}》</p>}
            {!preview && <div className="empty-preview">
              <span className="paper-symbol" aria-hidden="true">≡</span>
              <h3>{busy === 'preview' ? '正在整理划线和想法' : currentBook ? '这本书，值得记住什么？' : '从一本书开始'}</h3>
              <p>{busy === 'preview' ? '完整拉取后显示预览，请稍等。' : currentBook ? '点击“拉取笔记”，查看将写入 flomo 的完整内容。' : '在左侧选书，预览你的划线、想法和书评。'}</p>
              <p className="hint">拉取不会发送任何笔记，也不会保存原始 JSON。</p>
            </div>}
            {preview && <>
              {preview.warnings.length > 0 && <div className="notice" role="status"><strong>数量与概览不一致，请核对后再选择。</strong>{preview.warnings.map(w => <p key={w}>{w}</p>)}</div>}
              <div className="selection-bar">
                <span>共 {preview.notes.length} 条 · 待发送 {ready.length} 条</span>
                <button className="text-button" disabled={locked || !ready.length} onClick={() => { setSelected(new Set(selected.size ? [] : ready.slice(0, 100).map(n => n.id))); setConfirming(false); }}>{selected.size ? '取消选择' : ready.length > 100 ? '选择前 100 条' : '全选待发送'}</button>
              </div>
              {ready.length > 100 && <p className="hint">每批最多选择 100 条；flomo 每日 API 总额度也是 100 条。</p>}
              <div className="note-list">
                {preview.notes.map((note, index) => {
                  const newline = note.content.indexOf('\n');
                  return <article className={`note ${selected.has(note.id) ? 'is-selected' : ''}`} key={note.id}>
                    <div className="note-heading"><label><input type="checkbox" checked={selected.has(note.id)} disabled={locked || note.status !== 'ready' || (!selected.has(note.id) && selected.size >= 100)} onChange={() => toggle(note.id)} /><span>{String(index + 1).padStart(2, '0')} · {note.kind === 'highlight' ? '划线' : '独立想法 / 书评'}</span><span className="sr-only">：{note.content.slice(newline + 1).trim().slice(0, 40)}</span></label><span className={`status status-${note.status}`}>{labels[note.status]}</span></div>
                    <p className="note-tag">{note.content.slice(0, newline)}</p>
                    <p className="note-content">{note.content.slice(newline + 1).trimStart()}</p>
                  </article>;
                })}
              </div>
              {!preview.notes.length && <p className="empty-small">这本书没有可导出的划线或想法。书签不在导出范围内。</p>}
            </>}
          </section>
        </div>

        <section className="send-panel" aria-label="确认发送">
          <div><h2><span className="step">03</span> 带到 flomo</h2><p>已选 <strong>{selected.size}</strong> 条。已发送的内容不重复发送，也不检查后续变化。</p></div>
          <button className="primary" disabled={locked || !selected.size || !config?.sendingEnabled} onClick={() => setConfirming(true)}>{busy === 'send' ? '正在发送，请勿关闭…' : `发送所选 ${selected.size} 条`}</button>
          <p className="send-help">{config?.sendingEnabled ? '建议先发送 1 条确认格式。flomo 每天最多接收 100 条 API 笔记，其他工具写入也占用额度。' : '请先在账号设置中保存 flomo API 地址，才能发送。'}</p>
          {confirming && <div className="confirm-send" role="group" aria-label="最终确认"><p>将向你的 flomo 新增 <strong>{selected.size}</strong> 条笔记，内容与上方预览一致。确认发送？</p><button disabled={locked} onClick={() => setConfirming(false)}>返回检查</button><button className="primary" disabled={locked} onClick={send}>确认发送</button></div>}
        </section>
      </main>
      <footer>密钥仅留在服务端 · 同步记录保存在 PostgreSQL · 只有你确认后才发送</footer>
    </div>
  );
}
