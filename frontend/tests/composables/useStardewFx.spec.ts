import { describe, expect, it, vi } from 'vitest';

describe('useStardewFx', () => {
  it('does not load tone.js until audio is explicitly unlocked', async () => {
    vi.resetModules();
    const toneLoaded = vi.fn();
    vi.doMock('tone', () => {
      toneLoaded();
      return {
        start: vi.fn(),
        now: () => 0,
        Synth: class {},
        MembraneSynth: class {},
        MetalSynth: class {},
        PolySynth: class {},
      };
    });

    await import('@/composables/useStardewFx');

    expect(toneLoaded).not.toHaveBeenCalled();
  });
});
