import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ExpeditionBossView } from '@/types/rogue';

const phaserMocks = vi.hoisted(() => ({
  createPhaserGame: vi.fn(),
  destroyPhaserGame: vi.fn(),
  getPhaserGame: vi.fn(),
  ensureExpeditionSceneStarted: vi.fn(),
  loadExpedition: vi.fn(),
  stopExpeditionScene: vi.fn(),
  ensureExpeditionBossSceneStarted: vi.fn(),
  loadExpeditionBoss: vi.fn(),
  stopExpeditionBossScene: vi.fn(),
  playSfx: vi.fn(),
}));

vi.mock('@/game/adapters/phaserGame', () => ({
  createPhaserGame: phaserMocks.createPhaserGame,
  destroyPhaserGame: phaserMocks.destroyPhaserGame,
  getPhaserGame: phaserMocks.getPhaserGame,
}));
vi.mock('@/game/scenes/ExpeditionScene', () => ({
  ensureExpeditionSceneStarted: phaserMocks.ensureExpeditionSceneStarted,
  loadExpedition: phaserMocks.loadExpedition,
  stopExpeditionScene: phaserMocks.stopExpeditionScene,
}));
vi.mock('@/game/scenes/ExpeditionBossScene', () => ({
  BOSS_IMPACT_SOUND_KEY: 'sfx_boss_impact',
  ensureExpeditionBossSceneStarted: phaserMocks.ensureExpeditionBossSceneStarted,
  loadExpeditionBoss: phaserMocks.loadExpeditionBoss,
  stopExpeditionBossScene: phaserMocks.stopExpeditionBossScene,
}));
vi.mock('@/composables/useAudio', () => ({ playSfx: phaserMocks.playSfx }));

import ExpeditionCanvas from '@/components/expedition/ExpeditionCanvas.vue';

const BOSS_VIEW: ExpeditionBossView = {
  chapter: 4,
  name: '流程钟楼',
  phase: 2,
  phasesTotal: 3,
  hp: 4,
  maxHp: 5,
  status: 'settling',
  attemptId: null,
};

describe('ExpeditionCanvas scene switching', () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it('loads Boss state before starting it and reuses the mounted Phaser Game', async () => {
    const game = { scene: {}, scale: { resize: vi.fn() } };
    phaserMocks.getPhaserGame.mockReturnValue(game);
    phaserMocks.createPhaserGame.mockReturnValue({ destroy: vi.fn() });

    const wrapper = mount(ExpeditionCanvas, {
      props: {
        nodes: [],
        layers: 1,
        currentNodeId: null,
        phase: 'map',
        hp: 4,
        maxHp: 5,
        score: 0,
        mode: 'tower',
        boss: null,
      },
    });
    await wrapper.setProps({ mode: 'boss', boss: BOSS_VIEW });
    await flushPromises();

    expect(phaserMocks.createPhaserGame).toHaveBeenCalledTimes(1);
    expect(phaserMocks.loadExpeditionBoss).toHaveBeenCalledWith(
      BOSS_VIEW,
      expect.objectContaining({ onBossHit: expect.any(Function) }),
    );
    const bossOptions = phaserMocks.loadExpeditionBoss.mock.calls[0]?.[1] as {
      onBossHit?: () => void;
    } | undefined;
    bossOptions?.onBossHit?.();
    expect(phaserMocks.playSfx).toHaveBeenCalledWith('sfx_boss_impact');
    expect(phaserMocks.stopExpeditionScene).toHaveBeenCalledWith(game);
    expect(phaserMocks.ensureExpeditionBossSceneStarted).toHaveBeenCalledWith(game);
    expect(phaserMocks.ensureExpeditionSceneStarted).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    expect(phaserMocks.stopExpeditionBossScene).toHaveBeenCalledWith(game);
  });
});
