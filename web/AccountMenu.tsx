import { useEffect, useRef, useState } from 'react';
import { ChartNoAxesColumn, Boxes, ChevronDown, LogOut, Settings } from 'lucide-react';

interface Props {
  username: string;
  disabled: boolean;
  needsSetup: boolean;
  onSettings: () => void;
  onModels: () => void;
  onStatistics: () => void;
  onLogout: () => void;
}
export function AccountMenu({ username, disabled, needsSetup, onSettings, onModels, onStatistics, onLogout }: Props) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !root.current?.contains(event.target)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus(); }
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [open]);
  return <div ref={root} className="account-menu" onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
  }}>
    <button ref={trigger} type="button" className="text-button account-trigger" aria-expanded={open} aria-controls="account-options" aria-label={`${username}，账号菜单`} onClick={() => setOpen(value => !value)}>
      <span className="account-avatar" aria-hidden="true">{username.slice(0, 1).toUpperCase()}{needsSetup && <i />}</span>
      <span className="account-name">{username}</span>
      <ChevronDown size={14} strokeWidth={1.75} aria-hidden="true" />
    </button>
    {open && <div className="account-options" id="account-options">
      <p className="account-menu-name">{username}</p>
      <button type="button" className="text-button" disabled={disabled} onClick={() => { setOpen(false); onSettings(); }}><Settings size={16} strokeWidth={1.75} aria-hidden="true" />账号设置{needsSetup && <span className="attention" aria-hidden="true" />}</button>
      <button type="button" className="text-button" disabled={disabled} onClick={() => { setOpen(false); onStatistics(); }}><ChartNoAxesColumn size={16} strokeWidth={1.75} aria-hidden="true" />数据统计</button>
      <button type="button" className="text-button" disabled={disabled} onClick={() => { setOpen(false); onModels(); }}><Boxes size={16} strokeWidth={1.75} aria-hidden="true" />模型设置</button>
      <button type="button" className="text-button" disabled={disabled} onClick={() => { setOpen(false); onLogout(); }}><LogOut size={16} strokeWidth={1.75} aria-hidden="true" />退出登录</button>
    </div>}
  </div>;
}
