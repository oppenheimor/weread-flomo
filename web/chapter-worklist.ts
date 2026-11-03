import { pendingNotes } from './note-status.ts';

type Chapter = { id: string; title: string; order: number };
type Note = { status: string; chapter: Chapter };
type Organized = { state: string; notes: readonly { status: string }[] };

export function chapterWorklist(notes: readonly Note[], saved: readonly Chapter[], organized: Readonly<Record<string, Organized>>, currentId: string | null = null) {
  const all = [...new Map([...saved.map(c => [c.id, c] as const), ...notes.map(n => [n.chapter.id, n.chapter] as const)]).values()].sort((a, b) => a.order - b.order);
  const pendingIds = new Set(pendingNotes(notes).map(n => n.chapter.id));
  for (const chapter of all) {
    const state = organized[chapter.id];
    // Unknown stored state is not proof of completion.
    if ((!state && saved.some(c => c.id === chapter.id)) || (state && (state.state === 'running' || state.state === 'failed' || pendingNotes(state.notes).length))) pendingIds.add(chapter.id);
  }
  return { pendingIds, chapters: all.filter(c => pendingIds.has(c.id) || c.id === currentId) };
}

export function completionTitle(justSynced: boolean, hasOtherWork: boolean): string {
  if (justSynced) return hasOtherWork ? '本章已全部同步到 flomo' : '这本书的笔记已全部同步';
  return hasOtherWork ? '本章暂无待同步笔记' : '本书暂无待同步笔记';
}
