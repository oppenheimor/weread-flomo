export interface Book {
  bookId: string;
  title: string;
  author: string;
  noteCount?: number;
  reviewCount?: number;
  cover?: string;
  readingProgress?: number | null;
  markedStatus?: number | null;
}
/** 账本中按书统计的来源条数：每条划线、每条想法各计一次。 */
export interface SyncStats {
  synced: number;
  uncertain: number;
  deleted?: number;
}
export type NoteStatus = 'ready' | 'sending' | 'sent' | 'uncertain';
export interface PreviewNote {
  id: string;
  bookId: string;
  kind: 'highlight' | 'review';
  content: string;
  quote: string;
  thought: string;
  /** 微信读书原始创建时间（Unix 秒），非同步时间。 */
  createTime?: number | null;
  /** 合并进这条 memo 的微信读书条目数（划线 + 附带的想法）。 */
  sources: number;
  status: NoteStatus;
  chapter: { id: string; title: string; order: number };
}
export interface PreviewResponse {
  previewId: string;
  book: Book;
  warnings: string[];
  notes: PreviewNote[];
}
export interface ConfigStatus {
  wereadConfigured: boolean;
  flomoConfigured: boolean;
  sendingEnabled: boolean;
  flomoError?: string;
}
export interface Session {
  authenticated: boolean;
  username: string;
  csrfToken: string;
}
