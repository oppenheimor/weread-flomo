import type { Book, PreviewNote } from './types.ts';
type Chapter = PreviewNote['chapter'];
export interface AiProvider { id: string; name: string; protocol: string; baseUrl: string; hasKey: boolean; models: string[] }
export interface AiSettings { thinkingControl?: boolean; providers: AiProvider[]; defaultModel: { providerId: string; model: string } | null }
export interface AiSource { id: string; content: string; quote: string; thought: string }
export interface AiCard { id: string; title: string; body: string; snapshot: { book: Book; chapter: Chapter; notes: AiSource[] }; status: string; edited: boolean; version: number }
export interface AiProgress { phase: string; startedAt: number | null; sourceCount: number; outputChars: number; summary: string }
export interface AiDraft { id: string; kind: 'excerpt' | 'viewpoint'; title: string; body: string; quote: string; thought: string }
export interface AiChapter { state: 'idle' | 'running' | 'ready' | 'failed'; error: string; notes: AiCard[]; progress?: AiProgress | null; drafts?: AiDraft[] }
