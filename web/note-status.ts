export function needsReview(status: string): boolean {
  return status === 'uncertain' || status === 'sending';
}

/** The work list excludes synced history but must never hide unresolved deliveries. */
export function pendingNotes<T extends { status: string }>(notes: readonly T[]): T[] {
  return notes.filter(note => note.status === 'ready' || needsReview(note.status));
}
