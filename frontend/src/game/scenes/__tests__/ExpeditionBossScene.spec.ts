import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  __resetExpeditionBossForTests,
  BOSS_IMPACT_SOUND_KEY,
  ExpeditionBossScene,
  loadExpeditionBoss,
  type ExpeditionBossSceneContext,
} from '../ExpeditionBossScene';
import type { ExpeditionBossView } from '@/types/rogue';

function chainable<T extends Record<string, unknown>>(methods: string[]): T {
  const value: Record<string, unknown> = {};
  for (const method of methods) value[method] = vi.fn().mockReturnThis();
  value.destroy = vi.fn();
  return value as T;
}

function createScene(): {
  context: ExpeditionBossSceneContext;
  textContent: string[];
  loadImage: ReturnType<typeof vi.fn>;
  loadAudio: ReturnType<typeof vi.fn>;
  shake: ReturnType<typeof vi.fn>;
} {
  const textContent: string[] = [];
  const loadImage = vi.fn();
  const loadAudio = vi.fn();
  const shake = vi.fn();
  const context = {
    add: {
      graphics: vi.fn(() => chainable(['fillStyle', 'fillRect', 'fillRoundedRect', 'lineStyle', 'lineBetween', 'strokeRoundedRect', 'setDepth', 'setAlpha', 'setPosition', 'setScale'])),
      text: vi.fn((_x: number, _y: number, content: string) => {
        textContent.push(content);
        return chainable(['setOrigin', 'setDepth', 'setColor']);
      }),
      image: vi.fn(() => chainable(['setOrigin', 'setDepth', 'setDisplaySize', 'setPosition', 'setTint', 'clearTint'])),
    },
    load: { image: loadImage, audio: loadAudio },
    tweens: { add: vi.fn(), killAll: vi.fn() },
    cameras: { main: { shake, flash: vi.fn() } },
    scale: { width: 960, height: 360, on: vi.fn(), off: vi.fn() },
    events: { on: vi.fn() },
    children: { removeAll: vi.fn() },
  } as unknown as ExpeditionBossSceneContext;
  return { context, textContent, loadImage, loadAudio, shake };
}

function bossView(overrides: Partial<ExpeditionBossView> = {}): ExpeditionBossView {
  return {
    chapter: 2,
    name: '解释器学徒',
    phase: 1,
    phasesTotal: 3,
    hp: 8,
    maxHp: 8,
    status: 'ready',
    attemptId: null,
    ...overrides,
  };
}

describe('ExpeditionBossScene', () => {
  beforeEach(() => __resetExpeditionBossForTests());
  afterEach(() => __resetExpeditionBossForTests());

  it('preloads the chapter portrait and CC0 impact sound', () => {
    const { context, loadImage, loadAudio } = createScene();
    loadExpeditionBoss(bossView());

    ExpeditionBossScene.preload.call(context);

    expect(loadImage).toHaveBeenCalledWith('expedition_boss_2', '/assets/pixel/bosses/boss-2-interpreter-apprentice.png');
    expect(loadAudio).toHaveBeenCalledWith(BOSS_IMPACT_SOUND_KEY, '/assets/pixel/sfx/boss/impactSoft_medium_000.ogg');
  });

  it('plays one hit for a settled pass and ignores failure or duplicate replay', () => {
    const { context, shake } = createScene();
    const onBossHit = vi.fn();
    const ready = bossView();
    loadExpeditionBoss(ready, { onBossHit });
    ExpeditionBossScene.create.call(context);

    loadExpeditionBoss(bossView({ status: 'failed', hp: 6 }), { onBossHit });
    expect(onBossHit).not.toHaveBeenCalled();

    const passed = bossView({ status: 'passed', hp: 6, attemptId: 'run-1:problem-1:1' });
    loadExpeditionBoss(passed, { onBossHit });
    loadExpeditionBoss(bossView({ status: 'judging' }), { onBossHit });
    loadExpeditionBoss(passed, { onBossHit });

    expect(onBossHit).toHaveBeenCalledTimes(1);
    expect(shake).toHaveBeenCalledTimes(1);
  });

  it('shows server-provided stamina on failure without a hit effect', () => {
    const { context, textContent, shake } = createScene();
    const onBossHit = vi.fn();
    loadExpeditionBoss(bossView({ status: 'failed', hp: 3, maxHp: 8 }), { onBossHit });

    ExpeditionBossScene.create.call(context);

    expect(textContent).toContain('体力 3 / 8');
    expect(textContent).toContain('未通过 · 当前体力 3 / 8');
    expect(onBossHit).not.toHaveBeenCalled();
    expect(shake).not.toHaveBeenCalled();
  });
});
