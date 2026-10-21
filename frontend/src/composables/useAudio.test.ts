import { beforeEach, describe, expect, it, vi } from 'vitest';

const { getPhaserGame, soundAdd } = vi.hoisted(() => ({
  getPhaserGame: vi.fn(),
  soundAdd: vi.fn(),
}));

vi.mock('@game/adapters/phaserGame', () => ({ getPhaserGame }));

describe('useAudio', () => {
  beforeEach(() => {
    vi.resetModules();
    getPhaserGame.mockReset();
    soundAdd.mockReset();
    getPhaserGame.mockReturnValue({ sound: { add: soundAdd } });
  });

  it('does not start a lazy BGM request after stopBgm cancels it', async () => {
    const { playBgm, stopBgm, unlockAudio } = await import('./useAudio');

    unlockAudio();
    playBgm('bgm_town_day');
    stopBgm();
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(getPhaserGame).toHaveBeenCalledOnce();
    expect(soundAdd).not.toHaveBeenCalled();
  });
});
