import { useEffect, useId, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronDown, Search } from 'lucide-react';
import './chapter-menu.css';

interface Props {
  chapters: { id: string; title: string }[];
  currentId: string;
  disabled: boolean;
  onChange: (index: number) => void;
}

export function ChapterMenu({ chapters, currentId, disabled, onChange }: Props) {
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const list = useRef<HTMLDivElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [scrolling, setScrolling] = useState(false);
  const scrollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  function revealScrollbar() {
    setScrolling(true);
    if (scrollTimer.current !== null) clearTimeout(scrollTimer.current);
    scrollTimer.current = setTimeout(() => { setScrolling(false); scrollTimer.current = null; }, 700);
  }
  useEffect(() => {
    setScrolling(false);
    return () => {
      if (scrollTimer.current !== null) clearTimeout(scrollTimer.current);
      scrollTimer.current = null;
    };
  }, [open]);
  const searchable = chapters.length > 12;
  const filtered = chapters.map((chapter, index) => ({ ...chapter, index }))
    .filter(chapter => !query.trim() || chapter.title.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()) || String(chapter.index + 1) === query.trim());

  function position() {
    if (!trigger.current || !panel.current) return;
    const rect = trigger.current.getBoundingClientRect();
    const width = Math.min(360, window.innerWidth - 24);
    const below = window.innerHeight - rect.bottom - 20;
    const above = rect.top - 20;
    const upward = below < 240 && above > below;
    Object.assign(panel.current.style, {
      width: `${width}px`,
      left: `${Math.max(12, Math.min(rect.left, window.innerWidth - width - 12))}px`,
      top: upward ? 'auto' : `${rect.bottom + 8}px`,
      bottom: upward ? `${window.innerHeight - rect.top + 8}px` : 'auto',
      maxHeight: `${Math.max(100, Math.min(440, upward ? above : below))}px`,
    });
  }
  function close() { panel.current?.hidePopover(); }
  function show() {
    if (open) { close(); return; }
    setQuery(''); position(); panel.current?.showPopover();
  }
  useEffect(() => {
    if (!open) return;
    const current = list.current?.querySelector<HTMLButtonElement>('[aria-current="true"]');
    current?.focus({ preventScroll: true });
    current?.scrollIntoView({ block: 'nearest' });
    window.addEventListener('resize', position);
    // The popup is in the top layer, so it is never clipped by the notes scroller.
    const scroll = (event: Event) => { if (event.target instanceof Node && !panel.current?.contains(event.target)) position(); };
    window.addEventListener('scroll', scroll, true);
    return () => { window.removeEventListener('resize', position); window.removeEventListener('scroll', scroll, true); };
  }, [open]);
  useEffect(() => { if (disabled) close(); }, [disabled]);

  return <>
    <button ref={trigger} type="button" className="text-button chapter-menu-trigger" disabled={disabled}
      aria-label={`选择章节，当前 ${chapters.find(c => c.id === currentId)?.title ?? ''}`}
      aria-haspopup="dialog" aria-expanded={open} aria-controls={id}
      onClick={show} onKeyDown={event => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); if (!open) show(); }
      }}>
      <span>{chapters.find(c => c.id === currentId)?.title}</span><span className="chapter-menu-chevron" aria-hidden="true"><ChevronDown size={14} strokeWidth={1.75} /></span>
    </button>
    {createPortal(<div ref={panel} id={id} popover="auto" role="dialog" aria-label="选择章节"
      className="account-options chapter-menu-panel" onToggle={event => setOpen(event.newState === 'open')}
      onKeyDown={event => {
        if (event.key === 'Escape') { event.preventDefault(); close(); trigger.current?.focus(); return; }
        const buttons = Array.from(list.current?.querySelectorAll<HTMLButtonElement>('button') ?? []);
        const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
        const inSearch = document.activeElement === search.current;
        const next = event.key === 'ArrowDown' ? Math.min(index + 1, buttons.length - 1)
          : event.key === 'ArrowUp' ? Math.max(0, index - 1)
          : !inSearch && event.key === 'Home' ? 0
          : !inSearch && event.key === 'End' ? buttons.length - 1 : null;
        if (next !== null) { event.preventDefault(); buttons[next]?.focus(); }
        if (event.key === 'Tab') {
          const first = searchable ? search.current : buttons[0];
          const last = buttons[buttons.length - 1] ?? search.current;
          if (event.shiftKey && document.activeElement === first) { event.preventDefault(); close(); trigger.current?.focus(); }
          else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); close(); trigger.current?.focus(); }
        }
      }}>
      {searchable && <label className="chapter-menu-search"><Search size={15} aria-hidden="true" />
        <input ref={search} type="search" value={query} onChange={event => { setQuery(event.target.value); if (list.current) list.current.scrollTop = 0; }} placeholder="搜索章节名称或序号" aria-label="搜索章节名称或序号" />
      </label>}
      <div ref={list} className={`chapter-menu-list${scrolling ? ' is-scrolling' : ''}`} onScroll={revealScrollbar}>
        {filtered.map(chapter => <button key={chapter.id} type="button" className="text-button"
          aria-current={chapter.id === currentId ? 'true' : undefined}
          onClick={() => { close(); trigger.current?.focus(); onChange(chapter.index); }}>
          <span className="chapter-menu-number" aria-hidden="true">{chapter.index + 1}</span>
          <span className="chapter-menu-title">{chapter.title}</span>
          {chapter.id === currentId && <Check size={15} aria-hidden="true" />}
        </button>)}
        {!filtered.length && <p className="chapter-menu-empty" role="status">没有匹配的章节，试试其他关键词。</p>}
      </div>
    </div>, document.body)}
  </>;
}
