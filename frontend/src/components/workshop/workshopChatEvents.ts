import type { AguiEvent } from '@/composables/agui';

export function findRuntimeRunId(
  events: readonly AguiEvent[],
  startIndex: number,
): number | null {
  for (let index = events.length - 1; index >= Math.max(0, startIndex); index -= 1) {
    const event = events[index];
    if (event?.type !== 'CUSTOM' || event.name !== 'runtime_run') continue;
    const value = event.value;
    if (!value || typeof value !== 'object') return null;
    const loopRunId = (value as Record<string, unknown>)['loop_run_id'];
    return typeof loopRunId === 'number' && Number.isSafeInteger(loopRunId) && loopRunId > 0
      ? loopRunId
      : null;
  }
  return null;
}
