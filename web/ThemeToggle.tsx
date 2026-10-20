import { useSyncExternalStore } from 'react';
import { Monitor, Moon, Sun } from 'lucide-react';

type Preference = 'system' | 'light' | 'dark';
const key = 'shimo-theme';
const media = window.matchMedia('(prefers-color-scheme: dark)');
const listeners = new Set<() => void>();
const valid = (value: string | null): Preference => value === 'light' || value === 'dark' ? value : 'system';
let preference: Preference = 'system';
try { preference = valid(localStorage.getItem(key)); } catch { /* Private browsing may block storage. */ }
function apply() {
  document.documentElement.dataset.theme = preference === 'system' ? (media.matches ? 'dark' : 'light') : preference;
  listeners.forEach(listener => listener());
}
apply();
media.addEventListener('change', apply);
window.addEventListener('storage', event => {
  if (event.key === key || event.key === null) { preference = valid(event.newValue); apply(); }
});
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
const labels = { system: '跟随系统', light: '浅色模式', dark: '深色模式' };
const next: Record<Preference, Preference> = { system: 'dark', dark: 'light', light: 'system' };
export function ThemeToggle() {
  const current = useSyncExternalStore(subscribe, () => preference);
  const Icon = current === 'system' ? Monitor : current === 'dark' ? Moon : Sun;
  const label = `${labels[current]}，切换为${labels[next[current]]}`;
  function toggle() {
    preference = next[preference];
    try { localStorage.setItem(key, preference); } catch { /* Switching still works for this page. */ }
    apply();
  }
  return <button type="button" className="icon-button theme-toggle" onClick={toggle} aria-label={label} title={label}>
    <Icon size={18} strokeWidth={1.75} aria-hidden="true" />
  </button>;
}
