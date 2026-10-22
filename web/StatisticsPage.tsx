import { useEffect, useRef, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { request } from './api.ts';
import { activityLevel, calendarDays, type Statistics } from './statistics.ts';
import './statistics.css';
import { StatisticsYearMenu } from './StatisticsYearMenu.tsx';

const number = (n: number) => n.toLocaleString('zh-CN');
export function StatisticsPage({ onClose }: { onClose: () => void }) {
  const [data, setData] = useState<Statistics | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const [year, setYear] = useState<number | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, []);
  useEffect(() => {
    const controller = new AbortController();
    setError('');
    request<Statistics>('/api/statistics', undefined, controller.signal).then(result => {
      setData(result); setYear(Number(result.today.slice(0, 4)));
    }).catch(e => { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : '读取失败，请重试'); });
    return () => controller.abort();
  }, [attempt]);
  const currentYear = data ? Number(data.today.slice(0, 4)) : 0;
  const firstYear = data ? Math.min(currentYear, ...Object.keys(data.days).map(day => Number(day.slice(0, 4)))) : 0;
  const cells = year ? calendarDays(year) : [];
  const annual = data && year ? Object.entries(data.days).filter(([day]) => day.startsWith(`${year}-`)) : [];
  const total = annual.reduce((sum, [, count]) => sum + count, 0);
  const detail = selected && data ? `${selected.replaceAll('-', ' / ')} · ${selected > data.today ? '尚未到来' : `同步 ${number(data.days[selected] ?? 0)} 条原始笔记`}` : '悬停或点选日期，查看当天同步数量';
  return <main className="statistics-page">
    <button className="text-button muted statistics-back" onClick={onClose}><ArrowLeft size={16} aria-hidden="true" />返回书架</button>
    <header className="statistics-heading"><h2 tabIndex={-1} ref={heading}>数据统计</h2><p>从微信读书到 flomo，留下的每一笔。</p></header>
    {error ? <div className="notice error" role="alert"><p>{error}</p><button className="text-button" onClick={() => setAttempt(n => n + 1)}>重新加载</button></div>
      : !data || !year ? <div className="statistics-loading" role="status">正在读取同步记录…<div className="skeleton" /></div> : <>
        <dl className="statistics-totals">
          {[[data.notes, '累计同步笔记', '条'], [data.books, '累计同步书籍', '本'], [data.activeDays, '有同步记录', '天']].map(([value, label, unit]) => <div key={label}><dt>{label}</dt><dd>{number(Number(value))}<span>{unit}</span></dd></div>)}
        </dl>
        <section className="statistics-calendar" aria-labelledby="activity-title">
          <div className="statistics-calendar-head"><div><h3 id="activity-title">同步足迹</h3><p><span>{year} 年同步 <strong>{number(total)}</strong> 条笔记</span><span>，分布在 <strong>{annual.length}</strong> 天</span></p></div>
            <StatisticsYearMenu year={year} firstYear={firstYear} currentYear={currentYear} onChange={value => { setYear(value); setSelected(null); }} />
          </div>
          <div className="statistics-scroll" role="region" aria-label={`${year} 年同步热力图，可横向滚动`} tabIndex={0}>
            <div className="statistics-grid" style={{ gridTemplateColumns: `28px repeat(${cells.length / 7}, minmax(10px, 1fr))` }}>
              {cells.map((day, i) => day?.endsWith('-01') ? <span key={`month-${day}`} className="statistics-month" style={{ gridColumn: Math.floor(i / 7) + 2, gridRow: 1 }}>{Number(day.slice(5, 7))}月</span> : null)}
              {['日', '一', '二', '三', '四', '五', '六'].map((day, i) => <span key={day} className="statistics-weekday" style={{ gridColumn: 1, gridRow: i + 2 }}>{i % 2 ? day : ''}</span>)}
              {cells.map((day, i) => day ? <button key={day} type="button" className={`text-button statistics-day level-${activityLevel(data.days[day] ?? 0)}${day > data.today ? ' future' : ''}${day === data.today ? ' today' : ''}`}
                style={{ gridColumn: Math.floor(i / 7) + 2, gridRow: i % 7 + 2 }}
                aria-label={`${day}，${day > data.today ? '尚未到来' : `同步 ${data.days[day] ?? 0} 条原始笔记`}`}
                title={`${day} · ${day > data.today ? '尚未到来' : `${data.days[day] ?? 0} 条原始笔记`}`}
                tabIndex={day === (selected ?? (year === currentYear ? data.today : `${year}-01-01`)) ? 0 : -1}
                onFocus={() => setSelected(day)} onMouseEnter={() => setSelected(day)} onClick={() => setSelected(day)}
                onKeyDown={e => {
                  const delta = { ArrowUp: -1, ArrowDown: 1, ArrowLeft: -7, ArrowRight: 7 }[e.key];
                  if (delta == null) return;
                  e.preventDefault();
                  const target = cells[i + delta];
                  if (target) { const buttons = e.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('.statistics-day'); buttons?.[cells.filter(Boolean).indexOf(target)]?.focus(); }
                }} /> : null)}
            </div>
          </div>
          <div className="statistics-calendar-foot"><p aria-live="polite">{detail}</p><div className="statistics-legend" aria-label="颜色深浅：0 条、1 至 5 条、6 至 15 条、16 至 30 条、31 条及以上"><span>少</span>{[0, 1, 2, 3, 4].map(level => <i key={level} className={`level-${level}`} title={['0 条', '1–5 条', '6–15 条', '16–30 条', '31 条及以上'][level]} />)}<span>多</span></div></div>
          {total === 0 && <p className="statistics-empty">{data.notes === 0 ? '还没有成功同步的笔记。去书架选几条，这里就会留下第一格绿色。' : '这一年还没有同步记录，可以切换年份查看。'}</p>}
        </section>
      </>}
  </main>;
}
