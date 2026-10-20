import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

interface Props { className: string; labelledBy: string; describedBy?: string; role?: 'dialog' | 'alertdialog'; locked?: boolean; onClose: () => void; children: ReactNode }

/** 原生模态 dialog：挂载即打开，Esc 与点击遮罩关闭；locked 时（如发送中）不可关闭。 */
export function Sheet({ className, labelledBy, describedBy, role, locked = false, onClose, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) {
      dialog.showModal();
      // 焦点落在标题而不是第一个按钮，避免一打开就出现关闭按钮的焦点框。
      dialog.querySelector<HTMLElement>(`#${labelledBy}`)?.focus();
    }
    return () => dialog?.close();
  }, []);
  return <dialog ref={ref} className={className} role={role} aria-labelledby={labelledBy} aria-describedby={describedBy}
    onCancel={e => { e.preventDefault(); if (!locked) onClose(); }}
    onClick={e => { if (e.target === e.currentTarget && !locked) onClose(); }}>
    <div className="sheet-body">{children}</div>
  </dialog>;
}
