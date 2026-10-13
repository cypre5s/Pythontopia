import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  BOSS_IMPACT_SOUND_KEY,
  EXPEDITION_BOSS_SCENE_KEY,
  ExpeditionBossScene,
  __resetExpeditionBossForTests,
  ensureExpeditionBossSceneStarted,
  loadExpeditionBoss,
  stopExpeditionBossScene,
  type ExpeditionBossSceneContext,
  type ExpeditionBossView,
} from '@/game/scenes/ExpeditionBossScene';

type Spy = ReturnType<typeof vi.fn>;

function chain(methods: readonly string[]): Record<string, Spy> {
  const result: Record<string, Spy> = {};
  for (const method of methods) result[method] = vi.fn(() => result);
  result.destroy = vi.fn();
  return result;
}

const GRAPHICS_METHODS = [
  'fillStyle', 'fillRect', 'fillRoundedRect', 'lineStyle', 'lineBetween',
  'strokeRoundedRect', 'setDepth', 'setAlpha',
] as const;
const OBJECT_METHODS = ['setOrigin', 'setDepth', 'setDisplaySize', 'setPosition', 'setTint', 'clearTint'] as const;

function makeContext(width = 960, height = 360) {
  const graphics = vi.fn(() => chain(GRAPHICS_METHODS));
  const text = vi.fn(() => chain(OBJECT_METHODS));
  const image = vi.fn(() => chain(OBJECT_METHODS));
  const tweenAdd = vi.fn();
  const cameraShake = vi.fn();
  const cameraFlash = vi.fn();
  const removeAll = vi.fn();
  const eventHandlers = new Map<string, () => void>();
  const context = {
    add: { graphics, text, image },
    tweens: { add: tweenAdd, killAll: vi.fn() },
    cameras: { main: { shake: cameraShake, flash: cameraFlash } },
    scale: { width, height, on: vi.fn(), off: vi.fn() },
    events: {
      on: vi.fn((event: string, callback: () => void) => eventHandlers.set(event, callback)),
    },
    children: { removeAll },
    load: { image: vi.fn(), audio: vi.fn() },
  };
  return { context: context as unknown as ExpeditionBossSceneContext, graphics, text, image, tweenAdd, cameraShake, cameraFlash, removeAll, eventHandlers };
}

const READY_BOSS: ExpeditionBossView = {
  chapter: 4,
  name: '流程钟楼 · 自动化锻造神',
  phase: 2,
  phasesTotal: 3,
  hp: 3,
  maxHp: 5,
  status: 'ready',
  attemptId: null,
};

afterEach(() => {
  __resetExpeditionBossForTests();
});

describe('ExpeditionBossScene', () => {
  it('loads the selected chapter portrait and the impact sound', () => {
    loadExpeditionBoss(READY_BOSS);
    const { context } = makeContext();

    ExpeditionBossScene.preload.call(context);

    expect(context.load?.image).toHaveBeenCalledWith(
      'expedition_boss_4',
      '/assets/pixel/bosses/boss-4-loop-tower.png',
    );
    expect(context.load?.audio).toHaveBeenCalledWith(
      BOSS_IMPACT_SOUND_KEY,
      '/assets/pixel/sfx/boss/impactSoft_medium_000.ogg',
    );
  });

  it('shows the real phase and HP, and animates one settled pass only once', () => {
    const onBossHit = vi.fn();
    loadExpeditionBoss(READY_BOSS, { onBossHit });
    const scene = makeContext();
    ExpeditionBossScene.create.call(scene.context);

    expect(scene.text).toHaveBeenCalledWith(
      expect.any(Number), expect.any(Number), '流程钟楼 · 自动化锻造神', expect.any(Object),
    );
    expect(scene.text).toHaveBeenCalledWith(
      expect.any(Number), expect.any(Number), '阶段 2 / 3', expect.any(Object),
    );
    expect(scene.text).toHaveBeenCalledWith(
      expect.any(Number), expect.any(Number), '体力 3 / 5', expect.any(Object),
    );

    const pendingAttempt: ExpeditionBossView = {
      ...READY_BOSS,
      status: 'judging',
      attemptId: 'problem-42',
    };
    loadExpeditionBoss(pendingAttempt, { onBossHit });

    expect(scene.cameraShake).not.toHaveBeenCalled();
    expect(scene.cameraFlash).not.toHaveBeenCalled();
    expect(onBossHit).not.toHaveBeenCalled();

    const settledPass: ExpeditionBossView = {
      ...pendingAttempt,
      status: 'passed',
    };
    loadExpeditionBoss(settledPass, { onBossHit });
    loadExpeditionBoss(settledPass, { onBossHit });

    expect(scene.cameraShake).toHaveBeenCalledTimes(1);
    expect(scene.cameraFlash).toHaveBeenCalledTimes(1);
    expect(onBossHit).toHaveBeenCalledTimes(1);
  });

  it('does not animate Boss damage after a failed answer and shows the settled HP', () => {
    const onBossHit = vi.fn();
    loadExpeditionBoss(READY_BOSS, { onBossHit });
    const scene = makeContext();
    ExpeditionBossScene.create.call(scene.context);
    scene.text.mockClear();

    loadExpeditionBoss({
      ...READY_BOSS,
      hp: 2,
      status: 'failed',
      attemptId: 'problem-43',
    }, { onBossHit });

    expect(scene.text).toHaveBeenCalledWith(
      expect.any(Number), expect.any(Number), '体力 2 / 5', expect.any(Object),
    );
    expect(scene.cameraShake).not.toHaveBeenCalled();
    expect(scene.cameraFlash).not.toHaveBeenCalled();
    expect(onBossHit).not.toHaveBeenCalled();
  });
});

describe('ExpeditionBossScene scene manager', () => {
  it('uses the existing game scene manager and stops by its registered key', () => {
    const add = vi.fn();
    const start = vi.fn();
    const stop = vi.fn();
    const bringToTop = vi.fn();
    const game = { scene: { add, start, stop, bringToTop, keys: {} } };

    ensureExpeditionBossSceneStarted(game);

    expect(add).toHaveBeenCalledWith(EXPEDITION_BOSS_SCENE_KEY, ExpeditionBossScene, true);
    expect(bringToTop).toHaveBeenCalledWith(EXPEDITION_BOSS_SCENE_KEY);
    stopExpeditionBossScene(game);
    expect(stop).toHaveBeenCalledWith(EXPEDITION_BOSS_SCENE_KEY);
  });
});
