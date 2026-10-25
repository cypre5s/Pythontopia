import { describe, expect, it } from 'vitest';
import type { AguiEvent } from '@/composables/agui';
import { findRuntimeRunId } from '../workshopChatEvents';

describe('findRuntimeRunId', () => {
  it('only searches events after the consumed cursor', () => {
    const events: AguiEvent[] = [
      { type: 'CUSTOM', name: 'runtime_run', value: { loop_run_id: 41 } },
      { type: 'TEXT_MESSAGE_CONTENT', delta: 'token' },
      { type: 'CUSTOM', name: 'runtime_run', value: { loop_run_id: 42 } },
      { type: 'TEXT_MESSAGE_CONTENT', delta: 'more' },
    ];

    expect(findRuntimeRunId(events, 1)).toBe(42);
    expect(findRuntimeRunId(events, 3)).toBeNull();
    expect(
      findRuntimeRunId(
        [{ type: 'CUSTOM', name: 'runtime_run', value: { loop_run_id: -1 } }],
        0,
      ),
    ).toBeNull();
    expect(findRuntimeRunId([], 0)).toBeNull();
  });
});
