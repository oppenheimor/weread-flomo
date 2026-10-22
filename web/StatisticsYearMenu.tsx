import { useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronDown } from 'lucide-react';

export function StatisticsYearMenu({ year, firstYear, currentYear, onChange }: {
  year: number; firstYear: number; currentYear: number; onChange: (year: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const id = useId();
  useEffect(() => {
    if (!open) return;
    root.current?.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus();
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false);
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open]);
  return <div className="statistics-year" ref={root}
    onBlur={e => { if (!e.currentTarget.contains(e.relatedTarget)) setOpen(false); }}
    onKeyDown={e => {
      if (e.key === 'Escape' && open) { e.preventDefault(); setOpen(false); trigger.current?.focus(); }
    }}>
    <button ref={trigger} type="button" className="text-button statistics-year-trigger" aria-label={`选择统计年份，当前 ${year} 年`}
      aria-haspopup="menu" aria-expanded={open} aria-controls={open ? id : undefined}
      onClick={() => setOpen(value => !value)} onKeyDown={e => {
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') { e.preventDefault(); setOpen(true); }
      }}><span>{year} 年</span><ChevronDown size={14} strokeWidth={1.75} aria-hidden="true" /></button>
    {open && <div id={id} className="account-options statistics-year-options" role="menu" aria-label="统计年份" onKeyDown={e => {
      const buttons = Array.from(e.currentTarget.querySelectorAll<HTMLButtonElement>('button'));
      const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
      const next = e.key === 'ArrowDown' ? (index + 1) % buttons.length : e.key === 'ArrowUp' ? (index - 1 + buttons.length) % buttons.length : e.key === 'Home' ? 0 : e.key === 'End' ? buttons.length - 1 : null;
      if (next != null) { e.preventDefault(); buttons[next]?.focus(); }
    }}>
      {Array.from({ length: currentYear - firstYear + 1 }, (_, i) => currentYear - i).map(value =>
        <button key={value} type="button" role="menuitemradio" aria-checked={year === value} tabIndex={-1} className="text-button"
          onClick={() => { onChange(value); setOpen(false); trigger.current?.focus(); }}>
          <span>{value} 年</span>{year === value && <Check size={15} strokeWidth={1.75} aria-hidden="true" />}
        </button>)}
    </div>}
  </div>;
}
