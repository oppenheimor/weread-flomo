import { useEffect, useState } from 'react';
import { request } from './api.ts';

/** Poll separately from the mutation queue: the send request holds that queue. */
export function useSyncProgress(ids: string[] | null) {
  const [progress, setProgress] = useState({ sent: 0, delayed: false });
  useEffect(() => {
    setProgress({ sent: 0, delayed: false });
    if (!ids) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const result = await request<{ sent: number }>('/api/send/progress', { ids }, controller.signal);
        if (!controller.signal.aborted) setProgress({ sent: result.sent, delayed: false });
      } catch {
        // Never infer success from elapsed time or a failed progress request.
        if (!controller.signal.aborted) setProgress(previous => ({ ...previous, delayed: true }));
      }
      if (!controller.signal.aborted) timer = setTimeout(poll, 1000);
    }
    void poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [ids]);
  return progress;
}
