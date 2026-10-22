import type { ReactNode } from 'react';
import notesIllustration from './assets/empty-notes.png';
import './empty-state.css';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: ReactNode;
  illustration?: ReactNode;
}

/** Presentational only: callers decide whether an empty result is safe to show. */
export function EmptyState({ title, description, action, illustration }: EmptyStateProps) {
  return <div className="empty-state">
    <div className="empty-state-illustration" aria-hidden="true">
      {illustration ?? <img src={notesIllustration} alt="" width={600} height={400} />}
    </div>
    <h3 className="empty-state-title">{title}</h3>
    {description && <p className="empty-state-description">{description}</p>}
    {action && <div className="empty-state-action">{action}</div>}
  </div>;
}
