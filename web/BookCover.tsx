import { useState } from 'react';
import type { Book } from './types.ts';

export function BookCover({ book, className = '' }: { book: Book; className?: string }) {
  const [failedUrl, setFailedUrl] = useState('');
  return book.cover && failedUrl !== book.cover
    ? <img className={`book-cover ${className}`} src={book.cover} alt="" loading="lazy" decoding="async" referrerPolicy="no-referrer" onError={() => setFailedUrl(book.cover ?? '')} />
    : <span className={`book-cover cover-placeholder ${className}`} aria-hidden="true">{book.title}</span>;
}

/** readingProgress 按微信读书客户端的 0–100 百分比展示；超出范围时不猜测单位。 */
export function progressText(book: Book): string {
  if (book.markedStatus === 1) return '已读完';
  const value = book.readingProgress;
  if (value == null) return '';
  return value >= 0 && value <= 100 ? `读到 ${Math.round(value)}%` : `进度 ${value}`;
}
