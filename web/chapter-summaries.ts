import type { PreviewNote } from './types.ts';

export interface ChapterWorkState { state: string; pending: boolean }
export interface ChapterSummary extends ChapterWorkState { chapter: PreviewNote['chapter'] }
type Request = <T>(path: string) => Promise<T>;

/** A single lightweight request regardless of the number of organized chapters. */
export async function loadChapterSummaries(bookId: string, request: Request) {
  const summaries = await request<ChapterSummary[]>(`/api/ai/chapter-summaries?bookId=${encodeURIComponent(bookId)}`);
  return {
    chapters: summaries.map(s => s.chapter),
    states: Object.fromEntries(summaries.map(s => [s.chapter.id, { state: s.state, pending: s.pending }])),
  };
}
