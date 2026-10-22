import { cloneElement, useEffect, useId, useRef, useState, type ReactElement } from 'react';
import './tooltip.css';

export function Tooltip({ label, children }: {
  label: string;
  children: ReactElement<{ 'aria-describedby'?: string }>;
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  function cancelTimer() { if (timer.current) clearTimeout(timer.current); timer.current = null; }
  function hide() { cancelTimer(); setOpen(false); }
  useEffect(() => () => cancelTimer(), []);
  useEffect(() => {
    if (!open) return;
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') hide(); };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [open]);
  return <span className="ui-tooltip-anchor"
    onMouseEnter={() => { cancelTimer(); timer.current = setTimeout(() => setOpen(true), 300); }}
    onMouseLeave={hide} onFocus={() => { cancelTimer(); setOpen(true); }} onBlur={hide} onClick={hide}>
    {cloneElement(children, { 'aria-describedby': [children.props['aria-describedby'], open ? id : undefined].filter(Boolean).join(' ') || undefined })}
    {open && <span id={id} className="ui-tooltip" role="tooltip">{label}</span>}
  </span>;
}
